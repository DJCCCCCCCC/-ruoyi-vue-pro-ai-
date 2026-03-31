<template>
  <div class="chat-page">
    <div class="ambient ambient-left"></div>
    <div class="ambient ambient-right"></div>

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
        <button type="button" class="header-btn" @click="handleClear">清空</button>
      </header>

      <div ref="messagesRef" class="chat-messages">
        <div class="day-divider">今天</div>

        <section v-if="chatStore.messages.length === 0" class="welcome-card">
          <h3>这里不是和风控助手对话，而是模拟你和对方的正常聊天。</h3>
          <p>对方可以发付款链接、退款通知、催促转账等内容；分析结果会去后台前端显示。</p>
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
        :analyze-disabled="chatStore.messages.length === 0"
        :disabled="chatStore.isLoading"
        @send="handleSend"
        @analyze="handleAnalyze"
        @reset="restoreDefaultPreset"
      />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import ChatBubble from '@/components/ChatBubble.vue'
import ChatInput from '@/components/ChatInput.vue'
import { useRiskAssess } from '@/composables/useRiskAssess'
import { riskPresets, type RiskPreset } from '@/constants/presets'
import { useChatStore } from '@/stores/chat'
import type { ChatMessage, ChatRole } from '@/types'

const chatStore = useChatStore()
const { assess, error } = useRiskAssess()
const messagesRef = ref<HTMLElement>()
const draft = ref('')
const draftSender = ref<Extract<ChatRole, 'self' | 'peer'>>('peer')
const lastSubmittedFingerprint = ref('')

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
  senderName?: string
): ChatMessage => ({
  id: `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
  type,
  content,
  timestamp: new Date(),
  senderName
})

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

watch(() => chatStore.messages.length, scrollToBottom)

const applyPreset = (preset: RiskPreset) => {
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
    void maybeAutoAnalyze('检测到对方消息中包含高风险信号，已自动提交后台分析')
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

  return riskSignalPatterns
    .filter((item) => item.pattern.test(content))
    .map((item) => item.label)
}

const buildAnalysisPayload = () => {
  const links = extractLinks(chatStore.messages)
  const latestPeerMessage = [...chatStore.messages].reverse().find((message) => message.type === 'peer')
  const detectedSignals = getRiskSignals(chatStore.messages)
  const detectedIp = extractFirstIp(chatStore.messages)

  return {
    ip: detectedIp || '8.8.8.8',
    paymentData: {
      scene: 'WECHAT_CHAT_RISK',
      source: 'chat-risk-test-page',
      messageCount: chatStore.messages.length,
      linkCount: links.length,
      links,
      detectedSignals,
      latestPeerMessage: latestPeerMessage?.content || '',
      messages: chatStore.messages.map((message) => ({
        role: message.type,
        senderName: message.senderName || (message.type === 'peer' ? '对方' : '我'),
        content: message.content,
        timestamp: message.timestamp.toISOString()
      }))
    }
  }
}

const buildFingerprint = () =>
  JSON.stringify(
    chatStore.messages.map((message) => ({
      role: message.type,
      content: message.content,
      time: message.timestamp.toISOString()
    }))
  )

const submitForAnalysis = async (successText: string) => {
  if (chatStore.messages.length === 0) {
    return
  }

  chatStore.setLoading(true)
  const result = await assess(buildAnalysisPayload())
  chatStore.setLoading(false)

  if (result) {
    lastSubmittedFingerprint.value = buildFingerprint()
    chatStore.addMessage(createMessage('system', successText))
    return
  }

  chatStore.addMessage(createMessage('system', error.value || '提交分析失败，请稍后重试'))
}

const maybeAutoAnalyze = async (successText: string) => {
  if (chatStore.isLoading || chatStore.messages.length === 0) {
    return
  }

  const detectedSignals = getRiskSignals(chatStore.messages)
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
  await submitForAnalysis('当前聊天记录已手动提交到后台风控分析')
}

const handleClear = () => {
  if (window.confirm('确认清空当前测试对话吗？')) {
    chatStore.clearMessages()
    draft.value = ''
    lastSubmittedFingerprint.value = ''
  }
}

if (chatStore.messages.length === 0) {
  restoreDefaultPreset()
}
</script>

<style scoped>
.chat-page {
  position: relative;
  min-height: 100vh;
  padding: 28px;
  display: flex;
  justify-content: center;
  align-items: stretch;
}

.ambient {
  position: absolute;
  border-radius: 999px;
  filter: blur(32px);
  opacity: 0.6;
  pointer-events: none;
}

.ambient-left {
  width: 280px;
  height: 280px;
  left: -60px;
  top: 40px;
  background: rgba(34, 197, 94, 0.18);
}

.ambient-right {
  width: 360px;
  height: 360px;
  right: -120px;
  bottom: -20px;
  background: rgba(59, 130, 246, 0.12);
}

.wechat-shell {
  position: relative;
  z-index: 1;
}

.wechat-shell {
  width: min(100%, 860px);
  min-height: calc(100vh - 56px);
  display: flex;
  flex-direction: column;
  border-radius: 34px;
  overflow: hidden;
  background: rgba(233, 238, 243, 0.92);
  border: 10px solid rgba(17, 24, 39, 0.88);
  box-shadow: 0 30px 80px rgba(15, 23, 42, 0.24);
}

.wechat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 16px 18px;
  background: linear-gradient(180deg, rgba(242, 245, 248, 0.98), rgba(232, 237, 242, 0.94));
  border-bottom: 1px solid rgba(148, 163, 184, 0.18);
}

.header-main {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.assistant-avatar {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 14px;
  background: linear-gradient(135deg, #16c167, #0fa958);
  color: #fff;
  font-weight: 700;
  box-shadow: 0 12px 24px rgba(15, 169, 88, 0.22);
  flex-shrink: 0;
}

.header-main h2 {
  margin: 0;
  font-size: 18px;
}

.header-main p {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-size: 13px;
}

.status-pill {
  display: inline-block;
  margin-left: 8px;
  padding: 4px 8px;
  border-radius: 999px;
  background: rgba(34, 197, 94, 0.12);
  color: #15803d;
}

.status-pill-alert {
  background: rgba(248, 113, 113, 0.14);
  color: #b91c1c;
}

.header-btn {
  border: 0;
  padding: 10px 14px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.08);
  color: var(--text-secondary);
  cursor: pointer;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 18px 18px 10px;
  background:
    radial-gradient(circle at top, rgba(255, 255, 255, 0.74), transparent 32%),
    linear-gradient(180deg, rgba(241, 245, 249, 0.9), rgba(229, 234, 239, 0.94));
}

.day-divider {
  width: fit-content;
  margin: 0 auto 18px;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(17, 24, 39, 0.08);
  color: rgba(17, 24, 39, 0.6);
  font-size: 12px;
}

.welcome-card {
  margin-bottom: 24px;
  padding: 18px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.76);
  border: 1px dashed rgba(34, 197, 94, 0.26);
}

.welcome-card h3 {
  margin: 0;
  font-size: 22px;
}

.welcome-card p {
  margin: 10px 0 0;
  color: var(--text-secondary);
  line-height: 1.7;
}

.welcome-actions,
.preset-strip {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.welcome-actions {
  margin-top: 16px;
}

.welcome-chip,
.strip-chip {
  border: 0;
  border-radius: 999px;
  background: rgba(34, 197, 94, 0.1);
  color: #15803d;
  padding: 9px 14px;
  cursor: pointer;
  font-size: 13px;
}

.preset-strip {
  padding: 0 14px 12px;
}

@media (max-width: 1180px) {
  .wechat-shell {
    min-height: 75vh;
  }
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
    padding-top: max(16px, env(safe-area-inset-top));
  }

  .chat-messages {
    padding-left: 12px;
    padding-right: 12px;
  }
}
</style>
