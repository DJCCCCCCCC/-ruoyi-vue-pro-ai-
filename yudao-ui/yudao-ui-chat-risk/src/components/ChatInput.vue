<template>
  <div class="chat-input">
    <div class="toolbar">
      <div class="toolbar-left">
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
        <div class="input-mode-switch">
          <button
            type="button"
            :class="['mode-btn', { active: inputMode === 'text' }]"
            :disabled="disabled || voiceRecorder.state.value !== 'idle'"
            @click="inputMode = 'text'"
          >
            键盘
          </button>
          <button
            type="button"
            :class="['mode-btn', { active: inputMode === 'voice' }]"
            :disabled="disabled || voiceRecorder.state.value === 'transcribing'"
            @click="inputMode = 'voice'"
          >
            语音
          </button>
        </div>
        <input
          ref="fileInputRef"
          type="file"
          class="sr-only"
          accept="image/jpeg,image/png,image/webp,image/gif"
          multiple
          :disabled="disabled"
          @change="onImageFiles"
        />
        <button
          v-if="inputMode === 'text'"
          type="button"
          class="ghost-btn ghost-btn-icon"
          title="上传图片"
          :disabled="disabled"
          @click="openFilePicker"
        >
          图片
        </button>
      </div>
      <button type="button" class="ghost-btn" @click="emit('reset')" :disabled="disabled">
        载入推荐样例
      </button>
    </div>

    <div v-if="inputMode === 'text'" class="editor-shell">
      <textarea
        :value="modelValue"
        :placeholder="
          sender === 'peer'
            ? '输入对方发来的消息，比如付款链接、转账催促、退款通知...'
            : '输入我方回复，比如追问、确认、拒绝转账...'
        "
        :disabled="disabled"
        rows="4"
        @input="handleInput"
        @keydown="handleKeydown"
      ></textarea>
    </div>

    <div v-else class="voice-shell">
      <div
        v-if="voiceRecorder.state.value === 'recording'"
        class="voice-recording-panel"
      >
        <span class="rec-dot" />
        <span class="rec-timer">{{ formatVoiceDuration(voiceRecorder.recordingSeconds.value) }}</span>
        <span class="rec-hint">正在录音，点击按钮结束</span>
        <button type="button" class="rec-cancel" :disabled="disabled" @click="handleCancelRecording">
          取消
        </button>
      </div>

      <div
        v-else-if="voiceRecorder.state.value === 'transcribing'"
        class="voice-transcribing-panel"
      >
        <LoadingDots />
        <span>正在识别语音（GLM-ASR）…</span>
      </div>

      <button
        v-else
        type="button"
        class="voice-hold-btn"
        :disabled="disabled"
        @click="handleVoiceClick"
      >
        <span class="mic-icon" aria-hidden="true" />
        <span class="voice-hold-label">点击开始录音</span>
        <span class="voice-hold-sub">松开后自动转文字并发送</span>
      </button>

      <button
        v-if="voiceRecorder.state.value === 'recording'"
        type="button"
        class="voice-stop-btn"
        :disabled="disabled"
        @click="handleVoiceClick"
      >
        结束并发送
      </button>
    </div>

    <div class="input-footer">
      <span class="char-count">
        <template v-if="inputMode === 'voice'">
          语音模式：录音后自动转写并发送，对方发言将触发风控与 Dify 分析
        </template>
        <template v-else> Enter 发文字消息；可切换「语音」发语音 </template>
      </span>
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
          v-if="inputMode === 'text'"
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
import { ref } from 'vue'
import { transcribeSpeech } from '@/api/risk'
import { useVoiceRecorder } from '@/composables/useVoiceRecorder'
import { blobToDataUrl, formatVoiceDuration } from '@/utils/audio'
import type { ChatMessageVoice, ChatRole } from '@/types'
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
  (e: 'pickImages', files: File[]): void
  (e: 'voiceError', message: string): void
  (e: 'voiceSent', payload: { text: string; model?: string; voice: ChatMessageVoice }): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()
const fileInputRef = ref<HTMLInputElement | null>(null)
const voiceRecorder = useVoiceRecorder()
const inputMode = ref<'text' | 'voice'>('text')

const extensionFromMime = (mime: string) => {
  if (mime.includes('webm')) return 'webm'
  if (mime.includes('ogg')) return 'ogg'
  if (mime.includes('mp4') || mime.includes('m4a')) return 'm4a'
  if (mime.includes('wav')) return 'wav'
  return 'webm'
}

const handleCancelRecording = async () => {
  await voiceRecorder.cancelRecording()
}

const handleVoiceClick = async () => {
  if (props.disabled) {
    return
  }
  if (voiceRecorder.state.value === 'recording') {
    const durationSec = Math.max(1, Math.round(voiceRecorder.recordingSeconds.value))
    const blob = await voiceRecorder.stopRecording()
    if (!blob) {
      if (voiceRecorder.errorMessage.value) {
        emit('voiceError', voiceRecorder.errorMessage.value)
      }
      return
    }
    voiceRecorder.setTranscribing(true)
    try {
      const filename = `recording.${extensionFromMime(blob.type)}`
      const [result, dataUrl] = await Promise.all([
        transcribeSpeech(blob, filename),
        blobToDataUrl(blob)
      ])
      const text = result.text?.trim()
      if (!text) {
        emit('voiceError', '未识别到有效语音内容')
        return
      }
      emit('voiceSent', {
        text,
        model: result.model,
        voice: {
          mime: blob.type || 'audio/webm',
          dataUrl,
          durationSec
        }
      })
    } catch (error) {
      const message = error instanceof Error ? error.message : '语音识别失败'
      emit('voiceError', message)
    } finally {
      voiceRecorder.setTranscribing(false)
    }
    return
  }
  if (voiceRecorder.state.value === 'transcribing') {
    return
  }
  const started = await voiceRecorder.startRecording()
  if (!started && voiceRecorder.errorMessage.value) {
    emit('voiceError', voiceRecorder.errorMessage.value)
  }
}

const openFilePicker = () => {
  if (props.disabled) return
  fileInputRef.value?.click()
}

const onImageFiles = (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = input.files ? Array.from(input.files) : []
  input.value = ''
  if (!files.length || props.disabled) {
    return
  }
  emit('pickImages', files)
}

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

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.input-mode-switch {
  display: flex;
  padding: 3px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.14);
}

.mode-btn {
  border: 0;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 13px;
  cursor: pointer;
  color: var(--text-secondary);
  background: transparent;
  transition: background 0.18s ease, color 0.18s ease;
}

.mode-btn.active {
  background: #fff;
  color: #15803d;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
}

.mode-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.ghost-btn-icon {
  flex-shrink: 0;
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

.editor-shell,
.voice-shell {
  display: block;
  border-radius: 18px;
  background: #fff;
  border: 1px solid rgba(148, 163, 184, 0.2);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.voice-shell {
  padding: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  min-height: 120px;
  justify-content: center;
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

.voice-hold-btn {
  width: 100%;
  max-width: 320px;
  border: 0;
  border-radius: 16px;
  padding: 18px 20px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  background: linear-gradient(180deg, #f8fafc 0%, #eef2f7 100%);
  border: 1px solid rgba(148, 163, 184, 0.25);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.voice-hold-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
}

.voice-hold-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.mic-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #22c55e, #16a34a);
  display: block;
  position: relative;
  box-shadow: 0 10px 24px rgba(34, 197, 94, 0.35);
}

.mic-icon::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 10px;
  height: 16px;
  margin: -10px 0 0 -5px;
  border-radius: 6px;
  background: #fff;
}

.mic-icon::before {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 6px;
  width: 18px;
  height: 8px;
  margin-left: -9px;
  border: 2px solid #fff;
  border-top: 0;
  border-radius: 0 0 10px 10px;
}

.voice-hold-label {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.voice-hold-sub {
  font-size: 12px;
  color: var(--text-muted);
}

.voice-recording-panel {
  width: 100%;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 10px 14px;
  padding: 12px;
  border-radius: 14px;
  background: rgba(239, 68, 68, 0.08);
  border: 1px solid rgba(239, 68, 68, 0.2);
}

.rec-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #ef4444;
  animation: rec-blink 1s step-end infinite;
}

.rec-timer {
  font-size: 18px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: #b91c1c;
}

.rec-hint {
  font-size: 13px;
  color: var(--text-secondary);
}

.rec-cancel {
  border: 0;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.2);
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 12px;
}

.voice-stop-btn {
  width: 100%;
  max-width: 280px;
  padding: 12px 18px;
  border: 0;
  border-radius: 999px;
  background: linear-gradient(135deg, #ef4444, #dc2626);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 10px 24px rgba(239, 68, 68, 0.35);
}

.voice-transcribing-panel {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: var(--text-secondary);
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
.send-btn:hover:not(:disabled),
.voice-stop-btn:hover:not(:disabled) {
  transform: translateY(-1px);
}

.ghost-btn:disabled,
.send-btn:disabled,
.voice-stop-btn:disabled {
  cursor: not-allowed;
  opacity: 0.58;
  box-shadow: none;
  transform: none;
}

@keyframes rec-blink {
  50% {
    opacity: 0.35;
  }
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
