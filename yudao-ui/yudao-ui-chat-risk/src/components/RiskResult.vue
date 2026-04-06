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

    <div v-if="behaviorInfo" class="behavior-block">
      <div class="behavior-header">
        <div>
          <p class="section-title">生物行为分析</p>
          <p class="behavior-summary">
            {{ behaviorInfo.summary || '已结合用户操作节奏、输入轨迹和设备行为完成分析。' }}
          </p>
        </div>
        <span :class="['behavior-badge', behaviorInfo.mocked ? 'is-mocked' : 'is-live']">
          {{ behaviorInfo.mocked ? '模拟画像' : '真实画像' }}
        </span>
      </div>

      <div class="behavior-score-card">
        <span class="behavior-score-label">行为加分</span>
        <strong class="behavior-score-value">+{{ behaviorExtraScore }}</strong>
      </div>

      <div v-if="behaviorInfo.factors?.length" class="behavior-factor-list">
        <span v-for="factor in behaviorInfo.factors" :key="factor" class="behavior-factor-chip">
          {{ factor }}
        </span>
      </div>

      <div v-if="behaviorMetrics.length" class="behavior-metrics">
        <div v-for="metric in behaviorMetrics" :key="metric.label" class="behavior-metric">
          <span class="behavior-metric-label">{{ metric.label }}</span>
          <strong class="behavior-metric-value">{{ metric.value }}</strong>
        </div>
      </div>

      <details v-if="behaviorInfo.notes?.length || behaviorInfo.snapshot" class="behavior-details">
        <summary>查看行为分析明细</summary>
        <div v-if="behaviorInfo.notes?.length" class="behavior-notes">
          <p class="section-title">分析备注</p>
          <ul class="behavior-note-list">
            <li v-for="note in behaviorInfo.notes" :key="note">{{ note }}</li>
          </ul>
        </div>
        <pre v-if="behaviorInfo.snapshot">{{ formatJSON(behaviorInfo.snapshot) }}</pre>
      </details>
    </div>

    <details v-if="riskData.ipInfo" class="ip-block">
      <summary>查看 IP 情报</summary>
      <pre>{{ formatJSON(riskData.ipInfo) }}</pre>
    </details>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { BehaviorInfo, PayRiskAssessRespVO, RiskLevel } from '@/types'
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
const behaviorInfo = computed<BehaviorInfo | null>(() => props.riskData.behaviorInfo || null)
const behaviorExtraScore = computed(() => Math.max(Number(behaviorInfo.value?.extraScore) || 0, 0))

const formatMetricValue = (value: unknown) => {
  if (typeof value === 'boolean') {
    return value ? '是' : '否'
  }
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return String(value)
}

const behaviorMetrics = computed(() => {
  const snapshot = behaviorInfo.value?.snapshot
  if (!snapshot || typeof snapshot !== 'object') {
    return []
  }

  const metricMap: Array<{ key: string; label: string }> = [
    { key: 'operationSpeed', label: '操作速度' },
    { key: 'cardNumberInputDurationMs', label: '敏感字段输入耗时(ms)' },
    { key: 'averageKeyIntervalMs', label: '按键平均间隔(ms)' },
    { key: 'keyIntervalStdMs', label: '按键波动标准差(ms)' },
    { key: 'mouseStraightness', label: '鼠标轨迹笔直度' },
    { key: 'pointerJumpCount', label: '指针跳点次数' },
    { key: 'mouseTrajectoryType', label: '轨迹类型' },
    { key: 'pasteDetected', label: '是否粘贴输入' },
    { key: 'emulatorDetected', label: '是否模拟器' },
    { key: 'scriptHint', label: '是否脚本提示' }
  ]

  return metricMap
    .filter((item) => item.key in snapshot)
    .map((item) => ({
      label: item.label,
      value: formatMetricValue((snapshot as Record<string, unknown>)[item.key])
    }))
})
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
.behavior-block,
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

.behavior-block {
  padding: 14px;
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(255, 247, 237, 0.85), rgba(255, 255, 255, 0.95));
  border: 1px solid rgba(251, 146, 60, 0.18);
}

.behavior-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.behavior-summary {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-secondary);
}

.behavior-badge {
  padding: 7px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.behavior-badge.is-mocked {
  background: rgba(249, 115, 22, 0.14);
  color: #c2410c;
}

.behavior-badge.is-live {
  background: rgba(34, 197, 94, 0.14);
  color: #15803d;
}

.behavior-score-card {
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.82);
}

.behavior-score-label {
  font-size: 12px;
  color: var(--text-muted);
}

.behavior-score-value {
  font-size: 24px;
  color: #c2410c;
}

.behavior-factor-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.behavior-factor-chip {
  padding: 7px 10px;
  border-radius: 999px;
  background: rgba(249, 115, 22, 0.12);
  color: #9a3412;
  font-size: 12px;
}

.behavior-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.behavior-metric {
  padding: 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.84);
  border: 1px solid rgba(148, 163, 184, 0.12);
}

.behavior-metric-label {
  display: block;
  font-size: 12px;
  color: var(--text-muted);
}

.behavior-metric-value {
  display: block;
  margin-top: 6px;
  font-size: 15px;
  color: var(--text-primary);
}

.behavior-details {
  margin-top: 14px;
}

.behavior-details summary {
  cursor: pointer;
  color: var(--text-secondary);
  font-size: 13px;
}

.behavior-notes {
  margin-top: 12px;
}

.behavior-note-list {
  margin: 8px 0 0;
  padding-left: 18px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.behavior-details pre {
  margin: 12px 0 0;
  padding: 12px;
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.06);
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.6;
  overflow-x: auto;
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

@media (max-width: 640px) {
  .behavior-metrics {
    grid-template-columns: 1fr;
  }
}
</style>
