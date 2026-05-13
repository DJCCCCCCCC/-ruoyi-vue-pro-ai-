import { defineStore } from 'pinia'
import type { ChatMessage } from '@/types'

const CHAT_STORAGE_KEY = 'chat-risk-messages'

const restoreMessages = (): ChatMessage[] => {
  try {
    const raw = localStorage.getItem(CHAT_STORAGE_KEY)
    if (!raw) {
      return []
    }
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) {
      return []
    }
    return parsed.map((item: any) => ({
      ...item,
      timestamp: item?.timestamp ? new Date(item.timestamp) : new Date()
    }))
  } catch {
    return []
  }
}

/** 避免 localStorage 被巨量 base64 撑爆；持久化时去掉图片二进制，仅保留文案 */
const stripImagesForStorage = (messages: ChatMessage[]) =>
  messages.map((m) => ({
    ...m,
    images: undefined,
    content:
      m.images?.length && !m.content.trim()
        ? '[图片]'
        : m.images?.length && m.content.trim()
          ? `${m.content}\n[含 ${m.images.length} 张图片，刷新后需重新上传]`
          : m.content
  }))

const persistMessages = (messages: ChatMessage[]) => {
  const stripped = stripImagesForStorage(messages)
  try {
    localStorage.setItem(CHAT_STORAGE_KEY, JSON.stringify(stripped))
  } catch {
    try {
      localStorage.setItem(
        CHAT_STORAGE_KEY,
        JSON.stringify(
          stripped.map((m) => ({
            id: m.id,
            type: m.type,
            content: m.content || '[图片]',
            timestamp: m.timestamp,
            senderName: m.senderName
          }))
        )
      )
    } catch {
      /* ignore quota */
    }
  }
}

export const useChatStore = defineStore('chat', {
  state: () => ({
    messages: restoreMessages(),
    isLoading: false
  }),

  actions: {
    addMessage(message: ChatMessage) {
      this.messages.push(message)
      persistMessages(this.messages)
    },

    setMessages(messages: ChatMessage[]) {
      this.messages = messages
      persistMessages(this.messages)
    },

    clearMessages() {
      this.messages = []
      persistMessages(this.messages)
    },

    setLoading(status: boolean) {
      this.isLoading = status
    }
  }
})
