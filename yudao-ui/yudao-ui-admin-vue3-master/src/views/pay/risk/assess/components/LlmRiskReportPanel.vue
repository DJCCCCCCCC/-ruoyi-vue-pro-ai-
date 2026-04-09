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
import type { PayRiskLlmReport } from '@/api/pay/risk/assess'

interface Props {
  report?: PayRiskLlmReport
}

const props = defineProps<Props>()

const confidenceTagType = computed(() => {
  if (props.report?.confidence === 'HIGH') return 'danger'
  if (props.report?.confidence === 'LOW') return 'success'
  return 'warning'
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
