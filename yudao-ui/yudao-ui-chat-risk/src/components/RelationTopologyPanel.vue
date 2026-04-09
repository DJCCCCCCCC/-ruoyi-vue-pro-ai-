<template>
  <section v-if="hasTopology" class="topology-panel">
    <div class="panel-head">
      <div>
        <p class="panel-kicker">Relation Topology</p>
        <h3>人物关系拓扑</h3>
        <p class="panel-desc">把付款人、收款人和共享属性放进同一张关系图里，方便快速判断谁在触发异常链路。</p>
      </div>
      <span :class="['status-pill', `tone-${overallTone}`]">{{ overallLabel }}</span>
    </div>

    <div class="summary-grid">
      <article v-for="item in summaryCards" :key="item.label" class="summary-card">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <p>{{ item.caption }}</p>
      </article>
    </div>

    <div class="content-grid">
      <div class="graph-card">
        <div class="graph-meta">
          <span>{{ graphMeta }}</span>
          <span>点击风险信号可高亮相关人物和链路</span>
        </div>
        <svg class="graph-svg" :viewBox="`0 0 660 ${svgHeight}`" role="img" aria-label="人物关系拓扑图">
          <g v-for="edge in visibleEdges" :key="edge.id">
            <path :d="edge.path" :class="['graph-edge', `tone-${edge.tone}`, { dimmed: edge.dimmed }]" />
            <text
              v-if="edge.label"
              :x="edge.labelX"
              :y="edge.labelY"
              class="edge-label"
              text-anchor="middle"
            >
              {{ edge.label }}
            </text>
          </g>

          <g
            v-for="node in layoutNodes"
            :key="node.id"
            class="graph-node"
            :class="[{ active: selectedNodeId === node.id, dimmed: node.dimmed }]"
            @click="selectedNodeId = node.id"
          >
            <rect
              v-if="node.shape === 'rect'"
              :x="node.x - node.width / 2"
              :y="node.y - node.height / 2"
              :width="node.width"
              :height="node.height"
              :rx="18"
              :fill="node.fill"
              :stroke="node.stroke"
              :stroke-width="node.strokeWidth"
            />
            <circle
              v-else
              :cx="node.x"
              :cy="node.y"
              :r="node.radius"
              :fill="node.fill"
              :stroke="node.stroke"
              :stroke-width="node.strokeWidth"
            />
            <text :x="node.x" :y="node.y - 4" class="node-title" text-anchor="middle">{{ node.title }}</text>
            <text :x="node.x" :y="node.y + 14" class="node-subtitle" text-anchor="middle">{{ node.subtitle }}</text>
          </g>
        </svg>
      </div>

      <aside class="side-panel">
        <article class="info-card">
          <p class="card-title">风险信号</p>
          <div v-if="signalCards.length" class="signal-list">
            <button
              v-for="signal in signalCards"
              :key="signal.code"
              type="button"
              :class="['signal-chip', `tone-${signal.tone}`, { active: activeSignalCode === signal.code }]"
              @click="toggleSignal(signal.code)"
            >
              <strong>{{ signal.title }}</strong>
              <span>+{{ signal.score }}</span>
            </button>
          </div>
          <p v-else class="empty-text">当前没有额外关系信号。</p>
        </article>

        <article class="info-card">
          <p class="card-title">节点详情</p>
          <template v-if="selectedNodeDetail">
            <strong class="detail-name">{{ selectedNodeDetail.label }}</strong>
            <p class="detail-role">{{ selectedNodeDetail.role }}</p>
            <div class="chip-row">
              <span class="chip">{{ selectedNodeDetail.riskLabel }}</span>
              <span class="chip">{{ selectedNodeDetail.relationCountLabel }}</span>
              <span v-if="selectedNodeDetail.amountLabel" class="chip">{{ selectedNodeDetail.amountLabel }}</span>
            </div>
            <div class="detail-list">
              <span v-for="item in selectedNodeDetail.extraInfo" :key="item">{{ item }}</span>
            </div>
          </template>
          <p v-else class="empty-text">点击图中的人物、账户或设备节点查看详情。</p>
        </article>
        <article class="info-card">
          <p class="card-title">Signal Focus</p>
          <template v-if="activeSignalDetail">
            <strong class="detail-name">{{ activeSignalDetail.title }}</strong>
            <p class="detail-role">{{ activeSignalDetail.description }}</p>
            <div class="chip-row">
              <span class="chip">{{ activeSignalDetail.scoreLabel }}</span>
              <span class="chip">{{ activeSignalDetail.nodeLabel }}</span>
              <span class="chip">{{ activeSignalDetail.edgeLabel }}</span>
            </div>
            <div class="detail-list">
              <span v-for="item in activeSignalDetail.nodes" :key="item">{{ item }}</span>
            </div>
          </template>
          <p v-else class="empty-text">Tap a signal to focus the related people and links.</p>
        </article>

        <article class="info-card">
          <p class="card-title">Key Links</p>
          <div v-if="relationHighlights.length" class="highlight-list">
            <div v-for="item in relationHighlights" :key="item.id" class="highlight-item">
              <strong>{{ item.title }}</strong>
              <span>{{ item.badge }}</span>
              <p>{{ item.caption }}</p>
            </div>
          </div>
          <p v-else class="empty-text">No transfer links are currently highlighted.</p>
        </article>
      </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { PayRiskRelationTopology, RiskLevel } from '@/types'

interface Props {
  topology?: PayRiskRelationTopology
}

type Tone = 'safe' | 'neutral' | 'warn'

const props = defineProps<Props>()

const selectedNodeId = ref('')
const activeSignalCode = ref('')

const toneMap: Record<string, Tone> = { LOW: 'safe', MEDIUM: 'neutral', HIGH: 'warn', CRITICAL: 'warn' }
const typeLabelMap: Record<string, string> = {
  PARTICIPANT: '人物',
  DEVICE: '设备',
  IP: 'IP',
  PHONE: '手机号',
  CARD: '卡号',
  ACCOUNT: '账户',
  EMAIL: '邮箱',
  MERCHANT: '商户'
}
const riskLabelMap: Record<RiskLevel, string> = {
  LOW: '低风险',
  MEDIUM: '中风险',
  HIGH: '高风险',
  CRITICAL: '极高风险'
}

const normalizeText = (value: unknown) => (value === null || value === undefined ? '' : String(value).trim())
const shortText = (value: unknown, len = 10) => {
  const text = normalizeText(value)
  return text.length > len ? `${text.slice(0, len - 1)}…` : text
}
const formatAmount = (value: unknown) => {
  if (value === null || value === undefined || value === '') return ''
  const amount = Number(value)
  return Number.isFinite(amount) ? `¥${amount.toLocaleString('zh-CN')}` : String(value)
}
const resolveTone = (level?: string): Tone => toneMap[level || 'LOW'] || 'neutral'
const riskRank = (level?: string) => ({ CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 }[level || 'LOW'] || 0)

const nodes = computed(() => props.topology?.nodes ?? [])
const edges = computed(() => props.topology?.edges ?? [])
const signals = computed(() => props.topology?.signals ?? [])
const hasTopology = computed(() => nodes.value.length > 0)
const activeSignal = computed(() => signals.value.find((item) => item.code === activeSignalCode.value))
const highlightedNodeIds = computed(() => new Set(activeSignal.value?.relatedNodeIds || []))

watch(
  nodes,
  (value) => {
    if (!value.length) {
      selectedNodeId.value = ''
      return
    }
    if (!value.some((item) => item.id === selectedNodeId.value)) {
      selectedNodeId.value = value[0].id
    }
  },
  { immediate: true }
)

const relationCountMap = computed(() => {
  const map = new Map<string, number>()
  edges.value.forEach((edge) => {
    map.set(edge.source, (map.get(edge.source) || 0) + 1)
    map.set(edge.target, (map.get(edge.target) || 0) + 1)
  })
  return map
})

const amountMap = computed(() => {
  const map = new Map<string, number>()
  edges.value.forEach((edge) => {
    const amount = Number(edge.amount) || 0
    if (!amount) return
    map.set(edge.source, (map.get(edge.source) || 0) + amount)
    map.set(edge.target, (map.get(edge.target) || 0) + amount)
  })
  return map
})

const groupedNodes = computed(() => {
  const payerOnly = [] as typeof nodes.value
  const shared = [] as typeof nodes.value
  const payeeOnly = [] as typeof nodes.value

  nodes.value.forEach((node) => {
    const tags = node.tags || []
    const isPayer = tags.includes('PAYER')
    const isPayee = tags.includes('PAYEE')
    if (node.type !== 'PARTICIPANT' || (isPayer && isPayee)) {
      shared.push(node)
      return
    }
    if (isPayee) {
      payeeOnly.push(node)
      return
    }
    payerOnly.push(node)
  })

  return { payerOnly, shared, payeeOnly }
})

const svgHeight = computed(() => {
  const maxLaneCount = Math.max(
    groupedNodes.value.payerOnly.length,
    groupedNodes.value.shared.length,
    groupedNodes.value.payeeOnly.length,
    1
  )
  return Math.max(360, 120 + maxLaneCount * 92)
})

const layoutNodes = computed(() => {
  const columns = [
    { list: groupedNodes.value.payerOnly, x: 120 },
    { list: groupedNodes.value.shared, x: 330 },
    { list: groupedNodes.value.payeeOnly, x: 540 }
  ]

  const positioned: Array<{
    id: string
    x: number
    y: number
    width: number
    height: number
    radius: number
    shape: 'rect' | 'circle'
    fill: string
    stroke: string
    strokeWidth: number
    title: string
    subtitle: string
    dimmed: boolean
  }> = []

  columns.forEach((column) => {
    const gap = svgHeight.value / (column.list.length + 1)
    column.list.forEach((node, index) => {
      const tone = resolveTone(node.riskLevel)
      const isParticipant = node.type === 'PARTICIPANT'
      const dimmed = Boolean(activeSignalCode.value) && !highlightedNodeIds.value.has(node.id)
      const fill = isParticipant
        ? node.tags?.includes('PAYEE')
          ? '#fff7ed'
          : '#eff6ff'
        : '#f8fafc'
      const stroke = tone === 'warn' ? '#dc2626' : tone === 'neutral' ? '#d97706' : isParticipant ? '#0ea5e9' : '#94a3b8'
      positioned.push({
        id: node.id,
        x: column.x,
        y: gap * (index + 1),
        width: isParticipant ? 136 : 84,
        height: isParticipant ? 62 : 62,
        radius: 28,
        shape: isParticipant ? 'rect' : 'circle',
        fill,
        stroke,
        strokeWidth: tone === 'warn' ? 3 : 2,
        title: shortText(node.label || node.id, isParticipant ? 10 : 8),
        subtitle: isParticipant ? (node.tags?.includes('PAYEE') ? '收款主体' : '付款主体') : typeLabelMap[node.type] || node.type,
        dimmed
      })
    })
  })

  return positioned
})

const positionMap = computed(() => Object.fromEntries(layoutNodes.value.map((node) => [node.id, node])))

const visibleEdges = computed(() =>
  edges.value
    .filter((edge) => positionMap.value[edge.source] && positionMap.value[edge.target])
    .map((edge, index) => {
      const source = positionMap.value[edge.source]
      const target = positionMap.value[edge.target]
      const midX = (source.x + target.x) / 2
      const label = [shortText(edge.label || edge.meta?.relationLabel, 8), formatAmount(edge.amount)].filter(Boolean).join(' · ')
      const dimmed =
        Boolean(activeSignalCode.value) &&
        !highlightedNodeIds.value.has(edge.source) &&
        !highlightedNodeIds.value.has(edge.target)
      return {
        id: `${edge.source}-${edge.target}-${index}`,
        tone: resolveTone(edge.riskLevel),
        dimmed,
        label,
        labelX: midX,
        labelY: (source.y + target.y) / 2 - 10,
        path: `M ${source.x} ${source.y} C ${midX} ${source.y}, ${midX} ${target.y}, ${target.x} ${target.y}`
      }
    })
)

const overallTone = computed<Tone>(() =>
  signals.value.some((item) => ['HIGH', 'CRITICAL'].includes(item.level)) ? 'warn' : signals.value.length ? 'neutral' : 'safe'
)
const overallLabel = computed(() =>
  overallTone.value === 'warn' ? '关系风险较高' : overallTone.value === 'neutral' ? '存在可疑链路' : '关系结构平稳'
)

const summaryCards = computed(() => [
  {
    label: '参与节点',
    value: props.topology?.summary?.nodeCount || nodes.value.length,
    caption: `${props.topology?.summary?.participantCount || 0} 个主体 / ${props.topology?.summary?.sharedAttributeCount || 0} 个共享属性`
  },
  {
    label: '关系连线',
    value: props.topology?.summary?.edgeCount || edges.value.length,
    caption: `${props.topology?.summary?.highRiskEdgeCount || 0} 条高风险链路`
  },
  {
    label: '风险信号',
    value: props.topology?.summary?.signalCount || signals.value.length,
    caption: `${props.topology?.summary?.highRiskNodeCount || 0} 个高风险节点`
  }
])

const signalCards = computed(() =>
  signals.value.map((signal) => ({
    ...signal,
    tone: resolveTone(signal.level),
    score: signal.score || 0
  }))
)
const nodeMap = computed(() => Object.fromEntries(nodes.value.map((node) => [node.id, node])))
const activeSignalEdgeCount = computed(
  () =>
    edges.value.filter(
      (edge) => highlightedNodeIds.value.has(edge.source) || highlightedNodeIds.value.has(edge.target)
    ).length
)
const activeSignalDetail = computed(() => {
  if (!activeSignal.value) return null
  const relatedNodes = (activeSignal.value.relatedNodeIds || [])
    .map((id) => nodeMap.value[id])
    .filter(Boolean)
    .slice(0, 5)
    .map((node) => `${node.label} / ${typeLabelMap[node.type] || node.type}`)
  return {
    title: activeSignal.value.title,
    description: activeSignal.value.description,
    scoreLabel: `+${activeSignal.value.score || 0} weight`,
    nodeLabel: `${highlightedNodeIds.value.size} related nodes`,
    edgeLabel: `${activeSignalEdgeCount.value} related links`,
    nodes: relatedNodes
  }
})
const relationHighlights = computed(() =>
  edges.value
    .filter((edge) => edge.type === 'TRANSFER')
    .sort((a, b) => {
      const rankDiff = riskRank(b.riskLevel) - riskRank(a.riskLevel)
      if (rankDiff !== 0) return rankDiff
      return (Number(b.amount) || 0) - (Number(a.amount) || 0)
    })
    .slice(0, 4)
    .map((edge, index) => ({
      id: `${edge.source}-${edge.target}-${index}`,
      title: `${shortText(nodeMap.value[edge.source]?.label || edge.source, 8)} -> ${shortText(nodeMap.value[edge.target]?.label || edge.target, 8)}`,
      badge: formatAmount(edge.amount) || (edge.riskLevel || 'LINK'),
      caption: [shortText(edge.label || edge.meta?.relationLabel, 10), normalizeText(edge.meta?.scene)].filter(Boolean).join(' / ')
    }))
)

const selectedNodeDetail = computed(() => {
  const node = nodes.value.find((item) => item.id === selectedNodeId.value)
  if (!node) return null
  const meta = (node.meta || {}) as Record<string, unknown>
  return {
    label: node.label,
    role:
      node.type === 'PARTICIPANT'
        ? node.tags?.includes('PAYEE')
          ? '收款主体'
          : node.tags?.includes('PAYER')
            ? '付款主体'
            : '参与主体'
        : typeLabelMap[node.type] || node.type,
    relationCountLabel: `${relationCountMap.value.get(node.id) || 0} 条关联`,
    amountLabel: amountMap.value.get(node.id) ? formatAmount(amountMap.value.get(node.id)) : '',
    riskLabel: riskLabelMap[(node.riskLevel || 'LOW') as RiskLevel] || '低风险',
    extraInfo: [
      normalizeText(meta.mobile) ? `手机号 ${normalizeText(meta.mobile)}` : '',
      normalizeText(meta.ip) ? `IP ${normalizeText(meta.ip)}` : '',
      normalizeText(meta.deviceId || meta.fingerprint) ? `设备 ${normalizeText(meta.deviceId || meta.fingerprint)}` : '',
      normalizeText(meta.accountNo || meta.merchantNo) ? `账户 ${normalizeText(meta.accountNo || meta.merchantNo)}` : '',
      normalizeText(meta.value) ? `属性 ${normalizeText(meta.value)}` : ''
    ].filter(Boolean)
  }
})

const graphMeta = computed(
  () =>
    `交易 ${props.topology?.summary?.transactionCount || 0} 笔，节点 ${nodes.value.length} 个，信号 ${signals.value.length} 条`
)

const toggleSignal = (code: string) => {
  activeSignalCode.value = activeSignalCode.value === code ? '' : code
}
</script>

<style scoped>
.topology-panel {
  margin-top: 14px;
  padding: 16px;
  border-radius: 20px;
  background:
    radial-gradient(circle at 10% 0%, rgba(14, 165, 233, 0.14), transparent 30%),
    radial-gradient(circle at 100% 12%, rgba(249, 115, 22, 0.1), transparent 24%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(245, 249, 255, 0.98));
  border: 1px solid rgba(148, 163, 184, 0.16);
}

.panel-head,
.signal-chip,
.summary-grid,
.content-grid,
.chip-row,
.detail-list {
  display: flex;
}

.panel-head {
  justify-content: space-between;
  gap: 12px;
}

.panel-kicker,
.summary-card span,
.card-title {
  margin: 0;
  color: #6d8599;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.panel-head h3 {
  margin: 4px 0 0;
  font-size: 20px;
  color: #12202f;
}

.panel-desc,
.summary-card p,
.detail-role,
.empty-text {
  margin: 8px 0 0;
  color: #556d80;
  font-size: 13px;
  line-height: 1.7;
}

.status-pill {
  height: fit-content;
  padding: 8px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.tone-safe.status-pill {
  background: rgba(34, 197, 94, 0.14);
  color: #15803d;
}

.tone-neutral.status-pill {
  background: rgba(245, 158, 11, 0.16);
  color: #b45309;
}

.tone-warn.status-pill {
  background: rgba(239, 68, 68, 0.14);
  color: #b91c1c;
}

.summary-grid {
  gap: 10px;
  margin-top: 14px;
}

.summary-card,
.graph-card,
.info-card {
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  background: rgba(255, 255, 255, 0.9);
}

.summary-card {
  flex: 1;
  min-width: 0;
  padding: 14px;
  flex-direction: column;
}

.summary-card strong {
  display: block;
  margin-top: 6px;
  color: #12202f;
  font-size: 24px;
}

.content-grid {
  margin-top: 14px;
  gap: 14px;
  align-items: stretch;
}

.graph-card {
  flex: 1.35;
  padding: 14px;
  background:
    radial-gradient(circle at center, rgba(59, 130, 246, 0.08), transparent 52%),
    linear-gradient(180deg, #f9fbff 0%, #eef5fb 100%);
}

.graph-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: #687f92;
  font-size: 12px;
}

.graph-svg {
  width: 100%;
  min-height: 360px;
  margin-top: 10px;
}

.graph-edge {
  fill: none;
  stroke-width: 2.4;
  opacity: 0.92;
}

.graph-edge.tone-safe {
  stroke: #22c55e;
}

.graph-edge.tone-neutral {
  stroke: #f59e0b;
}

.graph-edge.tone-warn {
  stroke: #ef4444;
}

.graph-edge.dimmed {
  opacity: 0.14;
}

.edge-label {
  fill: #4b6477;
  font-size: 11px;
}

.graph-node {
  cursor: pointer;
  transition: opacity 0.18s ease;
}

.graph-node.dimmed {
  opacity: 0.26;
}

.graph-node.active rect,
.graph-node.active circle {
  filter: drop-shadow(0 10px 20px rgba(15, 23, 42, 0.14));
}

.node-title {
  fill: #12202f;
  font-size: 12px;
  font-weight: 700;
}

.node-subtitle {
  fill: #64748b;
  font-size: 10px;
}

.side-panel {
  width: 248px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.info-card {
  padding: 14px;
  flex-direction: column;
}

.card-title {
  letter-spacing: 0;
}

.signal-list {
  display: grid;
  gap: 8px;
  margin-top: 10px;
}

.signal-chip {
  width: 100%;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  background: rgba(248, 251, 255, 0.88);
  cursor: pointer;
}

.signal-chip strong {
  color: #12202f;
  font-size: 12px;
  text-align: left;
}

.signal-chip span {
  font-size: 12px;
  font-weight: 700;
}

.signal-chip.tone-safe span {
  color: #15803d;
}

.signal-chip.tone-neutral span {
  color: #b45309;
}

.signal-chip.tone-warn span {
  color: #b91c1c;
}

.signal-chip.active {
  border-color: rgba(14, 165, 233, 0.24);
  box-shadow: 0 10px 20px rgba(14, 165, 233, 0.12);
}

.highlight-list {
  display: grid;
  gap: 8px;
  margin-top: 10px;
}

.highlight-item {
  padding: 12px;
  border-radius: 14px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  background: rgba(248, 251, 255, 0.88);
}

.highlight-item strong,
.highlight-item span {
  display: block;
}

.highlight-item strong {
  color: #12202f;
  font-size: 13px;
}

.highlight-item span {
  margin-top: 4px;
  color: #b91c1c;
  font-size: 12px;
  font-weight: 700;
}

.highlight-item p {
  margin: 8px 0 0;
  color: #556d80;
  font-size: 12px;
  line-height: 1.6;
}

.detail-name {
  color: #12202f;
  font-size: 18px;
}

.chip-row,
.detail-list {
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.chip,
.detail-list span {
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(226, 232, 240, 0.56);
  color: #294256;
  font-size: 12px;
  font-weight: 600;
}

@media (max-width: 768px) {
  .panel-head,
  .summary-grid,
  .content-grid {
    flex-direction: column;
  }

  .side-panel {
    width: 100%;
  }
}
</style>
