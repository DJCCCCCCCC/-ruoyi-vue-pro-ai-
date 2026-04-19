<template>
  <ContentWrap v-if="report" class="panel llm-panel">
    <div class="panel-head">
      <div>
        <p class="panel-kicker">LLM Analysis</p>
        <h3>模型综合研判</h3>
        <p class="panel-desc">{{ report.summary || '模型正在结合风险评分、拓扑、行为和情报结果做综合判断。' }}</p>
      </div>
      <div class="badge-group">
        <span class="mode-badge">{{ report.mode || 'LLM' }}</span>
        <el-tag :type="confidenceTagType">{{ report.confidence || 'MEDIUM' }}</el-tag>
      </div>
    </div>

    <div class="panel-grid">
      <article v-if="hasPersona && persona" class="llm-card persona-card">
        <span>人物画像分析</span>
        <p v-if="persona.summary" class="persona-summary">{{ persona.summary }}</p>
        <div class="persona-meta">
          <div v-if="persona.claimedOrImpliedRole" class="persona-meta-item">
            <em>呈现/暗示身份</em>
            <strong>{{ persona.claimedOrImpliedRole }}</strong>
          </div>
          <div v-if="persona.inferredArchetype" class="persona-meta-item">
            <em>原型归纳</em>
            <strong>{{ persona.inferredArchetype }}</strong>
          </div>
        </div>
        <div v-if="persona.communicationTraits?.length" class="persona-sub">
          <em>话术与互动</em>
          <ul class="bullet-list">
            <li v-for="item in persona.communicationTraits" :key="'t-' + item">{{ item }}</li>
          </ul>
        </div>
        <div v-if="persona.pressureAndControlSignals?.length" class="persona-sub">
          <em>施压与诱导信号</em>
          <ul class="bullet-list">
            <li v-for="item in persona.pressureAndControlSignals" :key="'p-' + item">{{ item }}</li>
          </ul>
        </div>
      </article>
      <article v-if="hasTailored && tailored" class="llm-card tailored-card">
        <span>为您说明（结合本人情况）</span>
        <p v-if="tailored.whyLikelyScamPlainLanguage" class="tailored-lead">{{ tailored.whyLikelyScamPlainLanguage }}</p>
        <p v-if="tailored.reassuranceLine" class="tailored-reassure">{{ tailored.reassuranceLine }}</p>
        <div v-if="tailored.preventionTipsForThisUser?.length" class="persona-sub">
          <em>个性化防范</em>
          <ul class="bullet-list">
            <li v-for="item in tailored.preventionTipsForThisUser" :key="'tu-' + item">{{ item }}</li>
          </ul>
        </div>
      </article>
      <article class="llm-card verdict-card">
        <span>结论</span>
        <strong>{{ report.verdict || '暂无模型结论' }}</strong>
      </article>
      <article class="llm-card">
        <span>关键证据</span>
        <ul v-if="report.evidence?.length" class="bullet-list">
          <li v-for="item in report.evidence" :key="item">{{ item }}</li>
        </ul>
        <p v-else class="empty-text">暂无证据摘要</p>
      </article>
      <article class="llm-card">
        <span>可疑主体</span>
        <div v-if="report.suspiciousEntities?.length" class="chip-list">
          <span v-for="item in report.suspiciousEntities" :key="item" class="chip">{{ item }}</span>
        </div>
        <p v-else class="empty-text">暂无主体归因</p>
      </article>
      <article class="llm-card">
        <span>建议动作</span>
        <ul v-if="report.recommendations?.length" class="bullet-list">
          <li v-for="item in report.recommendations" :key="item">{{ item }}</li>
        </ul>
        <p v-else class="empty-text">暂无处置建议</p>
      </article>
    </div>
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

const confidenceTagType = computed(() => {
  if (props.report?.confidence === 'HIGH') return 'danger'
  if (props.report?.confidence === 'LOW') return 'success'
  return 'warning'
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
  overflow: hidden;
  background:
    radial-gradient(circle at 0% 0%, rgba(14, 165, 233, 0.12), transparent 32%),
    linear-gradient(180deg, #ffffff 0%, #f5f9ff 100%);
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.panel-kicker {
  margin: 0;
  color: #6d8599;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.panel-head h3 {
  margin: 4px 0 0;
  color: #12202f;
  font-size: 20px;
}

.panel-desc,
.empty-text,
.bullet-list {
  margin: 8px 0 0;
  color: #556d80;
  font-size: 13px;
  line-height: 1.7;
}

.badge-group {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.mode-badge {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(14, 165, 233, 0.12);
  color: #0369a1;
  font-size: 12px;
  font-weight: 700;
}

.panel-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.llm-card {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.9);
}

.llm-card > span {
  display: block;
  color: #6d8599;
  font-size: 12px;
  font-weight: 700;
}

.llm-card > strong {
  display: block;
  margin-top: 8px;
  color: #12202f;
  font-size: 16px;
  line-height: 1.6;
}

.persona-card,
.tailored-card {
  grid-column: 1 / -1;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.08), rgba(255, 255, 255, 0.95));
  border-color: rgba(99, 102, 241, 0.22);
}

.tailored-card {
  background: linear-gradient(135deg, rgba(14, 165, 233, 0.07), rgba(254, 243, 199, 0.35));
  border-color: rgba(14, 165, 233, 0.2);
}

.tailored-lead {
  margin: 8px 0 0;
  color: #1e293b;
  font-size: 14px;
  line-height: 1.65;
}

.tailored-reassure {
  margin: 10px 0 0;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(254, 249, 195, 0.55);
  color: #713f12;
  font-size: 13px;
  line-height: 1.55;
}

.persona-summary {
  margin: 8px 0 0;
  color: #334155;
  font-size: 14px;
  line-height: 1.65;
  font-weight: 600;
}

.persona-meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.persona-meta-item em,
.persona-sub em {
  display: block;
  font-style: normal;
  font-size: 11px;
  font-weight: 700;
  color: #64748b;
  letter-spacing: 0.04em;
  margin-bottom: 4px;
}

.persona-meta-item strong {
  display: block;
  font-size: 13px;
  color: #1e293b;
  line-height: 1.5;
}

.persona-sub {
  margin-top: 12px;
}

.verdict-card {
  background: linear-gradient(135deg, rgba(14, 165, 233, 0.08), rgba(15, 23, 42, 0.02));
}

.bullet-list {
  padding-left: 18px;
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.chip {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(226, 232, 240, 0.6);
  color: #294256;
  font-size: 12px;
  font-weight: 600;
}

@media (max-width: 768px) {
  .panel-head,
  .panel-grid {
    grid-template-columns: 1fr;
    flex-direction: column;
  }
}
</style>
