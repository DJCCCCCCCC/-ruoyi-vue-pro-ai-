<template>
  <ContentWrap v-if="reflection" class="panel reflection-panel">
    <div class="panel-head reflection-head">
      <h3 class="section-title-only">Agent 校验</h3>
      <el-tag v-if="reflection.arbiter?.disputeLevel" size="small" :type="disputeTagType(reflection.arbiter.disputeLevel)">
        争议 {{ reflection.arbiter.disputeLevel }}
      </el-tag>
    </div>

    <div class="reflection-grid">
      <article class="agent-card assessor-card">
        <div class="agent-head">
          <span>判定</span>
          <strong>{{ reflection.assessor?.preliminaryScore ?? '-' }}/100</strong>
        </div>
        <p class="agent-meta">
          {{ reflection.assessor?.riskLevel || '-' }} · {{ reflection.assessor?.recommendation || '-' }} · 置信度 {{ percent(reflection.assessor?.confidence) }}
        </p>
        <div v-if="reflection.assessor?.coreClaims?.length" class="item-list">
          <div v-for="item in reflection.assessor.coreClaims" :key="item.claim" class="mini-item">
            <strong>{{ item.claim }}</strong>
            <p>{{ item.reasoning }}</p>
            <small>证据：{{ item.evidenceIds?.join(', ') || '—' }} · 置信度 {{ percent(item.confidence) }}</small>
          </div>
        </div>
        <p v-else class="empty-text">暂无初判主张</p>
      </article>

      <article class="agent-card skeptic-card">
        <div class="agent-head">
          <span>质疑</span>
          <strong>{{ percent(reflection.skeptic?.overallChallengeStrength) }}</strong>
        </div>
        <p class="agent-meta">建议修正分：{{ reflection.skeptic?.revisedScoreSuggestion ?? '-' }}</p>
        <div v-if="reflection.skeptic?.issues?.length" class="item-list">
          <div v-for="item in reflection.skeptic.issues" :key="`${item.issueType}-${item.description}`" class="mini-item issue-item">
            <div class="issue-head">
              <strong>{{ item.issueType || 'ISSUE' }}</strong>
              <el-tag size="small" :type="severityTagType(item.severity)">{{ item.severity || '-' }}</el-tag>
            </div>
            <p>{{ item.description }}</p>
            <small>针对：{{ item.targetClaim || '—' }}</small>
          </div>
        </div>
        <p v-else class="empty-text">暂无质疑问题</p>
      </article>

      <article class="agent-card arbiter-card">
        <div class="agent-head">
          <span>仲裁</span>
          <strong>{{ reflection.arbiter?.finalScore ?? '-' }}/100</strong>
        </div>
        <p class="agent-meta">
          {{ reflection.arbiter?.finalRiskLevel || '-' }} · {{ reflection.arbiter?.finalDecision || '-' }} · 置信度 {{ percent(reflection.arbiter?.confidence) }}
        </p>
        <p class="arbiter-summary">{{ reflection.arbiter?.summary || '—' }}</p>
        <div v-if="reflection.arbiter?.manualReviewFocus?.length" class="focus-box">
          <span>复核要点</span>
          <ul>
            <li v-for="item in reflection.arbiter.manualReviewFocus" :key="item">{{ item }}</li>
          </ul>
        </div>
      </article>
    </div>

    <div class="arbitration-grid">
      <article class="module-card">
        <h4>采纳观点</h4>
        <div v-if="reflection.arbiter?.adoptedPoints?.length" class="point-list">
          <div v-for="item in reflection.arbiter.adoptedPoints" :key="`${item.from}-${item.point}`" class="point-item">
            <el-tag size="small" type="success">{{ item.from }}</el-tag>
            <strong>{{ item.point }}</strong>
            <p>{{ item.reason }}</p>
          </div>
        </div>
        <p v-else class="empty-text">暂无采纳观点</p>
      </article>
      <article class="module-card">
        <h4>驳回观点</h4>
        <div v-if="reflection.arbiter?.rejectedPoints?.length" class="point-list">
          <div v-for="item in reflection.arbiter.rejectedPoints" :key="`${item.from}-${item.point}`" class="point-item">
            <el-tag size="small" type="warning">{{ item.from }}</el-tag>
            <strong>{{ item.point }}</strong>
            <p>{{ item.reason }}</p>
          </div>
        </div>
        <p v-else class="empty-text">暂无驳回观点</p>
      </article>
    </div>
  </ContentWrap>
</template>

<script setup lang="ts">
import type { PayRiskAgentReflection } from '@/api/pay/risk/assess'

interface Props {
  reflection?: PayRiskAgentReflection
}

defineProps<Props>()

const percent = (value?: number) => {
  if (value === undefined || value === null || Number.isNaN(Number(value))) return '-'
  return `${Math.round(Number(value) * 100)}%`
}

const disputeTagType = (level?: string) => {
  const upper = (level || '').toUpperCase()
  if (upper === 'HIGH') return 'danger'
  if (upper === 'MEDIUM') return 'warning'
  return 'success'
}

const severityTagType = (level?: string) => {
  const upper = (level || '').toUpperCase()
  if (upper === 'HIGH') return 'danger'
  if (upper === 'MEDIUM') return 'warning'
  return 'info'
}
</script>

<style scoped>
.reflection-panel {
  background:
    radial-gradient(circle at 100% 0%, rgba(168, 85, 247, 0.1), transparent 30%),
    linear-gradient(180deg, #ffffff 0%, #fbf8ff 100%);
}

.section-title-only {
  margin: 0 0 10px;
  font-size: 16px;
  font-weight: 700;
  color: #12202f;
}

.reflection-head {
  margin-bottom: 10px;
}

.panel-kicker {
  margin: 0;
  color: #7c3aed;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.panel-head h3 {
  margin: 4px 0 0;
  color: #141827;
  font-size: 20px;
}

.panel-desc,
.agent-meta,
.empty-text,
.mini-item p,
.point-item p,
.arbiter-summary {
  color: #5d667a;
  font-size: 13px;
  line-height: 1.7;
}

.reflection-grid,
.arbitration-grid {
  display: grid;
  gap: 14px;
  margin-top: 16px;
}

.reflection-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.arbitration-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.agent-card,
.module-card {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.92);
}

.agent-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.agent-head span,
.focus-box span {
  color: #6d28d9;
  font-size: 13px;
  font-weight: 800;
}

.agent-head strong {
  color: #111827;
  font-size: 22px;
}

.item-list,
.point-list {
  display: grid;
  gap: 10px;
  margin-top: 12px;
}

.mini-item,
.point-item,
.focus-box {
  padding: 12px;
  border-radius: 14px;
  background: rgba(248, 250, 252, 0.9);
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.issue-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.mini-item strong,
.point-item strong,
.module-card h4 {
  color: #111827;
}

.mini-item small {
  color: #7a8699;
  font-size: 12px;
  font-weight: 700;
}

.focus-box {
  margin-top: 12px;
  background: rgba(254, 243, 199, 0.55);
}

.focus-box ul {
  margin: 8px 0 0;
  padding-left: 18px;
  color: #6b4e16;
  font-size: 13px;
  line-height: 1.7;
}

.point-item {
  display: grid;
  gap: 8px;
}

.point-item .el-tag {
  width: fit-content;
}

@media (max-width: 1100px) {
  .reflection-grid,
  .arbitration-grid {
    grid-template-columns: 1fr;
  }
}
</style>
