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

const persistMessages = (messages: ChatMessage[]) => {
  localStorage.setItem(CHAT_STORAGE_KEY, JSON.stringify(messages))
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
