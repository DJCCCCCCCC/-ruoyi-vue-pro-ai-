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
import type { PayRiskLlmReport } from '@/types'

interface Props {
  report?: PayRiskLlmReport
}

const props = defineProps<Props>()

const confidenceToneClass = computed(() => {
  if (props.report?.confidence === 'HIGH') return 'chip-warn'
  if (props.report?.confidence === 'LOW') return 'chip-safe'
  return 'chip-neutral'
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
