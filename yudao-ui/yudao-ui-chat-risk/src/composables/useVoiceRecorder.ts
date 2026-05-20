import { onUnmounted, ref } from 'vue'

export type VoiceRecorderState = 'idle' | 'recording' | 'transcribing'

const pickMimeType = () => {
  const candidates = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4', 'audio/ogg']
  for (const type of candidates) {
    if (typeof MediaRecorder !== 'undefined' && MediaRecorder.isTypeSupported(type)) {
      return type
    }
  }
  return ''
}

export const useVoiceRecorder = () => {
  const state = ref<VoiceRecorderState>('idle')
  const errorMessage = ref('')
  const recordingSeconds = ref(0)

  let mediaRecorder: MediaRecorder | null = null
  let mediaStream: MediaStream | null = null
  const chunks: Blob[] = []
  let discardOnStop = false
  let recordingTimer: ReturnType<typeof setInterval> | null = null
  let recordingStartedAt = 0

  const clearRecordingTimer = () => {
    if (recordingTimer) {
      clearInterval(recordingTimer)
      recordingTimer = null
    }
  }

  const startRecordingTimer = () => {
    clearRecordingTimer()
    recordingStartedAt = Date.now()
    recordingSeconds.value = 0
    recordingTimer = setInterval(() => {
      recordingSeconds.value = (Date.now() - recordingStartedAt) / 1000
    }, 100)
  }

  const cleanupStream = () => {
    if (mediaStream) {
      mediaStream.getTracks().forEach(track => track.stop())
      mediaStream = null
    }
  }

  const stopRecording = (): Promise<Blob | null> => {
    return new Promise(resolve => {
      clearRecordingTimer()
      if (!mediaRecorder || mediaRecorder.state === 'inactive') {
        cleanupStream()
        state.value = 'idle'
        recordingSeconds.value = 0
        resolve(null)
        return
      }

      mediaRecorder.onstop = () => {
        const mime = mediaRecorder?.mimeType || chunks[0]?.type || 'audio/webm'
        const blob = !discardOnStop && chunks.length ? new Blob(chunks, { type: mime }) : null
        discardOnStop = false
        chunks.length = 0
        cleanupStream()
        mediaRecorder = null
        state.value = 'idle'
        const duration = recordingSeconds.value
        recordingSeconds.value = 0
        resolve(blob && blob.size > 0 ? blob : null)
        void duration
      }

      try {
        mediaRecorder.stop()
      } catch {
        cleanupStream()
        state.value = 'idle'
        recordingSeconds.value = 0
        resolve(null)
      }
    })
  }

  const cancelRecording = async () => {
    if (state.value !== 'recording') {
      return
    }
    discardOnStop = true
    await stopRecording()
  }

  const startRecording = async () => {
    errorMessage.value = ''
    if (state.value !== 'idle') {
      return false
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      errorMessage.value = '当前浏览器不支持麦克风录音'
      return false
    }

    try {
      mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true })
      chunks.length = 0
      discardOnStop = false
      const mimeType = pickMimeType()
      mediaRecorder = mimeType ? new MediaRecorder(mediaStream, { mimeType }) : new MediaRecorder(mediaStream)
      mediaRecorder.ondataavailable = event => {
        if (event.data.size > 0) {
          chunks.push(event.data)
        }
      }
      mediaRecorder.start()
      state.value = 'recording'
      startRecordingTimer()
      return true
    } catch {
      cleanupStream()
      errorMessage.value = '无法访问麦克风，请检查浏览器权限'
      state.value = 'idle'
      recordingSeconds.value = 0
      return false
    }
  }

  const setTranscribing = (value: boolean) => {
    state.value = value ? 'transcribing' : 'idle'
  }

  const getRecordingDuration = () => recordingSeconds.value

  onUnmounted(() => {
    clearRecordingTimer()
    if (mediaRecorder && mediaRecorder.state !== 'inactive') {
      discardOnStop = true
      try {
        mediaRecorder.stop()
      } catch {
        /* ignore */
      }
    }
    cleanupStream()
  })

  return {
    state,
    errorMessage,
    recordingSeconds,
    startRecording,
    stopRecording,
    cancelRecording,
    setTranscribing,
    getRecordingDuration
  }
}
