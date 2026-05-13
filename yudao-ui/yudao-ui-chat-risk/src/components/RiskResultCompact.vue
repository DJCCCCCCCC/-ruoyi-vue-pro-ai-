<template>
  <div class="risk-compact">
    <div class="compact-head">
      <div class="score-block">
        <span class="score">{{ normalizedScore }}</span>
        <span class="score-suffix">/ 100</span>
      </div>
      <span :class="['level-tag', `lv-${levelKey}`]">{{ levelLabel }}</span>
    </div>

    <p class="one-liner">{{ headline }}</p>

    <div v-if="imageOcrBlock" class="block image-ocr-block">
      <span class="block-label">图片与 OCR</span>
      <p class="image-ocr-summary">{{ imageOcrBlock.summary }}</p>
      <pre v-if="imageOcrBlock.preview" class="image-ocr-preview">{{ imageOcrBlock.preview }}</pre>
    </div>

    <p v-if="scamPlain" class="scam-plain">{{ scamPlain }}</p>

    <div v-if="keyPoints.length" class="block">
      <span class="block-label">要点</span>
      <ul class="compact-list">
        <li v-for="(line, i) in keyPoints" :key="i">{{ line }}</li>
      </ul>
    </div>

    <div v-if="tips.length" class="block">
      <span class="block-label">建议</span>
      <ul class="compact-list tips">
        <li v-for="(line, i) in tips" :key="i">{{ line }}</li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { PayRiskAssessRespVO, RiskLevel } from '@/types'

const props = defineProps<{
  riskData: PayRiskAssessRespVO
}>()

const levelLabels: Record<RiskLevel, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  CRITICAL: '严重'
}

const normalizedScore = computed(() =>
  Math.min(Math.max(Number(props.riskData.riskScore) || 0, 0), 100)
)

const levelKey = computed(() => props.riskData.riskLevel.toLowerCase())

const levelLabel = computed(() => levelLabels[props.riskData.riskLevel] || props.riskData.riskLevel)

const imageOcrBlock = computed(() => {
  const n = props.riskData.embeddedImageCount
  if (n == null || n <= 0) {
    return null
  }
  const summary = props.riskData.imageOcrSummary?.trim()
  const preview = props.riskData.imageOcrTextPreview?.trim()
  if (!summary && !preview) {
    return null
  }
  return { summary: summary || '已检测到聊天中的图片。', preview: preview || '' }
})

const headline = computed(() => {
  const llm = props.riskData.llmReport
  const v = llm?.verdict?.trim()
  if (v) {
    return truncate(v, 140)
  }
  const s = llm?.summary?.trim()
  if (s) {
    return truncate(s, 140)
  }
  return truncate(props.riskData.deepAnalysis?.trim() || '暂无结论摘要', 120)
})

const keyPoints = computed(() => {
  const llm = props.riskData.llmReport
  const fromLlm = (llm?.evidence || []).map((x) => String(x).trim()).filter(Boolean)
  if (fromLlm.length) {
    return fromLlm.slice(0, 3).map((x) => truncate(x, 100))
  }
  const factors = (props.riskData.riskFactors || []).map((x) => String(x).trim()).filter(Boolean)
  return factors.slice(0, 4).map((x) => truncate(x, 80))
})

const scamPlain = computed(() => {
  const t = props.riskData.llmReport?.tailoredUserGuidance?.whyLikelyScamPlainLanguage?.trim()
  return t ? truncate(t, 180) : ''
})

const tips = computed(() => {
  const tailored = props.riskData.llmReport?.tailoredUserGuidance?.preventionTipsForThisUser
  const fromTailored = (tailored || []).map((x) => String(x).trim()).filter(Boolean)
  if (fromTailored.length) {
    return fromTailored.slice(0, 3).map((x) => truncate(x, 100))
  }
  const recs = (props.riskData.llmReport?.recommendations || [])
    .map((x) => String(x).trim())
    .filter(Boolean)
  return recs.slice(0, 2).map((x) => truncate(x, 100))
})

function truncate(s: string, max: number) {
  if (s.length <= max) {
    return s
  }
  return s.slice(0, max - 1) + '…'
}
</script>

<style scoped>
.risk-compact {
  font-size: 14px;
  color: #1e293b;
}

.compact-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.score-block {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.score {
  font-size: 32px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: #0f172a;
  line-height: 1;
}

.score-suffix {
  font-size: 13px;
  color: #64748b;
}

.level-tag {
  flex-shrink: 0;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.lv-low {
  background: #dcfce7;
  color: #166534;
}
.lv-medium {
  background: #fef9c3;
  color: #854d0e;
}
.lv-high {
  background: #ffedd5;
  color: #c2410c;
}
.lv-critical {
  background: #fee2e2;
  color: #b91c1c;
}

.one-liner {
  margin: 0 0 14px;
  line-height: 1.55;
  font-weight: 600;
  color: #334155;
}

.scam-plain {
  margin: 0 0 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(254, 243, 199, 0.65);
  border: 1px solid rgba(251, 191, 36, 0.35);
  font-size: 13px;
  line-height: 1.55;
  color: #713f12;
}

.block {
  margin-top: 10px;
}

.block-label {
  display: block;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: #94a3b8;
  text-transform: uppercase;
  margin-bottom: 6px;
}

.compact-list {
  margin: 0;
  padding-left: 1.1rem;
  line-height: 1.5;
  color: #475569;
}

.compact-list li + li {
  margin-top: 4px;
}

.compact-list.tips li {
  color: #0f766e;
}

.image-ocr-block {
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(239, 246, 255, 0.9);
  border: 1px solid rgba(59, 130, 246, 0.22);
}

.image-ocr-summary {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 1.55;
  color: #1e3a5f;
  white-space: pre-wrap;
}

.image-ocr-preview {
  margin: 0;
  max-height: 140px;
  overflow: auto;
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid rgba(148, 163, 184, 0.35);
  font-size: 12px;
  line-height: 1.5;
  color: #334155;
  white-space: pre-wrap;
}
</style>
