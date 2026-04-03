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
        <button type="button" class="header-btn" @click="handleClear">清空</button>
      </header>

      <div ref="messagesRef" class="chat-messages">
        <div class="day-divider">今天</div>

        <section v-if="chatStore.messages.length === 0" class="welcome-card">
          <h3>这里不是和风控助手对话，而是模拟你和对方的正常聊天。</h3>
          <p>对方可以发付款链接、退款通知、催促转账等内容；分析结果会提交到后台风控接口。</p>
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
const sessionVersion = ref(0)

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

const getConversationMessages = () => chatStore.messages.filter((message) => message.type !== 'system')

const applyPreset = (preset: RiskPreset) => {
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
  return riskSignalPatterns.filter((item) => item.pattern.test(content)).map((item) => item.label)
}

const buildAnalysisPayload = () => {
  const conversationMessages = getConversationMessages()
  const links = extractLinks(conversationMessages)
  const latestPeerMessage = [...conversationMessages].reverse().find((message) => message.type === 'peer')
  const detectedSignals = getRiskSignals(conversationMessages)
  const detectedIp = extractFirstIp(conversationMessages)

  return {
    ip: detectedIp || '8.8.8.8',
    paymentData: {
      scene: 'WECHAT_CHAT_RISK',
      source: 'chat-risk-test-page',
      messageCount: conversationMessages.length,
      linkCount: links.length,
      links,
      detectedSignals,
      latestPeerMessage: latestPeerMessage?.content || '',
      messages: conversationMessages.map((message) => ({
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
    getConversationMessages().map((message) => ({
      role: message.type,
      content: message.content,
      time: message.timestamp.toISOString()
    }))
  )

const submitForAnalysis = async (successText: string) => {
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
  await submitForAnalysis('当前聊天记录已手动提交到后台风控分析')
}

const handleClear = () => {
  if (window.confirm('确认清空当前测试对话吗？')) {
    sessionVersion.value += 1
    chatStore.clearMessages()
    chatStore.setLoading(false)
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

.header-btn {
  border: 0;
  padding: 8px 14px;
  border-radius: 999px;
  background: #e4e9f0;
  color: #334155;
  cursor: pointer;
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

@media (max-width: 640px) {
  .chat-page {
    padding: 0;
  }

  .wechat-shell {
    border-radius: 0;
    border-width: 0;
    min-height: 100vh;
  }
}
</style>