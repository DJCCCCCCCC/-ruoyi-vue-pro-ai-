<template>
  <section v-if="report" class="police-panel">
    <div class="panel-head">
      <div>
        <span class="panel-eyebrow">面向民警的案情摘要</span>
        <h4>{{ report.reportTitle || '电信网络诈骗报案协助摘要' }}</h4>
        <p class="panel-summary">{{ report.caseSummary }}</p>
      </div>
      <div class="badge-group">
        <span class="chip chip-mode">{{ report.mode || 'LLM' }}</span>
        <span :class="['chip', urgencyClass]">{{ urgencyLabel }}</span>
      </div>
    </div>

    <div class="meta-row">
      <span v-if="report.fraudType" class="meta-tag">类型：{{ report.fraudType }}</span>
      <span v-if="generatedAt" class="meta-tag">生成：{{ formatTime(generatedAt) }}</span>
    </div>

    <article v-if="report.fraudModusOperandi" class="panel-block">
      <span class="block-label">作案手法</span>
      <p>{{ report.fraudModusOperandi }}</p>
    </article>

    <article v-if="report.printableStatement" class="panel-block statement-block">
      <div class="block-head">
        <span class="block-label">口述稿（可向民警陈述）</span>
        <button type="button" class="mini-btn" @click="copyStatement">复制全文</button>
      </div>
      <p class="statement-text">{{ report.printableStatement }}</p>
    </article>

    <article v-if="report.timeline?.length" class="panel-block">
      <span class="block-label">事件时间线</span>
      <ul class="timeline">
        <li v-for="(item, i) in report.timeline" :key="i">
          <span class="tl-time">{{ item.time || '待核实' }}</span>
          <span class="tl-phase">{{ item.phase }}</span>
          <p>{{ item.description }}</p>
        </li>
      </ul>
    </article>

    <article v-if="hasTransferSummary" class="panel-block">
      <span class="block-label">转账 / 损失</span>
      <ul v-if="report.transferSummary?.amounts?.length" class="detail-list">
        <li v-for="(a, i) in report.transferSummary!.amounts" :key="'a-' + i">{{ a }}</li>
      </ul>
      <ul v-if="report.transferSummary?.channels?.length" class="detail-list">
        <li v-for="(c, i) in report.transferSummary!.channels" :key="'c-' + i"><strong>渠道</strong> {{ c }}</li>
      </ul>
      <ul v-if="report.transferSummary?.payeeAccounts?.length" class="detail-list">
        <li v-for="(p, i) in report.transferSummary!.payeeAccounts" :key="'p-' + i"><strong>收款方</strong> {{ p }}</li>
      </ul>
      <p v-if="report.transferSummary?.totalLossEstimate" class="loss-estimate">
        估计损失：{{ report.transferSummary.totalLossEstimate }}
      </p>
    </article>

    <article v-if="report.suspectClues?.length" class="panel-block">
      <span class="block-label">嫌疑人 / 对端线索</span>
      <ul class="clue-list">
        <li v-for="(clue, i) in report.suspectClues" :key="i">
          <span class="clue-cat">{{ categoryLabel(clue.category) }}</span>
          <strong>{{ clue.value }}</strong>
          <span v-if="clue.note" class="clue-note">{{ clue.note }}</span>
        </li>
      </ul>
    </article>

    <article v-if="report.fundFlowAnalysis" class="panel-block">
      <span class="block-label">资金去向推测</span>
      <p v-if="report.fundFlowAnalysis.summary">{{ report.fundFlowAnalysis.summary }}</p>
      <ul v-if="report.fundFlowAnalysis.inferredPaths?.length" class="detail-list">
        <li v-for="(p, i) in report.fundFlowAnalysis.inferredPaths" :key="'fp-' + i">{{ p }}</li>
      </ul>
      <ul v-if="report.fundFlowAnalysis.freezeTargets?.length" class="detail-list warn-list">
        <li v-for="(t, i) in report.fundFlowAnalysis.freezeTargets" :key="'ft-' + i">
          <strong>建议追踪</strong> {{ t }}
        </li>
      </ul>
      <ul v-if="report.fundFlowAnalysis.limitations?.length" class="detail-list muted-list">
        <li v-for="(l, i) in report.fundFlowAnalysis.limitations" :key="'fl-' + i">{{ l }}</li>
      </ul>
    </article>

    <article v-if="report.systemWarnings?.length" class="panel-block warn-block">
      <span class="block-label">系统曾发出的预警</span>
      <ul class="detail-list">
        <li v-for="(w, i) in report.systemWarnings" :key="'sw-' + i">{{ w }}</li>
      </ul>
    </article>

    <div class="two-col">
      <article v-if="report.evidenceInventory?.length" class="panel-block">
        <span class="block-label">证据清单</span>
        <ul class="detail-list">
          <li v-for="(e, i) in report.evidenceInventory" :key="'ev-' + i">{{ e }}</li>
        </ul>
      </article>
      <article v-if="report.policeChecklist?.length" class="panel-block">
        <span class="block-label">建议警方核查</span>
        <ul class="detail-list">
          <li v-for="(c, i) in report.policeChecklist" :key="'pc-' + i">{{ c }}</li>
        </ul>
      </article>
    </div>

    <article v-if="report.victimActionItems?.length" class="panel-block action-block">
      <span class="block-label">您现在可以做的</span>
      <ul class="detail-list">
        <li v-for="(v, i) in report.victimActionItems" :key="'va-' + i">{{ v }}</li>
      </ul>
    </article>

    <p v-if="report.disclaimer" class="disclaimer">{{ report.disclaimer }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { PayRiskPoliceReport } from '@/types'

const props = defineProps<{
  report?: PayRiskPoliceReport
  generatedAt?: string
}>()

const emit = defineEmits<{
  copied: [text: string]
}>()

const urgencyLabels: Record<string, string> = {
  URGENT: '紧急',
  HIGH: '高',
  MEDIUM: '中',
  LOW: '低'
}

const categoryLabels: Record<string, string> = {
  account: '账户',
  phone: '手机',
  link: '链接',
  ip: 'IP',
  identity: '身份',
  platform: '平台',
  other: '其他'
}

const urgencyLabel = computed(() => {
  const level = props.report?.urgencyLevel?.toUpperCase() || 'MEDIUM'
  return urgencyLabels[level] || level
})

const urgencyClass = computed(() => {
  const level = props.report?.urgencyLevel?.toUpperCase()
  if (level === 'URGENT' || level === 'HIGH') return 'chip-urgent'
  if (level === 'MEDIUM') return 'chip-warn'
  return 'chip-neutral'
})

const hasTransferSummary = computed(() => {
  const t = props.report?.transferSummary
  if (!t) return false
  return !!(
    t.amounts?.length ||
    t.channels?.length ||
    t.payeeAccounts?.length ||
    t.totalLossEstimate
  )
})

const categoryLabel = (cat?: string) => categoryLabels[cat || 'other'] || cat || '线索'

const formatTime = (iso: string) => {
  try {
    return new Date(iso).toLocaleString('zh-CN')
  } catch {
    return iso
  }
}

const copyStatement = async () => {
  const text = props.report?.printableStatement?.trim()
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    emit('copied', text)
  } catch {
    emit('copied', '')
  }
}
</script>

<style scoped>
.police-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.panel-eyebrow {
  display: block;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: #1d4ed8;
  text-transform: uppercase;
}

.panel-head h4 {
  margin: 4px 0 0;
  font-size: 17px;
  color: #0f172a;
}

.panel-summary {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.55;
  color: #475569;
}

.badge-group {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-content: flex-start;
}

.chip {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}

.chip-mode {
  background: #e0e7ff;
  color: #3730a3;
}

.chip-urgent {
  background: #fee2e2;
  color: #991b1b;
}

.chip-warn {
  background: #ffedd5;
  color: #9a3412;
}

.chip-neutral {
  background: #f1f5f9;
  color: #475569;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.meta-tag {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 8px;
  background: #f8fafc;
  color: #334155;
}

.panel-block {
  padding: 12px 14px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid #e2e8f0;
}

.statement-block {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.block-label {
  display: block;
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  margin-bottom: 6px;
}

.block-head .block-label {
  margin-bottom: 0;
}

.statement-text {
  margin: 0;
  line-height: 1.65;
  font-size: 13px;
  color: #1e293b;
  white-space: pre-wrap;
}

.mini-btn {
  border: 0;
  padding: 5px 10px;
  border-radius: 8px;
  background: #1d4ed8;
  color: #fff;
  font-size: 11px;
  cursor: pointer;
}

.detail-list {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.55;
  color: #334155;
}

.detail-list li + li {
  margin-top: 4px;
}

.timeline {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.timeline li {
  padding-left: 10px;
  border-left: 3px solid #93c5fd;
}

.tl-time {
  display: inline-block;
  font-size: 11px;
  color: #64748b;
  margin-right: 8px;
}

.tl-phase {
  display: inline-block;
  font-size: 11px;
  font-weight: 700;
  color: #1d4ed8;
  margin-right: 6px;
}

.timeline p {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.5;
  color: #334155;
}

.clue-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.clue-list li {
  font-size: 13px;
  line-height: 1.5;
}

.clue-cat {
  display: inline-block;
  margin-right: 6px;
  padding: 2px 6px;
  border-radius: 6px;
  background: #f1f5f9;
  font-size: 11px;
  color: #475569;
}

.clue-note {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: #64748b;
}

.loss-estimate {
  margin: 8px 0 0;
  font-weight: 700;
  color: #b91c1c;
  font-size: 13px;
}

.warn-block {
  background: #fff7ed;
  border-color: #fed7aa;
}

.action-block {
  background: #f0fdf4;
  border-color: #bbf7d0;
}

.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.disclaimer {
  margin: 0;
  font-size: 11px;
  line-height: 1.5;
  color: #94a3b8;
}

@media (max-width: 640px) {
  .two-col {
    grid-template-columns: 1fr;
  }

  .panel-head {
    flex-direction: column;
  }
}
</style>
