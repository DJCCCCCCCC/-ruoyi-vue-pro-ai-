<template>
  <div class="voice-bar" :class="{ playing: isPlaying }">
    <button type="button" class="play-btn" :aria-label="isPlaying ? '暂停' : '播放'" @click="togglePlay">
      <span class="play-icon">{{ isPlaying ? '❚❚' : '▶' }}</span>
    </button>
    <div class="wave-track" @click="togglePlay">
      <span v-for="i in 12" :key="i" class="wave-bar" :style="{ animationDelay: `${i * 0.05}s` }" />
    </div>
    <span class="duration">{{ formatVoiceDuration(displayDuration) }}</span>
    <audio ref="audioRef" :src="src" preload="metadata" @loadedmetadata="onMeta" @timeupdate="onTime" @ended="onEnded" />
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { formatVoiceDuration } from '@/utils/audio'

interface Props {
  src: string
  durationSec?: number
}

const props = defineProps<Props>()
const audioRef = ref<HTMLAudioElement | null>(null)
const isPlaying = ref(false)
const metaDuration = ref(0)
const currentTime = ref(0)

const displayDuration = computed(() => {
  if (isPlaying.value) {
    return Math.max(props.durationSec ?? 0, metaDuration.value, currentTime.value)
  }
  return props.durationSec ?? metaDuration.value ?? 0
})

const togglePlay = async () => {
  const el = audioRef.value
  if (!el) {
    return
  }
  if (isPlaying.value) {
    el.pause()
    isPlaying.value = false
    return
  }
  try {
    await el.play()
    isPlaying.value = true
  } catch {
    isPlaying.value = false
  }
}

const onMeta = () => {
  const el = audioRef.value
  if (el && Number.isFinite(el.duration)) {
    metaDuration.value = el.duration
  }
}

const onTime = () => {
  const el = audioRef.value
  if (el) {
    currentTime.value = el.currentTime
  }
}

const onEnded = () => {
  isPlaying.value = false
  currentTime.value = 0
  const el = audioRef.value
  if (el) {
    el.currentTime = 0
  }
}

watch(
  () => props.src,
  () => {
    isPlaying.value = false
    metaDuration.value = 0
    currentTime.value = 0
  }
)

onUnmounted(() => {
  audioRef.value?.pause()
})
</script>

<style scoped>
.voice-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 168px;
  padding: 8px 12px;
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.06);
}

.is-self .voice-bar {
  background: rgba(255, 255, 255, 0.35);
}

.play-btn {
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 50%;
  display: grid;
  place-items: center;
  cursor: pointer;
  background: rgba(34, 197, 94, 0.2);
  color: #15803d;
  flex-shrink: 0;
}

.is-self .play-btn {
  background: rgba(255, 255, 255, 0.55);
  color: #166534;
}

.play-icon {
  font-size: 11px;
  line-height: 1;
}

.wave-track {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 3px;
  height: 24px;
  cursor: pointer;
}

.wave-bar {
  width: 3px;
  height: 8px;
  border-radius: 999px;
  background: rgba(34, 197, 94, 0.55);
  transform-origin: center bottom;
}

.is-self .wave-bar {
  background: rgba(22, 101, 52, 0.65);
}

.voice-bar.playing .wave-bar {
  animation: wave-bounce 0.9s ease-in-out infinite;
}

.duration {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--text-secondary);
  flex-shrink: 0;
}

audio {
  display: none;
}

@keyframes wave-bounce {
  0%,
  100% {
    height: 8px;
  }
  50% {
    height: 18px;
  }
}
</style>
