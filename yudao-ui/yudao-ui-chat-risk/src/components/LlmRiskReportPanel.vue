<template>
  <section v-if="report" class="module-card llm-panel">
    <div class="module-head">
      <div>
        <span class="section-title">LLM 综合研判</span>
        <p class="module-summary">{{ report.summary || '模型正在结合多维风控上下文生成综合结论。' }}</p>
      </div>
      <div class="badge-group">
        <span class="chip chip-neutral">{{ report.mode || 'LLM' }}</span>
        <span :class="['chip', confidenceToneClass]">{{ report.confidence || 'MEDIUM' }}</span>
      </div>
    </div>

    <div class="llm-grid">
      <article v-if="hasPersona && persona" class="llm-card persona-card">
        <span class="mini-label">人物画像</span>
        <p v-if="persona.summary" class="persona-lead">{{ persona.summary }}</p>
        <div class="persona-row">
          <div v-if="persona.claimedOrImpliedRole" class="persona-bit">
            <span class="persona-bit-label">呈现/暗示身份</span>
            <span class="persona-bit-value">{{ persona.claimedOrImpliedRole }}</span>
          </div>
          <div v-if="persona.inferredArchetype" class="persona-bit">
            <span class="persona-bit-label">原型归纳</span>
            <span class="persona-bit-value">{{ persona.inferredArchetype }}</span>
          </div>
        </div>
        <ul v-if="persona.communicationTraits?.length" class="detail-list persona-list">
          <li v-for="item in persona.communicationTraits" :key="'ct-' + item"><strong>话术</strong> {{ item }}</li>
        </ul>
        <ul v-if="persona.pressureAndControlSignals?.length" class="detail-list persona-list">
          <li v-for="item in persona.pressureAndControlSignals" :key="'ps-' + item"><strong>施压</strong> {{ item }}</li>
        </ul>
      </article>
      <article v-if="hasTailored && tailored" class="llm-card tailored-card">
        <span class="mini-label">为您说明（结合本人情况）</span>
        <p v-if="tailored.whyLikelyScamPlainLanguage" class="tailored-lead">{{ tailored.whyLikelyScamPlainLanguage }}</p>
        <p v-if="tailored.reassuranceLine" class="tailored-reassure">{{ tailored.reassuranceLine }}</p>
        <ul v-if="tailored.preventionTipsForThisUser?.length" class="detail-list tailored-list">
          <li v-for="item in tailored.preventionTipsForThisUser" :key="'tu-' + item">
            <strong>建议</strong> {{ item }}
          </li>
        </ul>
      </article>
      <article class="llm-card llm-card-accent">
        <span class="mini-label">结论</span>
        <strong class="mini-value">{{ report.verdict || '暂无模型结论' }}</strong>
      </article>
      <article class="llm-card">
        <span class="mini-label">关键证据</span>
        <ul v-if="report.evidence?.length" class="detail-list">
          <li v-for="item in report.evidence" :key="item">{{ item }}</li>
        </ul>
        <p v-else class="module-summary compact-summary">暂无证据摘要</p>
      </article>
      <article class="llm-card">
        <span class="mini-label">可疑主体</span>
        <div v-if="report.suspiciousEntities?.length" class="chip-list compact-top">
          <span v-for="item in report.suspiciousEntities" :key="item" class="chip chip-neutral">{{ item }}</span>
        </div>
        <p v-else class="module-summary compact-summary">暂无主体归因</p>
      </article>
      <article class="llm-card">
        <span class="mini-label">建议动作</span>
        <ul v-if="report.recommendations?.length" class="detail-list">
          <li v-for="item in report.recommendations" :key="item">{{ item }}</li>
        </ul>
        <p v-else class="module-summary compact-summary">暂无处置建议</p>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { PayRiskLlmPersonaProfile, PayRiskLlmReport, PayRiskLlmTailoredUserGuidance } from '@/types'

interface Props {
  report?: PayRiskLlmReport
}

const props = defineProps<Props>()

const confidenceToneClass = computed(() => {
  if (props.report?.confidence === 'HIGH') return 'chip-warn'
  if (props.report?.confidence === 'LOW') return 'chip-safe'
  return 'chip-neutral'
})

const persona = computed(() => props.report?.personaProfile)

const hasPersona = computed(() => {
  const pr = persona.value as PayRiskLlmPersonaProfile | undefined
  if (!pr) {
    return false
  }
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
  const t = tailored.value as PayRiskLlmTailoredUserGuidance | undefined
  if (!t) {
    return false
  }
  return !!(
    t.whyLikelyScamPlainLanguage?.trim() ||
    t.reassuranceLine?.trim() ||
    (t.preventionTipsForThisUser && t.preventionTipsForThisUser.length > 0)
  )
})
</script>

<style scoped>
.llm-panel {
  background:
    radial-gradient(circle at 0% 0%, rgba(14, 165, 233, 0.1), transparent 32%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(244, 249, 255, 0.98));
}

.badge-group {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.llm-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.llm-card {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.92);
}

.persona-card,
.tailored-card {
  grid-column: 1 / -1;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.09), rgba(255, 255, 255, 0.95));
  border-color: rgba(99, 102, 241, 0.2);
}

.tailored-card {
  background: linear-gradient(135deg, rgba(14, 165, 233, 0.08), rgba(254, 243, 199, 0.4));
  border-color: rgba(14, 165, 233, 0.22);
}

.tailored-lead {
  margin: 8px 0 0;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  line-height: 1.55;
}

.tailored-reassure {
  margin: 10px 0 0;
  padding: 8px 10px;
  border-radius: 12px;
  background: rgba(254, 249, 195, 0.65);
  color: #713f12;
  font-size: 13px;
  line-height: 1.5;
}

.tailored-list {
  margin-top: 10px;
}

.tailored-list li strong {
  margin-right: 6px;
  color: #0369a1;
}

.persona-lead {
  margin: 8px 0 0;
  font-size: 14px;
  font-weight: 600;
  color: #334155;
  line-height: 1.55;
}

.persona-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px;
  margin-top: 10px;
}

.persona-bit {
  padding: 8px 10px;
  border-radius: 12px;
  background: rgba(248, 250, 252, 0.95);
  border: 1px solid rgba(148, 163, 184, 0.2);
}

.persona-bit-label {
  display: block;
  font-size: 11px;
  color: #64748b;
  font-weight: 700;
  margin-bottom: 4px;
}

.persona-bit-value {
  font-size: 13px;
  color: #0f172a;
  line-height: 1.45;
}

.persona-list {
  margin-top: 10px;
}

.persona-list li strong {
  margin-right: 6px;
  color: #4f46e5;
}

.llm-card-accent {
  background: linear-gradient(135deg, rgba(14, 165, 233, 0.08), rgba(15, 23, 42, 0.02));
}

.compact-summary {
  margin-top: 8px;
}

.detail-list {
  margin: 10px 0 0;
  padding-left: 18px;
}

@media (max-width: 768px) {
  .llm-grid {
    grid-template-columns: 1fr;
  }
}
</style>
