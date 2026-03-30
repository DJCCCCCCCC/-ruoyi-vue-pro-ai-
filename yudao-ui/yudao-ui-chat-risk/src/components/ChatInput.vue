<template>
  <div class="chat-input">
    <div class="toolbar">
      <div class="sender-switch">
        <button
          type="button"
          :class="['sender-btn', { active: sender === 'peer' }]"
          :disabled="disabled"
          @click="emit('update:sender', 'peer')"
        >
          对方发言
        </button>
        <button
          type="button"
          :class="['sender-btn', { active: sender === 'self' }]"
          :disabled="disabled"
          @click="emit('update:sender', 'self')"
        >
          我发言
        </button>
      </div>
      <button type="button" class="ghost-btn" @click="emit('reset')" :disabled="disabled">
        载入推荐样例
      </button>
    </div>
    <label class="editor-shell">
      <textarea
        :value="modelValue"
        :placeholder="sender === 'peer' ? '输入对方发来的消息，比如付款链接、转账催促、退款通知...' : '输入我方回复，比如追问、确认、拒绝转账...'"
        :disabled="disabled"
        rows="4"
        @input="handleInput"
        @keydown="handleKeydown"
      ></textarea>
    </label>
    <div class="input-footer">
      <span class="char-count">Enter 发消息，检测到链接/二维码等高风险信号会自动提交后台</span>
      <div class="action-group">
        <button
          type="button"
          class="ghost-btn"
          :disabled="disabled || analyzeDisabled"
          @click="emit('analyze')"
        >
          提交到后台分析
        </button>
        <button
          type="button"
          class="send-btn"
          :disabled="disabled || !modelValue.trim()"
          @click="handleSend"
        >
          <span v-if="!disabled">发送消息</span>
          <LoadingDots v-else />
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ChatRole } from '@/types'
import LoadingDots from './LoadingDots.vue'

interface Props {
  modelValue: string
  sender: Extract<ChatRole, 'self' | 'peer'>
  analyzeDisabled?: boolean
  disabled?: boolean
}

interface Emits {
  (e: 'update:modelValue', value: string): void
  (e: 'update:sender', value: Extract<ChatRole, 'self' | 'peer'>): void
  (e: 'send', value: string): void
  (e: 'analyze'): void
  (e: 'reset'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const handleInput = (event: Event) => {
  emit('update:modelValue', (event.target as HTMLTextAreaElement).value)
}

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    handleSend()
  }
}

const handleSend = () => {
  if (!props.modelValue.trim() || props.disabled) {
    return
  }

  emit('send', props.modelValue)
}
</script>

<style scoped>
.chat-input {
  padding: 12px 14px 14px;
  background: rgba(248, 250, 252, 0.92);
  border-top: 1px solid rgba(148, 163, 184, 0.22);
  backdrop-filter: blur(12px);
}

.toolbar,
.input-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.toolbar {
  margin-bottom: 10px;
}

.char-count {
  font-size: 12px;
  color: var(--text-muted);
}

.sender-switch,
.action-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.editor-shell {
  display: block;
  border-radius: 18px;
  background: #fff;
  border: 1px solid rgba(148, 163, 184, 0.2);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

textarea {
  width: 100%;
  border: 0;
  background: transparent;
  resize: none;
  padding: 14px 16px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-primary);
  font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
}

textarea:focus {
  outline: none;
}

textarea:disabled {
  cursor: not-allowed;
}

.input-footer {
  margin-top: 12px;
}

.sender-btn,
.ghost-btn,
.send-btn {
  border: 0;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
}

.sender-btn {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.12);
  color: var(--text-secondary);
}

.sender-btn.active {
  background: rgba(34, 197, 94, 0.14);
  color: #15803d;
  font-weight: 600;
}

.ghost-btn {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.14);
  color: var(--text-secondary);
}

.send-btn {
  min-width: 112px;
  padding: 10px 18px;
  border-radius: 999px;
  background: linear-gradient(135deg, #1fc16b, #12a150);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  box-shadow: 0 12px 24px rgba(18, 161, 80, 0.28);
}

.ghost-btn:hover:not(:disabled),
.send-btn:hover:not(:disabled) {
  transform: translateY(-1px);
}

.ghost-btn:disabled,
.send-btn:disabled {
  cursor: not-allowed;
  opacity: 0.58;
  box-shadow: none;
  transform: none;
}

@media (max-width: 720px) {
  .toolbar,
  .input-footer {
    flex-wrap: wrap;
  }

  .sender-switch,
  .action-group {
    width: 100%;
  }

  .sender-btn,
  .ghost-btn,
  .send-btn {
    flex: 1;
  }
}
</style>
