<template>
  <ContentWrap v-if="analysis" class="panel advanced-panel">
    <div class="panel-head">
      <div>
        <p class="panel-kicker">Advanced Analysis</p>
        <h3>风险演化与干预推演</h3>
        <p class="panel-desc">{{ analysis.universe?.summary || '从风险时间轴、反事实场景、关系宇宙和干预策略四个方向做深入推演。' }}</p>
      </div>
    </div>

    <div class="section-grid">
      <article class="module-card">
        <h4>风险演化时间轴</h4>
        <div v-if="analysis.timeline?.length" class="timeline-list">
          <div v-for="item in analysis.timeline" :key="`${item.stage}-${item.title}`" class="timeline-item">
            <span class="timeline-stage">{{ item.stage }}</span>
            <div>
              <strong>{{ item.title }}</strong>
              <p>{{ item.description }}</p>
            </div>
            <span class="timeline-delta">+{{ item.riskDelta || 0 }}</span>
          </div>
        </div>
        <p v-else class="empty-text">暂无时间轴数据</p>
      </article>

      <article class="module-card">
        <h4>反事实推演</h4>
        <div v-if="analysis.counterfactuals?.length" class="card-list">
          <div v-for="item in analysis.counterfactuals" :key="item.title" class="mini-card">
            <strong>{{ item.title }}</strong>
            <p>{{ item.hypothesis }}</p>
            <span>预测分数：{{ item.expectedRiskScore ?? '-' }}</span>
            <small>{{ item.reason }}</small>
          </div>
        </div>
        <p v-else class="empty-text">暂无反事实场景</p>
      </article>

      <article class="module-card">
        <h4>诈骗关系宇宙</h4>
        <div class="universe-block">
          <div>
            <span class="block-title">重复指示器</span>
            <div class="chip-list">
              <span v-for="item in analysis.universe?.repeatedIndicators || []" :key="item" class="chip">{{ item }}</span>
            </div>
          </div>
          <div>
            <span class="block-title">观察名单</span>
            <div class="chip-list">
              <span v-for="item in analysis.universe?.watchList || []" :key="item" class="chip chip-warn">{{ item }}</span>
            </div>
          </div>
          <div>
            <span class="block-title">团伙线索</span>
            <ul class="bullet-list">
              <li v-for="item in analysis.universe?.campaignHints || []" :key="item">{{ item }}</li>
            </ul>
          </div>
        </div>
      </article>

      <article class="module-card">
        <h4>干预策略生成</h4>
        <div v-if="analysis.interventions?.length" class="card-list">
          <div v-for="item in analysis.interventions" :key="`${item.priority}-${item.title}`" class="mini-card intervention-card">
            <div class="intervention-head">
              <strong>{{ item.title }}</strong>
              <span>{{ item.priority }}</span>
            </div>
            <p>{{ item.description }}</p>
            <small>{{ item.type }} / {{ item.automationLevel }}</small>
          </div>
        </div>
        <p v-else class="empty-text">暂无干预策略</p>
      </article>
    </div>
  </ContentWrap>
</template>

<script setup lang="ts">
import type { PayRiskAdvancedAnalysis } from '@/api/pay/risk/assess'

interface Props {
  analysis?: PayRiskAdvancedAnalysis
}

defineProps<Props>()
</script>

<style scoped>
.advanced-panel {
  background:
    radial-gradient(circle at 0% 0%, rgba(56, 189, 248, 0.1), transparent 30%),
    linear-gradient(180deg, #ffffff 0%, #f7fbff 100%);
}

.panel-head h3 {
  margin: 4px 0 0;
  color: #12202f;
  font-size: 20px;
}

.panel-kicker {
  margin: 0;
  color: #6d8599;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.panel-desc,
.empty-text,
.bullet-list,
.mini-card p,
.timeline-item p {
  color: #556d80;
  font-size: 13px;
  line-height: 1.7;
}

.section-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.module-card {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.92);
}

.module-card h4 {
  margin: 0;
  color: #12202f;
  font-size: 16px;
}

.timeline-list,
.card-list {
  display: grid;
  gap: 10px;
  margin-top: 12px;
}

.timeline-item,
.mini-card {
  padding: 12px;
  border-radius: 14px;
  background: rgba(248, 251, 255, 0.92);
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.timeline-item {
  display: grid;
  grid-template-columns: 88px 1fr 56px;
  gap: 12px;
  align-items: start;
}

.timeline-stage,
.timeline-delta,
.block-title,
.intervention-head span,
.mini-card span,
.mini-card small {
  font-size: 12px;
  font-weight: 700;
}

.timeline-stage,
.block-title {
  color: #0369a1;
}

.timeline-delta,
.intervention-head span {
  color: #b91c1c;
}

.timeline-item strong,
.mini-card strong {
  color: #12202f;
}

.universe-block {
  display: grid;
  gap: 14px;
  margin-top: 12px;
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.chip {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(226, 232, 240, 0.6);
  color: #294256;
  font-size: 12px;
  font-weight: 600;
}

.chip-warn {
  background: rgba(248, 113, 113, 0.14);
  color: #b91c1c;
}

.bullet-list {
  margin: 8px 0 0;
  padding-left: 18px;
}

.intervention-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

@media (max-width: 768px) {
  .section-grid {
    grid-template-columns: 1fr;
  }

  .timeline-item {
    grid-template-columns: 1fr;
  }
}
</style>
