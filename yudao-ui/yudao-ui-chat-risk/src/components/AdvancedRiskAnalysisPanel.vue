<template>
  <section v-if="analysis" class="module-card advanced-panel">
    <div class="module-head">
      <div>
        <span class="section-title">高级推演分析</span>
        <p class="module-summary">{{ analysis.universe?.summary || '从时间轴、反事实、关系宇宙和干预策略四个方向拆解风险。' }}</p>
      </div>
    </div>

    <div class="advanced-grid">
      <article class="advanced-card">
        <span class="mini-label">风险演化时间轴</span>
        <div v-if="analysis.timeline?.length" class="timeline-list">
          <div v-for="item in analysis.timeline" :key="`${item.stage}-${item.title}`" class="timeline-item">
            <strong>{{ item.title }}</strong>
            <p>{{ item.description }}</p>
            <span class="chip chip-neutral">{{ item.stage }}</span>
          </div>
        </div>
        <p v-else class="module-summary compact-summary">暂无时间轴</p>
      </article>

      <article class="advanced-card">
        <span class="mini-label">反事实推演</span>
        <div v-if="analysis.counterfactuals?.length" class="timeline-list">
          <div v-for="item in analysis.counterfactuals" :key="item.title" class="timeline-item">
            <strong>{{ item.title }}</strong>
            <p>{{ item.hypothesis }}</p>
            <span class="chip chip-safe">预测 {{ item.expectedRiskScore ?? '-' }}</span>
          </div>
        </div>
        <p v-else class="module-summary compact-summary">暂无反事实场景</p>
      </article>

      <article class="advanced-card">
        <span class="mini-label">诈骗关系宇宙</span>
        <div class="chip-list compact-top">
          <span v-for="item in analysis.universe?.repeatedIndicators || []" :key="item" class="chip chip-neutral">{{ item }}</span>
          <span v-for="item in analysis.universe?.watchList || []" :key="item" class="chip chip-warn">{{ item }}</span>
        </div>
        <ul v-if="analysis.universe?.campaignHints?.length" class="detail-list">
          <li v-for="item in analysis.universe?.campaignHints" :key="item">{{ item }}</li>
        </ul>
      </article>

      <article class="advanced-card">
        <span class="mini-label">干预策略生成</span>
        <div v-if="analysis.interventions?.length" class="timeline-list">
          <div v-for="item in analysis.interventions" :key="`${item.priority}-${item.title}`" class="timeline-item">
            <strong>{{ item.title }}</strong>
            <p>{{ item.description }}</p>
            <span class="chip chip-neutral">{{ item.priority }} / {{ item.automationLevel }}</span>
          </div>
        </div>
        <p v-else class="module-summary compact-summary">暂无干预策略</p>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { PayRiskAdvancedAnalysis } from '@/types'

interface Props {
  analysis?: PayRiskAdvancedAnalysis
}

defineProps<Props>()
</script>

<style scoped>
.advanced-panel {
  background:
    radial-gradient(circle at 0% 0%, rgba(56, 189, 248, 0.08), transparent 32%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(244, 249, 255, 0.98));
}

.advanced-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.advanced-card {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.92);
}

.timeline-list {
  display: grid;
  gap: 10px;
  margin-top: 10px;
}

.timeline-item {
  padding: 12px;
  border-radius: 14px;
  background: rgba(248, 251, 255, 0.88);
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.timeline-item strong {
  color: #12202f;
}

.timeline-item p {
  margin: 8px 0;
  color: #556d80;
  font-size: 13px;
  line-height: 1.7;
}

.compact-summary {
  margin-top: 8px;
}

@media (max-width: 768px) {
  .advanced-grid {
    grid-template-columns: 1fr;
  }
}
</style>
