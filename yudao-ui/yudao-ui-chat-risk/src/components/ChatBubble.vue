<template>
  <div v-if="message.type === 'system'" class="system-row">
    <span class="system-tag">{{ message.content }}</span>
  </div>
  <div v-else :class="['message-row', `is-${message.type}`]">
    <div v-if="message.type !== 'self'" class="avatar avatar-peer">对</div>
    <div class="message-main">
      <div class="message-meta">
        <span class="speaker">{{ message.senderName || (message.type === 'self' ? '我' : '对方') }}</span>
        <span class="time">{{ formatTime(message.timestamp) }}</span>
      </div>
      <div class="bubble">
        <div v-if="message.content" class="message-text">
          <pre v-if="message.type === 'self'">{{ message.content }}</pre>
          <p v-else>{{ message.content }}</p>
        </div>
      </div>
    </div>
    <div v-if="message.type === 'self'" class="avatar avatar-self">我</div>
  </div>
</template>

<script setup lang="ts">
import dayjs from 'dayjs'
import type { ChatMessage } from '@/types'

interface Props {
  message: ChatMessage
}

defineProps<Props>()

const formatTime = (date: Date) => dayjs(date).format('HH:mm')
</script>

<style scoped>
.system-row {
  display: flex;
  justify-content: center;
  margin: 12px 0 18px;
}

.system-tag {
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(17, 24, 39, 0.08);
  color: rgba(17, 24, 39, 0.65);
  font-size: 12px;
  backdrop-filter: blur(8px);
}

.message-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 18px;
  animation: slide-in 0.25s ease;
}

.message-row.is-self {
  justify-content: flex-end;
}

.message-main {
  max-width: min(78%, 540px);
}

.message-row.is-self .message-main {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.message-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
  color: var(--text-muted);
  font-size: 12px;
}

.bubble {
  position: relative;
  border-radius: 18px;
  padding: 12px 14px;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.08);
}

.is-peer .bubble {
  background: rgba(255, 255, 255, 0.96);
  border-top-left-radius: 6px;
}

.is-self .bubble {
  background: var(--wechat-green);
  border-top-right-radius: 6px;
}

.bubble::after {
  content: '';
  position: absolute;
  top: 10px;
  width: 10px;
  height: 10px;
  transform: rotate(45deg);
  background: inherit;
}

.is-peer .bubble::after {
  left: -4px;
}

.is-self .bubble::after {
  right: -4px;
}

.message-text p,
.message-text pre {
  margin: 0;
  line-height: 1.7;
  font-size: 14px;
  color: var(--text-primary);
  white-space: pre-wrap;
  word-break: break-word;
}

.message-text pre {
  font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
}

.avatar {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  font-size: 14px;
  font-weight: 700;
  color: #fff;
  box-shadow: 0 10px 20px rgba(15, 23, 42, 0.12);
  flex-shrink: 0;
}

.avatar-peer {
  background: linear-gradient(135deg, #64748b, #94a3b8);
}

.avatar-self {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
}

@keyframes slide-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 640px) {
  .message-main {
    max-width: calc(100% - 56px);
  }

  .avatar {
    width: 34px;
    height: 34px;
    border-radius: 10px;
  }
}
</style>
