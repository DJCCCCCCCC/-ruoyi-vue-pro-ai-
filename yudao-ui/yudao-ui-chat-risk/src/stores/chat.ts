import { defineStore } from 'pinia'
import type { ChatMessage } from '@/types'

export const useChatStore = defineStore('chat', {
  state: () => ({
    messages: [] as ChatMessage[],
    isLoading: false
  }),

  actions: {
    addMessage(message: ChatMessage) {
      this.messages.push(message)
    },

    setMessages(messages: ChatMessage[]) {
      this.messages = messages
    },

    clearMessages() {
      this.messages = []
    },

    setLoading(status: boolean) {
      this.isLoading = status
    }
  }
})
