<template>
  <div class="chat-page">
    <section class="wechat-shell">
      <header class="wechat-header">
        <div class="header-main">
          <div class="assistant-avatar">聊</div>
          <div>
            <h2>聊天记录测试</h2>
            <p>
              模拟你和对方用户的真实对话
              <span class="status-pill">API {{ apiHost }}</span>
              <span class="status-pill status-pill-alert">自动预警开启</span>
            </p>
          </div>
        </div>
        <div class="header-actions">
          <button
            v-if="latestRiskResult"
            type="button"
            class="header-btn header-btn-secondary"
            @click="openRiskDialog"
          >
            {{ latestResultButtonText }}
          </button>
          <button type="button" class="header-btn" @click="handleClear">清空</button>
        </div>
      </header>

      <div class="user-profile-strip" aria-label="选填，用于生成更贴合你的防诈说明">
        <span class="strip-hint">本人情况（选填）</span>
        <select v-model="userAgeBand" class="strip-select" :disabled="chatStore.isLoading">
          <option value="">年龄段 · 不限</option>
          <option value="UNDER_18">未成年</option>
          <option value="YOUNG_ADULT">青年</option>
          <option value="MIDDLE_AGED">中年</option>
          <option value="SENIOR">中老年</option>
        </select>
        <select v-model="userPersonality" class="strip-select" :disabled="chatStore.isLoading">
          <option value="">个性倾向 · 不限</option>
          <option value="ANXIOUS">偏焦虑 / 易紧张</option>
          <option value="CAUTIOUS">偏谨慎</option>
          <option value="DIGITAL_NOVICE">对手机网银不熟</option>
          <option value="AUTHORITY_TRUSTING">较信「官方/警察」口吻</option>
          <option value="IMPULSIVE">容易匆忙做决定</option>
        </select>
        <select v-model="userRiskLiteracy" class="strip-select" :disabled="chatStore.isLoading">
          <option value="">防诈了解 · 不限</option>
          <option value="LOW">较少了解</option>
          <option value="MEDIUM">一般</option>
          <option value="HIGH">较熟悉</option>
        </select>
      </div>

      <div ref="messagesRef" class="chat-messages">
        <div class="day-divider">今天</div>

        <section v-if="chatStore.messages.length === 0" class="welcome-card">
          <h3>这里不是和风控助手对话，而是模拟你和对方的正常聊天。</h3>
          <p>
            对方可以发付款链接、退款通知、催促转账等。底部可切换「键盘 / 语音」发文字或语音（可播放 + 自动转写）；对方语音会进入风控与 Dify。上方「本人情况」选填。
          </p>
          <div class="welcome-actions">
            <button
              v-for="preset in riskPresets"
              :key="preset.id"
              type="button"
              class="welcome-chip"
              @click="applyPreset(preset)"
            >
              {{ preset.title }}
            </button>
          </div>
        </section>

        <ChatBubble v-for="message in chatStore.messages" :key="message.id" :message="message" />
      </div>

      <div class="preset-strip">
        <button
          v-for="preset in riskPresets"
          :key="preset.id"
          type="button"
          class="strip-chip"
          @click="applyPreset(preset)"
        >
          {{ preset.title }}
        </button>
      </div>

      <ChatInput
        v-model="draft"
        v-model:sender="draftSender"
        :analyze-disabled="getConversationMessages().length === 0"
        :disabled="chatStore.isLoading"
        @send="handleSend"
        @analyze="handleAnalyze"
        @reset="restoreDefaultPreset"
        @pick-images="handlePickImages"
        @voice-error="handleVoiceError"
        @voice-sent="handleVoiceSent"
      />
    </section>

    <transition name="risk-dialog-fade">
      <div
        v-if="riskDialogVisible && latestRiskResult"
        class="risk-dialog-mask"
        @click.self="closeRiskDialog"
      >
        <section class="risk-dialog" role="alertdialog" aria-modal="true" aria-labelledby="risk-dialog-title">
          <div class="risk-dialog-header">
            <div>
              <p class="risk-dialog-eyebrow">风险预警弹窗</p>
              <h3 id="risk-dialog-title">{{ riskDialogTitle }}</h3>
              <p class="risk-dialog-summary">{{ riskDialogSummary }}</p>
            </div>
            <button
              type="button"
              class="risk-dialog-close"
              aria-label="关闭风险预警"
              @click="closeRiskDialog"
            >
              ×
            </button>
          </div>

          <RiskResultCompact :risk-data="latestRiskResult" />

          <div class="risk-dialog-actions">
            <button type="button" class="risk-dialog-btn risk-dialog-btn-secondary" @click="closeRiskDialog">
              我知道了
            </button>
          </div>
        </section>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import ChatBubble from '@/components/ChatBubble.vue'
import ChatInput from '@/components/ChatInput.vue'
import RiskResultCompact from '@/components/RiskResultCompact.vue'
import { useRiskAssess } from '@/composables/useRiskAssess'
import { riskPresets, type RiskPreset } from '@/constants/presets'
import { useChatStore } from '@/stores/chat'
import type {
  ChatMessage,
  ChatMessageImage,
  ChatMessageVoice,
  ChatRole,
  PayRiskAssessRespVO,
  RiskLevel
} from '@/types'
import { fileToJpegDataUrl } from '@/utils/imageCompress'

const chatStore = useChatStore()
const { assess, error } = useRiskAssess()
const messagesRef = ref<HTMLElement>()
const draft = ref('')
const draftSender = ref<Extract<ChatRole, 'self' | 'peer'>>('peer')
const lastSubmittedFingerprint = ref('')
const sessionVersion = ref(0)
const latestRiskResult = ref<PayRiskAssessRespVO | null>(null)
const riskDialogVisible = ref(false)
const activePreset = ref<RiskPreset | null>(null)

/** 选填：传给后端 LLM，用于个性化防诈话术（年龄段 / 个性 / 防诈了解程度） */
const userAgeBand = ref('')
const userPersonality = ref('')
const userRiskLiteracy = ref('')

/** 本会话语音转写记录，提交 assess 时写入 paymentData，供 Dify 反思流引用 */
const voiceTranscripts = ref<
  Array<{ role: Extract<ChatRole, 'self' | 'peer'>; text: string; model?: string; at: string }>
>([])

const HIGH_RISK_SCORE_THRESHOLD = 65
const popupRiskLevels: RiskLevel[] = ['HIGH', 'CRITICAL']

const apiHost = computed(() => {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:48080'
  try {
    return new URL(baseUrl).host
  } catch {
    return baseUrl
  }
})

const createMessage = (
  type: ChatMessage['type'],
  content: string,
  senderName?: string,
  images?: ChatMessageImage[],
  source?: ChatMessage['source'],
  voice?: ChatMessageVoice
): ChatMessage => {
  const msg: ChatMessage = {
    id: `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
    type,
    content,
    timestamp: new Date(),
    senderName
  }
  if (source) {
    msg.source = source
  }
  if (voice) {
    msg.voice = voice
    if (!msg.source) {
      msg.source = 'voice_asr'
    }
  }
  if (images?.length) {
    msg.images = images
    if (!msg.source) {
      msg.source = 'image'
    }
  }
  return msg
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

watch(() => chatStore.messages.length, scrollToBottom)

const getConversationMessages = () => chatStore.messages.filter((message) => message.type !== 'system')

const isPopupRiskResult = (result: PayRiskAssessRespVO) =>
  Number(result.riskScore) >= HIGH_RISK_SCORE_THRESHOLD || popupRiskLevels.includes(result.riskLevel)

const openRiskDialog = () => {
  if (latestRiskResult.value) {
    riskDialogVisible.value = true
  }
}

const closeRiskDialog = () => {
  riskDialogVisible.value = false
}

const latestResultButtonText = computed(() => {
  if (!latestRiskResult.value) {
    return ''
  }
  return isPopupRiskResult(latestRiskResult.value) ? '查看风险预警' : '查看分析结果'
})

const riskDialogTitle = computed(() => {
  const result = latestRiskResult.value
  if (!result) {
    return ''
  }
  if (result.riskLevel === 'CRITICAL') {
    return '严重风险'
  }
  if (isPopupRiskResult(result)) {
    return '高风险预警'
  }
  return '分析结果'
})

const riskDialogSummary = computed(() => {
  const result = latestRiskResult.value
  if (!result) {
    return ''
  }
  const n = Math.min(Math.max(Number(result.riskScore) || 0, 0), 100)
  const levelZh =
    result.riskLevel === 'CRITICAL'
      ? '严重'
      : result.riskLevel === 'HIGH'
        ? '高'
        : result.riskLevel === 'MEDIUM'
          ? '中'
          : '低'
  if (result.riskLevel === 'CRITICAL') {
    return `风险分 ${n} · 等级 ${levelZh} · 请先不要转账，核实对方身份。`
  }
  if (isPopupRiskResult(result)) {
    return `风险分 ${n} · 等级 ${levelZh} · 建议暂停付款并人工复核。`
  }
  return `风险分 ${n} · 等级 ${levelZh}`
})

const applyPreset = (preset: RiskPreset) => {
  activePreset.value = preset
  sessionVersion.value += 1
  chatStore.setMessages(
    preset.messages.map((message, index) => ({
      id: `${preset.id}-${index}`,
      type: message.type,
      content: message.content,
      timestamp: new Date(Date.now() + index * 60000),
      senderName: message.type === 'peer' ? '对方' : '我'
    }))
  )
  draft.value = ''
  draftSender.value = 'peer'
  lastSubmittedFingerprint.value = ''
  latestRiskResult.value = null
  riskDialogVisible.value = false
  voiceTranscripts.value = []
  void maybeAutoAnalyze('已识别到预置场景中的高风险信号，自动提交后台分析')
}

const restoreDefaultPreset = () => {
  if (riskPresets[0]) {
    applyPreset(riskPresets[0])
  }
}

const handleSend = (text: string) => {
  const content = text.trim()
  if (!content) {
    return
  }

  chatStore.addMessage(createMessage(draftSender.value, content, draftSender.value === 'peer' ? '对方' : '我'))
  draft.value = ''

  if (draftSender.value === 'peer') {
    void maybeAutoAnalyze('检测到对方消息中的高风险信号，已自动提交后台分析')
  }
}

const handleVoiceError = (message: string) => {
  chatStore.addMessage(createMessage('system', message || '语音识别失败'))
}

const handleVoiceSent = (payload: { text: string; model?: string; voice: ChatMessageVoice }) => {
  const content = payload.text.trim()
  if (!content) {
    return
  }

  voiceTranscripts.value.push({
    role: draftSender.value,
    text: content,
    model: payload.model,
    at: new Date().toISOString()
  })

  const label = draftSender.value === 'peer' ? '对方' : '我'
  chatStore.addMessage(
    createMessage(draftSender.value, content, label, undefined, 'voice_asr', payload.voice)
  )
  draft.value = ''

  if (draftSender.value === 'peer') {
    void maybeAutoAnalyze('已发送语音消息并自动提交后台分析（含 Dify 反思流）')
  } else {
    chatStore.addMessage(createMessage('system', '已发送语音消息；切换为对方发言后可继续自动分析'))
  }
}

const handlePickImages = async (files: File[]) => {
  if (chatStore.isLoading || files.length === 0) {
    return
  }
  const maxFiles = 5
  const images: ChatMessageImage[] = []
  try {
    for (const file of files.slice(0, maxFiles)) {
      if (!file.type.startsWith('image/')) {
        continue
      }
      if (file.size > 25 * 1024 * 1024) {
        chatStore.addMessage(createMessage('system', `已跳过大文件：${file.name}（请小于约 25MB）`))
        continue
      }
      const dataUrl = await fileToJpegDataUrl(file)
      images.push({ mime: 'image/jpeg', dataUrl })
    }
  } catch {
    chatStore.addMessage(createMessage('system', '图片处理失败，请换一张较小的 JPG/PNG 重试'))
    return
  }
  if (!images.length) {
    return
  }
  const caption = draft.value.trim()
  draft.value = ''
  const label = draftSender.value === 'peer' ? '对方' : '我'
  const text =
    caption ||
    (images.length > 1 ? `[${images.length} 张图片]` : '[图片]')
  chatStore.addMessage(createMessage(draftSender.value, text, label, images))

  if (draftSender.value === 'peer') {
    void maybeAutoAnalyze('检测到对方发送图片，已自动提交后台分析')
  }
}

const extractLinks = (messages: ChatMessage[]) => {
  const urlPattern = /https?:\/\/[^\s]+/g
  return messages.flatMap((message) => message.content.match(urlPattern) || [])
}

const extractFirstIp = (messages: ChatMessage[]) => {
  const ipPattern = /\b(?:\d{1,3}\.){3}\d{1,3}\b/
  for (const message of messages) {
    const match = message.content.match(ipPattern)
    if (match?.[0]) {
      return match[0]
    }
  }
  return ''
}

const riskSignalPatterns = [
  { label: '链接', pattern: /https?:\/\/|www\./i },
  { label: '二维码', pattern: /二维码|扫码|扫一扫/i },
  { label: '收款码', pattern: /收款码|付款码/i },
  { label: '转账催促', pattern: /转账|打款|汇款|垫付|先付|立刻付款/i },
  { label: '诱导点击', pattern: /点击链接|打开链接|复制链接|验证链接/i },
  { label: '时限施压', pattern: /3分钟内|马上处理|立刻处理|超时|否则|来不及了/i }
] as const

const getRiskSignals = (messages: ChatMessage[]) => {
  const peerMessages = messages.filter((message) => message.type === 'peer')
  const content = peerMessages.map((message) => message.content).join('\n')
  const labels: string[] = riskSignalPatterns
    .filter((item) => item.pattern.test(content))
    .map((item) => item.label)
  if (peerMessages.some((m) => m.images?.length)) {
    labels.push('图片')
  }
  return labels
}

const extractAmountFromMessages = (messages: ChatMessage[]) => {
  const pattern = /(\d+(?:\.\d{1,2})?)\s*(?:元|块|￥|¥)/g
  const amounts: number[] = []
  for (const message of messages) {
    const matches = message.content.matchAll(pattern)
    for (const match of matches) {
      const amount = Number(match[1])
      if (Number.isFinite(amount) && amount > 0) {
        amounts.push(amount)
      }
    }
  }
  return amounts.length ? amounts[amounts.length - 1] : undefined
}

const buildSimulatedTransactions = (amount: number | undefined) => {
  const simulation = activePreset.value?.simulation
  if (!simulation) {
    return undefined
  }
  return [
    {
      amount: amount || simulation.amount,
      currency: simulation.currency || 'CNY',
      relationLabel: simulation.relationLabel,
      relationType: simulation.relationType,
      payer: simulation.payer,
      payee: simulation.payee
    }
  ]
}

const buildAnalysisPayload = () => {
  const conversationMessages = getConversationMessages()
  const links = extractLinks(conversationMessages)
  const latestPeerMessage = [...conversationMessages].reverse().find((message) => message.type === 'peer')
  let latestPeerText = (latestPeerMessage?.content || '').trim()
  if (latestPeerMessage?.images?.length) {
    if (!latestPeerText) {
      latestPeerText = `[对方发送了 ${latestPeerMessage.images.length} 张图片]`
    } else {
      latestPeerText = `${latestPeerText} [含 ${latestPeerMessage.images.length} 张图片]`
    }
  }
  const detectedSignals = getRiskSignals(conversationMessages)
  const detectedIp = extractFirstIp(conversationMessages)
  const detectedAmount = extractAmountFromMessages(conversationMessages)
  const transactions = buildSimulatedTransactions(detectedAmount)

  const paymentData: Record<string, unknown> = {
    scene: 'WECHAT_CHAT_RISK',
    source: 'chat-risk-test-page',
    presetId: activePreset.value?.id,
    presetTitle: activePreset.value?.title,
    messageCount: conversationMessages.length,
    linkCount: links.length,
    links,
    amount: detectedAmount,
    detectedSignals,
    latestPeerMessage: latestPeerText,
    transactions,
    messages: conversationMessages.map((message) => {
      const row: Record<string, unknown> = {
        role: message.type,
        senderName: message.senderName || (message.type === 'peer' ? '对方' : '我'),
        content: message.content,
        timestamp: message.timestamp.toISOString()
      }
      if (message.images?.length) {
        row.imageDataUrls = message.images.map((img) => img.dataUrl)
      }
      if (message.source) {
        row.source = message.source
      }
      if (message.voice) {
        row.voiceDurationSec = message.voice.durationSec
        row.hasVoice = true
      }
      return row
    })
  }

  if (voiceTranscripts.value.length > 0) {
    paymentData.voiceTranscripts = voiceTranscripts.value.map(item => ({ ...item }))
    const latest = voiceTranscripts.value[voiceTranscripts.value.length - 1]
    paymentData.latestVoiceTranscript = latest.text
    paymentData.latestVoiceTranscriptRole = latest.role
    if (latest.model) {
      paymentData.voiceAsrModel = latest.model
    }
  }

  const profile: Record<string, string> = {}
  if (userAgeBand.value) {
    profile.ageBand = userAgeBand.value
  }
  if (userPersonality.value) {
    profile.personalityHint = userPersonality.value
  }
  if (userRiskLiteracy.value) {
    profile.riskLiteracy = userRiskLiteracy.value
  }
  if (Object.keys(profile).length > 0) {
    paymentData.userProfile = profile
  }

  return {
    ip: detectedIp || '8.8.8.8',
    paymentData
  }
}

const buildFingerprint = () =>
  JSON.stringify(
    getConversationMessages().map((message) => ({
      role: message.type,
      content: message.content,
      imageSig: message.images?.map((im) => im.dataUrl?.length ?? 0).join(',') ?? '',
      voiceSig: message.voice ? `${message.voice.durationSec}:${message.content}` : '',
      time: message.timestamp.toISOString()
    }))
  )

const submitForAnalysis = async (successText: string, forceOpenDialog = false) => {
  const conversationMessages = getConversationMessages()
  if (conversationMessages.length === 0) {
    return
  }

  const submitVersion = sessionVersion.value
  chatStore.setLoading(true)
  const result = await assess(buildAnalysisPayload())

  if (submitVersion !== sessionVersion.value) {
    chatStore.setLoading(false)
    return
  }

  chatStore.setLoading(false)

  if (result) {
    lastSubmittedFingerprint.value = buildFingerprint()
    latestRiskResult.value = result
    riskDialogVisible.value = forceOpenDialog || isPopupRiskResult(result)
    chatStore.addMessage(createMessage('system', successText))
    return
  }

  chatStore.addMessage(createMessage('system', error.value || '提交分析失败，请稍后重试'))
}

const maybeAutoAnalyze = async (successText: string) => {
  const conversationMessages = getConversationMessages()
  if (chatStore.isLoading || conversationMessages.length === 0) {
    return
  }

  const detectedSignals = getRiskSignals(conversationMessages)
  if (detectedSignals.length === 0) {
    return
  }

  const fingerprint = buildFingerprint()
  if (fingerprint === lastSubmittedFingerprint.value) {
    return
  }

  await submitForAnalysis(`${successText}（命中：${detectedSignals.join('、')}）`)
}

const handleAnalyze = async () => {
  await submitForAnalysis('当前聊天记录已手动提交到后台风控分析', true)
}

const handleClear = () => {
  if (window.confirm('确认清空当前测试对话吗？')) {
    sessionVersion.value += 1
    chatStore.clearMessages()
    chatStore.setLoading(false)
    draft.value = ''
    lastSubmittedFingerprint.value = ''
    latestRiskResult.value = null
    riskDialogVisible.value = false
    voiceTranscripts.value = []
    userAgeBand.value = ''
    userPersonality.value = ''
    userRiskLiteracy.value = ''
  }
}

if (chatStore.messages.length === 0) {
  restoreDefaultPreset()
}
</script>

<style scoped>
.chat-page {
  min-height: 100vh;
  padding: 24px;
  display: flex;
  justify-content: center;
  background: #f3f6fb;
}

.wechat-shell {
  width: min(100%, 860px);
  min-height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
  border-radius: 28px;
  overflow: hidden;
  background: #e9eef3;
  border: 8px solid #111827;
}

.wechat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  background: #f5f7fa;
  border-bottom: 1px solid #d7dde6;
}

.user-profile-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 10px;
  padding: 8px 14px 10px;
  background: #fdf4ff;
  border-bottom: 1px solid #e9d5ff;
  font-size: 12px;
}

.strip-hint {
  color: #6b21a8;
  font-weight: 700;
  margin-right: 4px;
}

.strip-select {
  flex: 1 1 auto;
  min-width: 120px;
  max-width: 200px;
  padding: 6px 8px;
  border-radius: 10px;
  border: 1px solid #d8b4fe;
  background: #fff;
  color: #4c1d95;
  font-size: 12px;
}

.header-main {
  display: flex;
  align-items: center;
  gap: 10px;
}

.assistant-avatar {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: #16c167;
  color: #fff;
  font-weight: 700;
}

.header-main h2 {
  margin: 0;
  font-size: 18px;
}

.header-main p {
  margin: 4px 0 0;
  font-size: 13px;
  color: #5b6577;
}

.status-pill {
  display: inline-block;
  margin-left: 8px;
  padding: 3px 8px;
  border-radius: 999px;
  background: #dcfce7;
  color: #166534;
}

.status-pill-alert {
  background: #fee2e2;
  color: #991b1b;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-btn {
  border: 0;
  padding: 8px 14px;
  border-radius: 999px;
  background: #e4e9f0;
  color: #334155;
  cursor: pointer;
}

.header-btn-secondary {
  background: #fee2e2;
  color: #991b1b;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 14px;
}

.day-divider {
  width: fit-content;
  margin: 0 auto 14px;
  padding: 4px 10px;
  border-radius: 999px;
  background: #dbe2ea;
  color: #4b5563;
  font-size: 12px;
}

.welcome-card {
  margin-bottom: 16px;
  padding: 14px;
  border-radius: 18px;
  background: #fff;
  border: 1px dashed #86efac;
}

.welcome-card h3 {
  margin: 0;
  font-size: 18px;
}

.welcome-card p {
  margin: 8px 0 0;
  color: #475569;
  line-height: 1.6;
}

.welcome-actions,
.preset-strip {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.welcome-actions {
  margin-top: 14px;
}

.welcome-chip,
.strip-chip {
  border: 0;
  border-radius: 999px;
  background: #dcfce7;
  color: #15803d;
  padding: 8px 12px;
  cursor: pointer;
}

.preset-strip {
  padding: 0 14px 12px;
}

.risk-dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.48);
  backdrop-filter: blur(8px);
}

.risk-dialog {
  width: min(100%, 480px);
  max-height: min(100vh - 40px, 640px);
  overflow-y: auto;
  padding: 24px;
  border-radius: 28px;
  background: linear-gradient(180deg, #fff8f8 0%, #ffffff 100%);
  border: 1px solid rgba(248, 113, 113, 0.24);
  box-shadow: 0 28px 80px rgba(15, 23, 42, 0.24);
  transition: transform 0.22s ease;
}

.risk-dialog-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.risk-dialog-eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #dc2626;
  text-transform: uppercase;
}

.risk-dialog-header h3 {
  margin: 0;
  font-size: 18px;
  line-height: 1.35;
  color: #111827;
}

.risk-dialog-summary {
  margin: 6px 0 0;
  line-height: 1.5;
  color: #475569;
  font-size: 13px;
}

.risk-dialog-close {
  border: 0;
  width: 38px;
  height: 38px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.06);
  color: #334155;
  font-size: 24px;
  line-height: 1;
  cursor: pointer;
}

.risk-dialog-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.risk-dialog-btn {
  border: 0;
  min-width: 112px;
  padding: 10px 18px;
  border-radius: 999px;
  cursor: pointer;
  font-weight: 600;
}

.risk-dialog-btn-secondary {
  background: #111827;
  color: #fff;
}

.risk-dialog-fade-enter-active,
.risk-dialog-fade-leave-active {
  transition:
    opacity 0.22s ease,
    transform 0.22s ease;
}

.risk-dialog-fade-enter-from,
.risk-dialog-fade-leave-to {
  opacity: 0;
}

.risk-dialog-fade-enter-from .risk-dialog,
.risk-dialog-fade-leave-to .risk-dialog {
  transform: translateY(12px) scale(0.98);
}

@media (max-width: 640px) {
  .chat-page {
    padding: 0;
  }

  .wechat-shell {
    border-radius: 0;
    border-width: 0;
    min-height: 100vh;
  }

  .wechat-header {
    align-items: flex-start;
  }

  .header-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .risk-dialog-mask {
    padding: 12px;
  }

  .risk-dialog {
    padding: 16px;
    border-radius: 22px;
    max-height: calc(100vh - 24px);
  }

  .risk-dialog-header h3 {
    font-size: 20px;
  }
}
</style>
