<template>
  <ContentWrap v-if="report" class="panel llm-panel">
    <div class="panel-head llm-head">
      <h3>AI 研判</h3>
      <el-tag :type="confidenceTagType" size="small">{{ confidenceLabel }}</el-tag>
    </div>

    <p v-if="headlineText" class="verdict-highlight">
      <span class="hl-label">结论</span>
      <span class="hl-text">{{ headlineText }}</span>
    </p>

    <div class="llm-core-grid">
      <article class="llm-card recommend-card">
        <span>建议</span>
        <ul v-if="report.recommendations?.length" class="bullet-list">
          <li v-for="item in report.recommendations.slice(0, 3)" :key="item">{{ truncate(item, 100) }}</li>
        </ul>
        <p v-else class="empty-text">—</p>
      </article>
      <article class="llm-card evidence-card">
        <span>证据</span>
        <ul v-if="report.evidence?.length" class="bullet-list">
          <li v-for="item in report.evidence.slice(0, 3)" :key="item">{{ truncate(item, 100) }}</li>
        </ul>
        <p v-else class="empty-text">—</p>
      </article>
    </div>

    <el-collapse v-if="hasExtraContent" class="llm-extra-collapse">
      <el-collapse-item v-if="report.evidence && report.evidence.length > 3" name="more-evidence">
        <template #title><span class="collapse-title">更多证据</span></template>
        <ul class="bullet-list">
          <li v-for="item in report.evidence.slice(3)" :key="'e-' + item">{{ truncate(item, 120) }}</li>
        </ul>
      </el-collapse-item>

      <el-collapse-item v-if="report.recommendations && report.recommendations.length > 3" name="more-rec">
        <template #title><span class="collapse-title">更多建议</span></template>
        <ul class="bullet-list">
          <li v-for="item in report.recommendations.slice(3)" :key="'r-' + item">{{ truncate(item, 120) }}</li>
        </ul>
      </el-collapse-item>

      <el-collapse-item v-if="hasPersona && persona" name="persona">
        <template #title><span class="collapse-title">画像 / 话术</span></template>
        <p v-if="persona.summary" class="persona-summary">{{ truncate(persona.summary, 200) }}</p>
        <div class="persona-meta">
          <div v-if="persona.claimedOrImpliedRole" class="persona-meta-item">
            <em>身份</em>
            <strong>{{ persona.claimedOrImpliedRole }}</strong>
          </div>
          <div v-if="persona.inferredArchetype" class="persona-meta-item">
            <em>原型</em>
            <strong>{{ persona.inferredArchetype }}</strong>
          </div>
        </div>
        <div v-if="persona.communicationTraits?.length" class="persona-sub">
          <ul class="bullet-list compact">
            <li v-for="item in persona.communicationTraits" :key="'t-' + item">{{ truncate(item, 80) }}</li>
          </ul>
        </div>
        <div v-if="persona.pressureAndControlSignals?.length" class="persona-sub">
          <em>施压</em>
          <ul class="bullet-list compact">
            <li v-for="item in persona.pressureAndControlSignals" :key="'p-' + item">{{ truncate(item, 80) }}</li>
          </ul>
        </div>
      </el-collapse-item>

      <el-collapse-item v-if="hasTailored && tailored" name="tailored">
        <template #title><span class="collapse-title">防诈提示</span></template>
        <p v-if="tailored.whyLikelyScamPlainLanguage" class="tailored-lead">{{ truncate(tailored.whyLikelyScamPlainLanguage, 200) }}</p>
        <ul v-if="tailored.preventionTipsForThisUser?.length" class="bullet-list compact">
          <li v-for="item in tailored.preventionTipsForThisUser" :key="'tu-' + item">{{ truncate(item, 100) }}</li>
        </ul>
      </el-collapse-item>

      <el-collapse-item v-if="report.suspiciousEntities?.length" name="entities">
        <template #title><span class="collapse-title">可疑主体</span></template>
        <div class="chip-list">
          <span v-for="item in report.suspiciousEntities" :key="item" class="chip">{{ item }}</span>
        </div>
      </el-collapse-item>
    </el-collapse>
  </ContentWrap>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type {
  PayRiskLlmPersonaProfile,
  PayRiskLlmReport,
  PayRiskLlmTailoredUserGuidance
} from '@/api/pay/risk/assess'

interface Props {
  report?: PayRiskLlmReport
}

const props = defineProps<Props>()

const truncate = (text?: string, max = 120) => {
  const s = (text || '').trim()
  if (!s) return ''
  return s.length > max ? `${s.slice(0, max)}…` : s
}

const headlineText = computed(() => {
  const r = props.report
  if (!r) return ''
  const verdict = r.verdict?.trim()
  const summary = r.summary?.trim()
  if (verdict && summary && verdict !== summary) {
    return truncate(verdict, 140)
  }
  return truncate(summary || verdict, 140)
})

const confidenceTagType = computed(() => {
  if (props.report?.confidence === 'HIGH') return 'danger'
  if (props.report?.confidence === 'LOW') return 'success'
  return 'warning'
})

const confidenceLabel = computed(() => {
  const c = props.report?.confidence
  if (c === 'HIGH') return '高'
  if (c === 'LOW') return '低'
  return '中'
})

const persona = computed(() => props.report?.personaProfile)

const hasPersona = computed(() => {
  const pr = persona.value as PayRiskLlmPersonaProfile | undefined
  if (!pr) return false
  return !!(
    pr.summary?.trim() ||
    pr.claimedOrImpliedRole?.trim() ||
    pr.inferredArchetype?.trim() ||
    (pr.communicationTraits && pr.communicationTraits.length > 0) ||
    (pr.pressureAndControlSignals && pr.pressureAndControlSignals.length > 0)
  )
})

const tailored = computed(() => props.report?.tailoredUserGuidance)

const hasTailored = computed(() => {
  const tg = tailored.value as PayRiskLlmTailoredUserGuidance | undefined
  if (!tg) return false
  return !!(
    tg.whyLikelyScamPlainLanguage?.trim() ||
    (tg.preventionTipsForThisUser && tg.preventionTipsForThisUser.length > 0)
  )
})

const hasExtraContent = computed(() => {
  const r = props.report
  if (!r) return false
  return (
    (r.evidence && r.evidence.length > 3) ||
    (r.recommendations && r.recommendations.length > 3) ||
    hasPersona.value ||
    hasTailored.value ||
    (r.suspiciousEntities && r.suspiciousEntities.length > 0)
  )
})
</script>

<style scoped lang="scss">
.llm-panel {
  border-color: rgba(99, 102, 241, 0.35);
}

.llm-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.llm-head h3 {
  margin: 0;
  font-size: 17px;
  color: #1a3d64;
}

.verdict-highlight {
  display: block;
  padding: 12px 14px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(14, 165, 233, 0.12), rgba(255, 255, 255, 0.92));
  border: 1px solid rgba(14, 165, 233, 0.25);
  margin-bottom: 12px;
}

.hl-label {
  display: block;
  font-size: 11px;
  font-weight: 700;
  color: #0369a1;
  letter-spacing: 0.06em;
  margin-bottom: 6px;
}

.hl-text {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
  line-height: 1.55;
}

.llm-core-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.llm-card {
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  background: rgba(255, 255, 255, 0.88);
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}

.llm-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.08);
}

.llm-card > span {
  display: block;
  font-size: 11px;
  font-weight: 700;
  color: #64748b;
}

.recommend-card {
  border-color: rgba(34, 197, 94, 0.35);
  background: linear-gradient(180deg, rgba(240, 253, 244, 0.85), rgba(255, 255, 255, 0.92));
}

.evidence-card {
  border-color: rgba(249, 115, 22, 0.35);
  background: linear-gradient(180deg, rgba(255, 247, 237, 0.85), rgba(255, 255, 255, 0.92));
}

.bullet-list {
  margin: 6px 0 0;
  padding-left: 16px;
  color: #334155;
  font-size: 12px;
  line-height: 1.5;
}

.bullet-list.compact {
  margin-top: 4px;
}

.empty-text {
  margin: 6px 0 0;
  color: #94a3b8;
  font-size: 12px;
}

.llm-extra-collapse {
  margin-top: 8px;
  border: none;
  background: transparent;
}

.llm-extra-collapse :deep(.el-collapse-item__header) {
  border: none;
  background: rgba(248, 250, 252, 0.8);
  border-radius: 8px;
  padding: 0 10px;
  height: 36px;
  font-size: 12px;
}

.llm-extra-collapse :deep(.el-collapse-item__wrap) {
  border: none;
  background: transparent;
}

.collapse-title {
  font-weight: 600;
  color: #334155;
}

.persona-summary {
  margin: 0 0 8px;
  color: #334155;
  font-size: 13px;
  line-height: 1.5;
}

.persona-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}

.persona-meta-item em,
.persona-sub em {
  display: block;
  font-style: normal;
  font-size: 11px;
  font-weight: 700;
  color: #64748b;
  margin-bottom: 2px;
}

.persona-sub {
  margin-top: 6px;
}

.tailored-lead {
  margin: 0;
  color: #1e293b;
  font-size: 13px;
  line-height: 1.5;
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.chip {
  padding: 4px 8px;
  border-radius: 999px;
  background: rgba(226, 232, 240, 0.8);
  color: #334155;
  font-size: 12px;
}

@media (max-width: 768px) {
  .llm-core-grid {
    grid-template-columns: 1fr;
  }
}
</style>
