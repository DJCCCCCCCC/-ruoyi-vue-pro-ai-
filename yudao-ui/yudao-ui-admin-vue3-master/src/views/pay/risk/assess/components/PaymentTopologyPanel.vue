<template>
  <section v-if="hasTopology" class="topology-panel">
    <div class="panel-head">
      <div>
        <p class="panel-kicker">Relation Graph</p>
        <h3>支付关系拓扑</h3>
        <p class="panel-desc">基于 ECharts Graph 展示付款人、收款人和共享属性之间的关系，支持拖拽、缩放、漫游和高亮联动。</p>
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

    <div class="toolbar">
      <label class="search-box">
        <span>节点搜索</span>
        <input v-model.trim="searchKeyword" type="text" placeholder="名称、账号、设备、IP" />
      </label>
      <button type="button" :class="['toolbar-btn', { active: onlyRisk }]" @click="onlyRisk = !onlyRisk">
        只看风险节点
      </button>
      <button
        v-for="type in availableNodeTypes"
        :key="type"
        type="button"
        :class="['toolbar-btn', { active: activeTypes.includes(type) }]"
        @click="toggleNodeType(type)"
      >
        {{ typeLabelMap[type] || type }}
      </button>
      <button
        v-if="searchKeyword || onlyRisk || activeTypes.length || activeSignalCode"
        type="button"
        class="toolbar-btn toolbar-btn-reset"
        @click="resetFilters"
      >
        清空筛选
      </button>
    </div>

    <div class="content-grid">
      <div class="chart-shell">
        <div class="chart-meta">
          <span>{{ chartMeta }}</span>
          <span>滚轮缩放，拖动画布或节点可调整视图</span>
        </div>
        <div ref="chartRef" class="chart-host"></div>
      </div>

      <aside class="side-panel">
        <article class="info-card">
          <p class="card-title">节点图例</p>
          <div class="legend-item"><i class="legend-dot payer"></i><span>付款人</span></div>
          <div class="legend-item"><i class="legend-dot payee"></i><span>收款人</span></div>
          <div class="legend-item"><i class="legend-dot attr"></i><span>共享属性</span></div>
          <div class="legend-item"><i class="legend-dot warn"></i><span>高风险节点</span></div>
        </article>

        <article class="info-card">
          <p class="card-title">关系焦点</p>
          <div v-if="relationHighlights.length" class="highlight-list">
            <div v-for="item in relationHighlights" :key="item.id" :class="['highlight-item', `tone-${item.tone}`]">
              <strong>{{ item.title }}</strong>
              <span>{{ item.badge }}</span>
              <p>{{ item.caption }}</p>
            </div>
          </div>
          <p v-else class="empty-text">当前未识别到显著关系链路。</p>
        </article>

        <article class="info-card">
          <p class="card-title">节点详情</p>
          <template v-if="selectedNodeDetail">
            <strong class="detail-name">{{ selectedNodeDetail.label }}</strong>
            <p class="detail-role">{{ selectedNodeDetail.caption }}</p>
            <div class="chip-row">
              <span class="chip">{{ selectedNodeDetail.relationCountLabel }}</span>
              <span v-if="selectedNodeDetail.amountLabel" class="chip">{{ selectedNodeDetail.amountLabel }}</span>
              <span class="chip">{{ selectedNodeDetail.riskLabel }}</span>
            </div>
            <div class="detail-list">
              <span v-for="item in selectedNodeDetail.extraInfo" :key="item">{{ item }}</span>
            </div>
          </template>
          <p v-else class="empty-text">点击图中的节点，可查看角色、交易金额和属性信息。</p>
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
          <p v-else class="empty-text">Click a signal card below to focus related nodes and links.</p>
        </article>

        <article class="info-card">
          <p class="card-title">High Risk Nodes</p>
          <div v-if="topRiskNodes.length" class="risk-node-list">
            <div v-for="item in topRiskNodes" :key="item.id" class="risk-node-item">
              <strong>{{ item.label }}</strong>
              <span>{{ item.riskLabel }}</span>
              <p>{{ item.caption }}</p>
            </div>
          </div>
          <p v-else class="empty-text">No additional high risk nodes are highlighted in the current view.</p>
        </article>
      </aside>
    </div>

    <div v-if="signalCards.length" class="signal-grid">
      <article
        v-for="signal in signalCards"
        :key="signal.code"
        :class="['signal-card', `tone-${signal.tone}`, { active: activeSignalCode === signal.code }]"
        @click="toggleSignal(signal.code)"
      >
        <div class="signal-head">
          <strong>{{ signal.title }}</strong>
          <span>{{ signal.badge }}</span>
        </div>
        <p>{{ signal.description }}</p>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { ECharts, EChartsOption } from 'echarts'
import echarts from '@/plugins/echarts'
import type { PayRiskRelationTopology } from '@/api/pay/risk/assess'

interface Props {
  topology?: PayRiskRelationTopology
  paymentData?: Record<string, any> | null
}

type Tone = 'safe' | 'neutral' | 'warn'

const props = defineProps<Props>()

const chartRef = ref<HTMLDivElement>()
let chartInstance: ECharts | null = null

const selectedNodeId = ref('')
const searchKeyword = ref('')
const onlyRisk = ref(false)
const activeSignalCode = ref('')
const activeTypes = ref<string[]>([])

const toneMap: Record<string, Tone> = { LOW: 'safe', MEDIUM: 'neutral', HIGH: 'warn', CRITICAL: 'warn' }
const riskLabelMap: Record<string, string> = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险', CRITICAL: '极高风险' }
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

const normalizeText = (value: unknown) => (value === null || value === undefined ? '' : String(value).trim())
const shortText = (value: unknown, len = 14) => {
  const text = normalizeText(value)
  return text.length > len ? `${text.slice(0, len - 1)}…` : text
}
const firstText = (source: Record<string, any> | undefined, keys: string[]) => {
  if (!source) return ''
  for (const key of keys) {
    const text = normalizeText(source[key])
    if (text) return text
  }
  return ''
}
const formatAmount = (value: unknown) => {
  if (value === null || value === undefined || value === '') return ''
  const amount = Number(value)
  return Number.isFinite(amount) ? `￥${amount.toLocaleString('zh-CN')}` : `￥${value}`
}
const resolveTone = (level?: string): Tone => toneMap[level || 'LOW'] || 'neutral'
const riskRank = (level?: string) => ({ CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 }[level || 'LOW'] || 0)

const buildFallbackTopology = (paymentData?: Record<string, any> | null): PayRiskRelationTopology | undefined => {
  if (!paymentData) return undefined
  const transactions = Array.isArray(paymentData.transactions) ? paymentData.transactions : []
  if (!transactions.length) return undefined

  const nodes = new Map<string, any>()
  const edges: any[] = []
  const signals: any[] = []
  const deviceBindings = new Map<string, Set<string>>()
  const payeeBindings = new Map<string, Set<string>>()

  const addNode = (node: any) => {
    if (!nodes.has(node.id)) nodes.set(node.id, node)
  }

  const addAttribute = (ownerId: string, type: string, value: string, label: string) => {
    if (!value) return
    const id = `${type}:${value}`
    addNode({ id, label: shortText(value, 16), type, riskLevel: 'MEDIUM', tags: [type], meta: { value } })
    edges.push({ source: ownerId, target: id, type: `LINK_${type}`, riskLevel: 'LOW', label })
    if (type === 'DEVICE') {
      if (!deviceBindings.has(value)) deviceBindings.set(value, new Set())
      deviceBindings.get(value)!.add(ownerId)
    }
  }

  const addParticipant = (data: Record<string, any> | undefined, role: 'PAYER' | 'PAYEE') => {
    if (!data) return ''
    const identity =
      firstText(data, ['userId', 'memberId', 'merchantNo', 'accountNo', 'account', 'mobile', 'phone', 'id']) ||
      firstText(data, ['name', 'merchantName', 'realName'])
    if (!identity) return ''
    const label = firstText(data, ['name', 'merchantName', 'realName', 'accountNo', 'merchantNo']) || identity
    const id = `participant:${identity}`
    addNode({ id, label, type: 'PARTICIPANT', riskLevel: role === 'PAYEE' ? 'MEDIUM' : 'LOW', tags: [role], meta: data })
    addAttribute(id, 'DEVICE', firstText(data, ['deviceId', 'fingerprint']), '设备')
    addAttribute(id, 'IP', firstText(data, ['ip']), 'IP')
    addAttribute(id, 'ACCOUNT', firstText(data, ['accountNo', 'merchantNo', 'bankCardNo', 'cardNo']), '账户')
    return id
  }

  transactions.forEach((item) => {
    const payerId = addParticipant(item?.payer, 'PAYER')
    const payeeId = addParticipant(item?.payee, 'PAYEE')
    if (!payerId || !payeeId) return
    edges.push({
      source: payerId,
      target: payeeId,
      type: 'TRANSFER',
      riskLevel: item?.riskLevel || 'MEDIUM',
      amount: item?.amount,
      label: item?.relationLabel || item?.relationType || '资金流转',
      meta: { relationLabel: item?.relationLabel || item?.relationType || '', scene: item?.scene || item?.scenarioTag || '' }
    })
    if (!payeeBindings.has(payeeId)) payeeBindings.set(payeeId, new Set())
    payeeBindings.get(payeeId)!.add(payerId)
  })

  deviceBindings.forEach((participants, deviceId) => {
    if (participants.size >= 2) {
      signals.push({
        code: `DEVICE_${deviceId}`,
        level: 'HIGH',
        title: '共享设备',
        description: `${participants.size} 个主体复用同一设备 ${deviceId}`,
        score: 10
      })
    }
  })

  payeeBindings.forEach((payers, payeeId) => {
    if (payers.size >= 2) {
      signals.push({
        code: `PAYEE_${payeeId}`,
        level: 'HIGH',
        title: '收款聚合',
        description: `同一收款主体连接 ${payers.size} 个付款主体`,
        score: 12
      })
    }
  })

  return {
    summary: {
      nodeCount: nodes.size,
      edgeCount: edges.length,
      participantCount: [...nodes.values()].filter((item) => item.type === 'PARTICIPANT').length,
      payerCount: [...nodes.values()].filter((item) => item.tags?.includes('PAYER')).length,
      payeeCount: [...nodes.values()].filter((item) => item.tags?.includes('PAYEE')).length,
      transactionCount: transactions.length,
      signalCount: signals.length,
      sharedAttributeCount: [...deviceBindings.values()].filter((item) => item.size >= 2).length
    },
    nodes: [...nodes.values()],
    edges,
    signals
  }
}

const topologyData = computed(() => props.topology || buildFallbackTopology(props.paymentData))
const rawNodes = computed(() => topologyData.value?.nodes ?? [])
const rawEdges = computed(() => topologyData.value?.edges ?? [])
const signals = computed(() => topologyData.value?.signals ?? [])
const hasTopology = computed(() => rawNodes.value.length > 0)
const availableNodeTypes = computed(() =>
  Array.from(new Set(rawNodes.value.map((node) => node.type).filter(Boolean))).sort()
)
const activeSignal = computed(() => signals.value.find((item) => item.code === activeSignalCode.value))
const highlightedNodeIds = computed(() => new Set(activeSignal.value?.relatedNodeIds || []))
const nodes = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  return rawNodes.value.filter((node) => {
    if (onlyRisk.value && !['HIGH', 'CRITICAL'].includes(node.riskLevel || '')) {
      return false
    }
    if (activeTypes.value.length && !activeTypes.value.includes(node.type)) {
      return false
    }
    if (!keyword) {
      return true
    }
    const searchable = [
      node.label,
      node.id,
      node.type,
      ...(node.meta ? Object.values(node.meta).map((item) => normalizeText(item)) : [])
    ]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
    return searchable.includes(keyword)
  })
})
const visibleNodeIds = computed(() => new Set(nodes.value.map((node) => node.id)))
const edges = computed(() =>
  rawEdges.value.filter((edge) => visibleNodeIds.value.has(edge.source) && visibleNodeIds.value.has(edge.target))
)

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
  edges.value.filter((edge) => edge.type === 'TRANSFER').forEach((edge) => {
    const amount = Number(edge.amount) || 0
    map.set(edge.source, (map.get(edge.source) || 0) + amount)
    map.set(edge.target, (map.get(edge.target) || 0) + amount)
  })
  return map
})

const chartNodes = computed(() =>
  nodes.value.map((node) => {
    const tags = Array.isArray(node.tags) ? node.tags : []
    const isPayer = tags.includes('PAYER')
    const isPayee = tags.includes('PAYEE')
    const isParticipant = node.type === 'PARTICIPANT'
    const tone = resolveTone(node.riskLevel)
    const category = isPayer ? 0 : isPayee ? 1 : 2
    const amount = amountMap.value.get(node.id) || 0
    const relationCount = relationCountMap.value.get(node.id) || 0
    const symbolSize = isParticipant ? 72 + Math.min(relationCount * 3, 18) : 46 + Math.min(relationCount * 2, 10)
    const isHighlighted = !activeSignalCode.value || highlightedNodeIds.value.has(node.id)
    const baseColor = isPayer ? '#0ea5e9' : isPayee ? '#f97316' : '#94a3b8'
    const borderColor = tone === 'warn' ? '#dc2626' : tone === 'neutral' ? '#d97706' : baseColor
    const shadowColor =
      tone === 'warn' ? 'rgba(220, 38, 38, 0.28)' : tone === 'neutral' ? 'rgba(217, 119, 6, 0.22)' : 'rgba(14, 165, 233, 0.18)'

    return {
      id: node.id,
      name: shortText(node.label || node.id, isParticipant ? 12 : 14),
      value: amount || relationCount,
      category,
      draggable: true,
      symbol: isParticipant ? 'roundRect' : 'circle',
      symbolSize,
      itemStyle: {
        color: baseColor,
        borderColor,
        borderWidth: tone === 'warn' ? 3 : 2,
        shadowBlur: tone === 'warn' ? 28 : 18,
        shadowColor,
        opacity: isHighlighted ? 1 : 0.24
      },
      label: {
        show: true,
        color: '#12202f',
        fontSize: isParticipant ? 13 : 11,
        fontWeight: 700,
        formatter: () => shortText(node.label || node.id, isParticipant ? 12 : 14)
      },
      emphasis: {
        scale: true,
        itemStyle: {
          shadowBlur: 34,
          shadowColor
        }
      },
      tooltipValue: {
        role: isPayer ? '付款人' : isPayee ? '收款人' : node.type === 'DEVICE' ? '设备' : node.type === 'IP' ? 'IP' : '账户',
        meta: node.meta || {},
        relationCount,
        amount
      }
    }
  })
)

const chartLinks = computed(() =>
  edges.value.map((edge, index) => {
    const tone = resolveTone(edge.riskLevel)
    const color = tone === 'warn' ? '#dc2626' : tone === 'neutral' ? '#d97706' : '#64748b'
    const amountLabel = formatAmount(edge.amount)
    const sceneLabel = normalizeText(edge.meta?.scene)
    const relationLabel = normalizeText(edge.meta?.relationLabel || edge.label)
    const isHighlighted =
      !activeSignalCode.value ||
      highlightedNodeIds.value.has(edge.source) ||
      highlightedNodeIds.value.has(edge.target)
    return {
      id: `${edge.source}-${edge.target}-${index}`,
      source: edge.source,
      target: edge.target,
      value: [relationLabel, amountLabel || sceneLabel].filter(Boolean).join(' · '),
      lineStyle: {
        color,
        width: edge.type === 'TRANSFER' ? 2.8 + Math.min((Number(edge.amount) || 0) / 2000, 2) : 1.8,
        curveness: edge.type === 'TRANSFER' ? 0.22 : 0.08,
        opacity: isHighlighted ? 0.92 : 0.14
      },
      label: {
        show: edge.type === 'TRANSFER',
        color: '#395165',
        fontSize: 11,
        backgroundColor: 'rgba(255,255,255,0.92)',
        borderRadius: 12,
        padding: [5, 8],
        formatter: () => [relationLabel, amountLabel].filter(Boolean).join(' · ')
      },
      emphasis: {
        lineStyle: {
          width: edge.type === 'TRANSFER' ? 4.2 : 2.4
        }
      }
    }
  })
)

const overallTone = computed<Tone>(() => (signals.value.some((item) => ['HIGH', 'CRITICAL'].includes(item.level)) ? 'warn' : signals.value.length ? 'neutral' : 'safe'))
const overallLabel = computed(() => (overallTone.value === 'warn' ? '关系异常突出' : overallTone.value === 'neutral' ? '存在可疑链路' : '关系结构平稳'))

const totalAmount = computed(() => edges.value.filter((edge) => edge.type === 'TRANSFER').reduce((sum, edge) => sum + (Number(edge.amount) || 0), 0))
const summaryCards = computed(() => [
  { label: '参与节点', value: topologyData.value?.summary?.nodeCount || 0, caption: '付款人、收款人和共享属性节点总数' },
  { label: '关系连线', value: topologyData.value?.summary?.edgeCount || 0, caption: `${topologyData.value?.summary?.sharedAttributeCount || 0} 个共享属性连接` },
  { label: '交易笔数', value: topologyData.value?.summary?.transactionCount || 0, caption: totalAmount.value ? `总交易额 ${formatAmount(totalAmount.value)}` : '未识别交易金额' },
  {
    label: '风险信号',
    value: topologyData.value?.summary?.signalCount || 0,
    caption: `${overallLabel.value} · 高风险节点 ${topologyData.value?.summary?.highRiskNodeCount || 0}`
  }
])

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
      tone: resolveTone(edge.riskLevel),
      title: `${shortText(nodeMap.value[edge.source]?.label || edge.source, 10)} → ${shortText(nodeMap.value[edge.target]?.label || edge.target, 10)}`,
      badge: formatAmount(edge.amount) || '关系',
      caption: [normalizeText(edge.meta?.relationLabel || edge.label), normalizeText(edge.meta?.scene)].filter(Boolean).join(' / ')
    }))
)

const signalCards = computed(() => signals.value.map((item) => ({ ...item, tone: resolveTone(item.level), badge: `+${item.score || 0}` })))
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
const topRiskNodes = computed(() =>
  nodes.value
    .filter((node) => ['HIGH', 'CRITICAL'].includes(node.riskLevel || ''))
    .sort((a, b) => {
      const rankDiff = riskRank(b.riskLevel) - riskRank(a.riskLevel)
      if (rankDiff !== 0) return rankDiff
      return (relationCountMap.value.get(b.id) || 0) - (relationCountMap.value.get(a.id) || 0)
    })
    .slice(0, 5)
    .map((node) => ({
      id: node.id,
      label: node.label,
      riskLabel: riskLabelMap[node.riskLevel || 'LOW'] || 'LOW',
      caption: `${typeLabelMap[node.type] || node.type} / ${relationCountMap.value.get(node.id) || 0} links`
    }))
)

const selectedNodeDetail = computed(() => {
  const node = nodes.value.find((item) => item.id === selectedNodeId.value)
  if (!node) return null
  const meta = node.meta || {}
  return {
    label: node.label,
    caption:
      node.tags?.includes('PAYER')
        ? '付款人'
        : node.tags?.includes('PAYEE')
          ? '收款人'
          : node.type === 'DEVICE'
            ? '设备'
            : node.type === 'IP'
              ? 'IP'
              : '账户',
    relationCountLabel: `${relationCountMap.value.get(node.id) || 0} 条关联`,
    amountLabel: amountMap.value.get(node.id) ? formatAmount(amountMap.value.get(node.id)) : '',
    riskLabel: riskLabelMap[node.riskLevel || 'LOW'] || '低风险',
    extraInfo: [
      meta.mobile ? `手机号 ${meta.mobile}` : '',
      meta.ip ? `IP ${meta.ip}` : '',
      meta.deviceId || meta.fingerprint ? `设备 ${meta.deviceId || meta.fingerprint}` : '',
      meta.accountNo || meta.merchantNo ? `账户 ${meta.accountNo || meta.merchantNo}` : '',
      meta.value ? `值 ${meta.value}` : ''
    ].filter(Boolean)
  }
})

const chartMeta = computed(() => {
  const filterText = [
    searchKeyword.value ? `搜索“${searchKeyword.value}”` : '',
    onlyRisk.value ? '仅风险节点' : '',
    activeTypes.value.length ? `${activeTypes.value.length} 类节点` : '',
    activeSignal.value ? `聚焦 ${activeSignal.value.title}` : ''
  ]
    .filter(Boolean)
    .join(' / ')
  return `交易 ${topologyData.value?.summary?.transactionCount || 0} 笔，节点 ${nodes.value.length} 个，异常信号 ${signals.value.length} 条${filterText ? ` · ${filterText}` : ''}`
})

const toggleNodeType = (type: string) => {
  activeTypes.value = activeTypes.value.includes(type)
    ? activeTypes.value.filter((item) => item !== type)
    : [...activeTypes.value, type]
}

const toggleSignal = (code: string) => {
  activeSignalCode.value = activeSignalCode.value === code ? '' : code
}

const resetFilters = () => {
  searchKeyword.value = ''
  onlyRisk.value = false
  activeTypes.value = []
  activeSignalCode.value = ''
}

const chartOption = computed<EChartsOption>(() => ({
  backgroundColor: 'transparent',
  animationDuration: 900,
  animationEasingUpdate: 'cubicOut',
  color: ['#0ea5e9', '#f97316', '#94a3b8'],
  legend: {
    bottom: 8,
    itemWidth: 12,
    itemHeight: 12,
    textStyle: {
      color: '#60798d',
      fontSize: 12
    },
    data: ['付款人', '收款人', '共享属性']
  },
  tooltip: {
    trigger: 'item',
    backgroundColor: 'rgba(15, 23, 42, 0.92)',
    borderWidth: 0,
    textStyle: {
      color: '#f8fafc'
    },
    formatter: (params: any) => {
      if (params.dataType === 'edge') {
        return `${params.data.value || '关系链路'}`
      }
      const info = params.data.tooltipValue || {}
      const pieces = [
        `<strong>${params.data.name}</strong>`,
        info.role ? `<div>${info.role}</div>` : '',
        info.relationCount !== undefined ? `<div>关联数：${info.relationCount}</div>` : '',
        info.amount ? `<div>交易额：${formatAmount(info.amount)}</div>` : ''
      ]
      return pieces.filter(Boolean).join('')
    }
  },
  series: [
    {
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      symbolKeepAspect: true,
      force: {
        repulsion: 520,
        edgeLength: [110, 220],
        friction: 0.08,
        gravity: 0.06
      },
      left: 12,
      top: 20,
      right: 12,
      bottom: 40,
      categories: [
        { name: '付款人' },
        { name: '收款人' },
        { name: '共享属性' }
      ],
      emphasis: {
        focus: 'adjacency',
        lineStyle: {
          width: 4
        }
      },
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: [0, 10],
      data: chartNodes.value,
      links: chartLinks.value,
      lineStyle: {
        opacity: 0.9
      },
      labelLayout: {
        hideOverlap: true
      }
    } as any
  ]
}))

const handleChartClick = (params: any) => {
  if (params?.dataType === 'node' && params?.data?.id) {
    selectedNodeId.value = params.data.id
  }
}

const renderChart = async () => {
  await nextTick()
  if (!chartRef.value || !hasTopology.value) return
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
    chartInstance.on('click', handleChartClick)
    window.addEventListener('resize', resizeChart)
  }
  chartInstance.setOption(chartOption.value, true)
}

const resizeChart = () => {
  chartInstance?.resize()
}

watch(chartOption, () => {
  renderChart()
}, { deep: true })

onMounted(() => {
  renderChart()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  chartInstance?.off('click', handleChartClick)
  chartInstance?.dispose()
  chartInstance = null
})
</script>

<style scoped>
.topology-panel {
  border-radius: 24px;
  padding: 20px;
  background:
    radial-gradient(circle at 8% 0%, rgba(14, 165, 233, 0.14), transparent 30%),
    radial-gradient(circle at 100% 12%, rgba(249, 115, 22, 0.1), transparent 24%),
    linear-gradient(180deg, #ffffff 0%, #f5f9fd 100%);
  border: 1px solid #d8e4ef;
}

.chart-shell:hover {
  box-shadow: 0 18px 42px rgba(56, 113, 185, 0.12);
}

.panel-head,
.signal-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
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
.summary-card p,
.signal-card p,
.highlight-item p,
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

.summary-grid,
.content-grid,
.signal-grid {
  display: grid;
  gap: 14px;
  margin-top: 16px;
}

.toolbar {
  margin-top: 16px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.search-box {
  min-width: 220px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: #6d8599;
  font-size: 12px;
  font-weight: 700;
}

.search-box input {
  height: 38px;
  padding: 0 12px;
  border-radius: 12px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  background: rgba(255, 255, 255, 0.92);
  color: #12202f;
  outline: none;
}

.toolbar-btn {
  height: 38px;
  padding: 0 14px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.88);
  color: #365167;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.toolbar-btn.active {
  background: rgba(14, 165, 233, 0.12);
  border-color: rgba(14, 165, 233, 0.28);
  color: #0369a1;
}

.toolbar-btn-reset {
  background: rgba(15, 23, 42, 0.06);
  color: #475569;
}

.summary-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.content-grid {
  grid-template-columns: minmax(0, 1.6fr) 320px;
}

.summary-card,
.chart-shell,
.info-card,
.signal-card {
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.88);
}

.summary-card,
.info-card,
.signal-card {
  padding: 16px;
}

.summary-card span,
.card-title {
  color: #6d8599;
  font-size: 12px;
  font-weight: 700;
}

.summary-card strong {
  display: block;
  margin-top: 6px;
  color: #12202f;
  font-size: 28px;
}

.chart-shell {
  min-height: 720px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  background:
    radial-gradient(circle at center, rgba(59, 130, 246, 0.08), transparent 52%),
    linear-gradient(180deg, #f9fbff 0%, #eef5fb 100%);
}

.chart-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: #687f92;
  font-size: 12px;
}

.chart-host {
  flex: 1;
  min-height: 640px;
}

.side-panel,
.highlight-list,
.detail-list {
  display: grid;
  gap: 12px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #294256;
  font-size: 13px;
}

.legend-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.legend-dot.payer { background: #0ea5e9; }
.legend-dot.payee { background: #f97316; }
.legend-dot.attr { background: #94a3b8; }
.legend-dot.warn { background: #dc2626; box-shadow: 0 0 0 6px rgba(220, 38, 38, 0.12); }

.highlight-item {
  padding: 12px;
  border-radius: 14px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  background: rgba(248, 251, 255, 0.84);
}

.highlight-item strong,
.detail-name,
.signal-card strong {
  color: #12202f;
}

.highlight-item span,
.signal-card span {
  display: inline-block;
  margin-top: 6px;
  font-size: 12px;
  font-weight: 700;
}

.highlight-item.tone-safe span,
.signal-card.tone-safe span {
  color: #15803d;
}

.highlight-item.tone-neutral span,
.signal-card.tone-neutral span {
  color: #b45309;
}

.highlight-item.tone-warn span,
.signal-card.tone-warn span {
  color: #b91c1c;
}

.detail-name {
  font-size: 18px;
}

.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.chip,
.detail-list span {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(226, 232, 240, 0.56);
  color: #294256;
  font-size: 12px;
  font-weight: 600;
}

.detail-list {
  margin-top: 10px;
}

.signal-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.signal-card {
  cursor: pointer;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    border-color 0.18s ease;
}

.signal-card.active {
  transform: translateY(-2px);
  border-color: rgba(14, 165, 233, 0.28);
  box-shadow: 0 12px 28px rgba(14, 165, 233, 0.12);
}

.risk-node-list {
  display: grid;
  gap: 8px;
  margin-top: 10px;
}

.risk-node-item {
  padding: 12px;
  border-radius: 14px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  background: rgba(248, 251, 255, 0.84);
}

.risk-node-item strong,
.risk-node-item span {
  display: block;
}

.risk-node-item strong {
  color: #12202f;
  font-size: 13px;
}

.risk-node-item span {
  margin-top: 4px;
  color: #b91c1c;
  font-size: 12px;
  font-weight: 700;
}

.risk-node-item p {
  margin: 8px 0 0;
  color: #556d80;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 1280px) {
  .summary-grid,
  .signal-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .summary-grid,
  .signal-grid {
    grid-template-columns: 1fr;
  }

  .panel-head,
  .chart-meta {
    flex-direction: column;
  }
}
</style>
