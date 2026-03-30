<template>
  <div class="risk-result">
    <div class="risk-header">
      <div>
        <span class="eyebrow">风险评分</span>
        <div class="score-row">
          <strong class="score-value">{{ normalizedScore }}</strong>
          <span class="score-unit">/ 100</span>
        </div>
      </div>
      <span :class="['risk-badge', `level-${levelKey}`]">{{ levelMeta.label }}</span>
    </div>

    <div class="score-track" aria-hidden="true">
      <div class="score-bar" :class="`level-${levelKey}`" :style="{ width: `${normalizedScore}%` }"></div>
    </div>

    <div class="analysis-block">
      <p class="analysis-title">{{ levelMeta.title }}</p>
      <p class="analysis-text">{{ riskData.deepAnalysis }}</p>
    </div>

    <div v-if="riskData.riskFactors?.length" class="factor-block">
      <p class="section-title">触发因子</p>
      <div class="factor-list">
        <span v-for="factor in riskData.riskFactors" :key="factor" class="factor-chip">
          {{ factor }}
        </span>
      </div>
    </div>

    <details v-if="riskData.ipInfo" class="ip-block">
      <summary>查看 IP 情报</summary>
      <pre>{{ formatJSON(riskData.ipInfo) }}</pre>
    </details>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { PayRiskAssessRespVO, RiskLevel } from '@/types'
import { formatJSON } from '@/utils/parser'

interface Props {
  riskData: PayRiskAssessRespVO
}

const props = defineProps<Props>()

const metaMap: Record<RiskLevel, { label: string; title: string }> = {
  LOW: {
    label: '低风险',
    title: '本次支付表现稳定，建议正常放行。'
  },
  MEDIUM: {
    label: '中风险',
    title: '存在异常信号，建议二次校验。'
  },
  HIGH: {
    label: '高风险',
    title: '风险较高，建议人工复核或追加验证。'
  },
  CRITICAL: {
    label: '严重风险',
    title: '多项高危规则命中，建议立即拦截。'
  }
}

const normalizedScore = computed(() => Math.min(Math.max(Number(props.riskData.riskScore) || 0, 0), 100))
const levelKey = computed(() => props.riskData.riskLevel.toLowerCase())
const levelMeta = computed(() => metaMap[props.riskData.riskLevel] || metaMap.MEDIUM)
</script>

<style scoped>
.risk-result {
  width: min(100%, 460px);
  padding: 16px;
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.98));
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.risk-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.eyebrow,
.section-title {
  font-size: 12px;
  color: var(--text-muted);
}

.score-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: 4px;
}

.score-value {
  font-size: 34px;
  line-height: 1;
  color: var(--text-primary);
}

.score-unit {
  font-size: 13px;
  color: var(--text-muted);
}

.risk-badge {
  padding: 8px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.score-track {
  margin-top: 14px;
  height: 10px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.16);
  overflow: hidden;
}

.score-bar {
  height: 100%;
  border-radius: inherit;
  transition: width 0.28s ease;
}

.analysis-block,
.factor-block,
.ip-block {
  margin-top: 16px;
}

.analysis-title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
}

.analysis-text {
  margin: 0;
  line-height: 1.75;
  color: var(--text-secondary);
  font-size: 14px;
}

.factor-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.factor-chip {
  padding: 7px 10px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.06);
  color: var(--text-secondary);
  font-size: 12px;
}

.ip-block summary {
  cursor: pointer;
  color: var(--text-secondary);
  font-size: 13px;
}

.ip-block pre {
  margin: 10px 0 0;
  padding: 12px;
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.06);
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.6;
  overflow-x: auto;
}

.level-low {
  background: rgba(34, 197, 94, 0.14);
  color: #15803d;
}

.score-bar.level-low {
  background: linear-gradient(90deg, #22c55e, #16a34a);
}

.level-medium {
  background: rgba(249, 115, 22, 0.14);
  color: #c2410c;
}

.score-bar.level-medium {
  background: linear-gradient(90deg, #fb923c, #f97316);
}

.level-high {
  background: rgba(239, 68, 68, 0.14);
  color: #dc2626;
}

.score-bar.level-high {
  background: linear-gradient(90deg, #f87171, #ef4444);
}

.level-critical {
  background: rgba(127, 29, 29, 0.14);
  color: #991b1b;
}

.score-bar.level-critical {
  background: linear-gradient(90deg, #dc2626, #7f1d1d);
}
</style>
