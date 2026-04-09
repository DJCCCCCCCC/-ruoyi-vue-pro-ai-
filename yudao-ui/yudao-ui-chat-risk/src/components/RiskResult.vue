<template>
  <div class="risk-result">
    <section class="hero-panel">
      <div class="hero-header">
        <div>
          <span class="eyebrow">风险评估总览</span>
          <h3 class="hero-title">{{ levelMeta.title }}</h3>
        </div>
        <span :class="['risk-badge', `level-${levelKey}`]">{{ levelMeta.label }}</span>
      </div>

      <div class="score-row">
        <strong class="score-value">{{ normalizedScore }}</strong>
        <span class="score-unit">/ 100</span>
      </div>

      <div class="score-track" aria-hidden="true">
        <div class="score-bar" :class="`level-${levelKey}`" :style="{ width: `${normalizedScore}%` }"></div>
      </div>

      <div class="overview-grid">
        <div class="overview-card">
          <span class="overview-label">风险等级</span>
          <strong class="overview-value">{{ levelMeta.label }}</strong>
        </div>
        <div class="overview-card">
          <span class="overview-label">触发因子</span>
          <strong class="overview-value">{{ riskData.riskFactors?.length || 0 }} 项</strong>
        </div>
        <div class="overview-card" v-if="behaviorInfo">
          <span class="overview-label">行为加分</span>
          <strong class="overview-value">+{{ behaviorExtraScore }}</strong>
        </div>
        <div class="overview-card" v-if="whoisInfo">
          <span class="overview-label">Whois 加分</span>
          <strong class="overview-value">+{{ whoisExtraScore }}</strong>
        </div>
      </div>
    </section>

    <section class="module-card">
      <div class="module-head">
        <span class="section-title">综合结论</span>
      </div>
      <p class="analysis-text">{{ riskData.deepAnalysis }}</p>
    </section>

    <section v-if="riskData.riskFactors?.length" class="module-card">
      <div class="module-head">
        <span class="section-title">风险因子</span>
        <span class="section-meta">{{ riskData.riskFactors.length }} 项</span>
      </div>
      <div class="chip-list">
        <span v-for="factor in riskData.riskFactors" :key="factor" class="chip chip-neutral">
          {{ factor }}
        </span>
      </div>
    </section>

    <LlmRiskReportPanel v-if="riskData.llmReport" :report="riskData.llmReport" />

    <AdvancedRiskAnalysisPanel v-if="riskData.advancedAnalysis" :analysis="riskData.advancedAnalysis" />

    <RelationTopologyPanel v-if="riskData.topologyInfo" :topology="riskData.topologyInfo" />

    <section v-if="behaviorInfo" class="module-card module-behavior">
      <div class="module-head">
        <div>
          <span class="section-title">生物行为分析</span>
          <p class="module-summary">
            {{ behaviorInfo.summary || '已结合用户操作节奏、输入轨迹和设备行为完成分析。' }}
          </p>
        </div>
        <span :class="['chip', behaviorInfo.mocked ? 'chip-warn' : 'chip-safe']">
          {{ behaviorInfo.mocked ? '模拟画像' : '真实画像' }}
        </span>
      </div>

      <div class="subgrid">
        <div class="mini-card accent-warn">
          <span class="mini-label">行为加分</span>
          <strong class="mini-value">+{{ behaviorExtraScore }}</strong>
        </div>
        <div class="mini-card">
          <span class="mini-label">风险提示</span>
          <strong class="mini-value">{{ behaviorInfo.factors?.length || 0 }} 项</strong>
        </div>
      </div>

      <div v-if="behaviorInfo.factors?.length" class="chip-list">
        <span v-for="factor in behaviorInfo.factors" :key="factor" class="chip chip-warn">
          {{ factor }}
        </span>
      </div>

      <div v-if="behaviorMetrics.length" class="metrics-grid">
        <div v-for="metric in behaviorMetrics" :key="metric.label" class="metric-card">
          <span class="metric-label">{{ metric.label }}</span>
          <strong class="metric-value">{{ metric.value }}</strong>
        </div>
      </div>

      <details v-if="behaviorInfo.notes?.length || behaviorInfo.snapshot" class="detail-panel">
        <summary>查看行为分析明细</summary>
        <div v-if="behaviorInfo.notes?.length" class="detail-block">
          <p class="section-title">分析备注</p>
          <ul class="detail-list">
            <li v-for="note in behaviorInfo.notes" :key="note">{{ note }}</li>
          </ul>
        </div>
        <pre v-if="behaviorInfo.snapshot">{{ formatJSON(behaviorInfo.snapshot) }}</pre>
      </details>
    </section>

    <div v-if="riskData.ipInfo || whoisInfo" class="intel-grid">
      <section v-if="ipInfoRecord" class="module-card">
        <div class="module-head">
          <div>
            <span class="section-title">IP 情报</span>
            <p class="module-summary">地理位置、网络归属和匿名代理特征按可视化卡片展示。</p>
          </div>
          <span :class="['chip', ipRiskTone === 'warn' ? 'chip-warn' : ipRiskTone === 'safe' ? 'chip-safe' : 'chip-neutral']">
            {{ ipRiskLabel }}
          </span>
        </div>

        <div class="intel-risk-strip">
          <div class="intel-score-block">
            <span class="mini-label">情报风险分</span>
            <div class="intel-score-row">
              <strong class="intel-score-value">{{ ipIntelScore }}</strong>
              <span class="intel-score-unit">/ 100</span>
            </div>
          </div>
          <div class="intel-track-block">
            <div class="intel-track">
              <div :class="['intel-track-fill', `tone-${ipRiskTone}`]" :style="{ width: `${ipIntelScore}%` }"></div>
            </div>
            <div class="intel-lights" aria-hidden="true">
              <span
                v-for="light in intelLights"
                :key="`ip-${light.key}`"
                :class="['intel-light', `tone-${light.tone}`, { active: ipIntelActiveLights >= light.step }]"
              ></span>
            </div>
            <p class="intel-caption">{{ ipRiskLabel }}</p>
          </div>
        </div>

        <div class="subgrid intel-summary-grid">
          <div class="mini-card">
            <span class="mini-label">IP 地址</span>
            <strong class="mini-value ip-mini-value">{{ ipText || '-' }}</strong>
          </div>
          <div class="mini-card">
            <span class="mini-label">地理位置</span>
            <strong class="mini-value">{{ ipLocationText }}</strong>
          </div>
          <div class="mini-card">
            <span class="mini-label">网络归属</span>
            <strong class="mini-value">{{ ipOrgText }}</strong>
          </div>
          <div class="mini-card" :class="ipRiskTone === 'warn' ? 'accent-warn' : ''">
            <span class="mini-label">风险状态</span>
            <strong class="mini-value">{{ ipRiskLabel }}</strong>
          </div>
        </div>

        <div class="ip-visual-grid">
          <article class="ip-visual-card">
            <div class="module-head compact-head">
              <span class="section-title">位置画像</span>
            </div>
            <div class="metrics-grid compact-grid">
              <div class="metric-card">
                <span class="metric-label">国家 / 地区</span>
                <strong class="metric-value">{{ ipCountryText }}</strong>
              </div>
              <div class="metric-card">
                <span class="metric-label">城市</span>
                <strong class="metric-value">{{ ipCityText }}</strong>
              </div>
              <div class="metric-card">
                <span class="metric-label">经纬度</span>
                <strong class="metric-value">{{ ipLocText }}</strong>
              </div>
              <div class="metric-card">
                <span class="metric-label">时区</span>
                <strong class="metric-value">{{ ipTimezoneText }}</strong>
              </div>
            </div>
          </article>

          <article class="ip-visual-card">
            <div class="module-head compact-head">
              <span class="section-title">网络属性</span>
            </div>
            <div class="chip-list compact-top">
              <span v-if="ipOrgText !== '-'" class="chip chip-neutral">{{ ipOrgText }}</span>
              <span v-if="ipHostnameText !== '-'" class="chip chip-neutral">{{ ipHostnameText }}</span>
              <span v-if="ipPostalText !== '-'" class="chip chip-neutral">邮编 {{ ipPostalText }}</span>
            </div>

            <div class="chip-list compact-top">
              <span
                v-for="signal in ipRiskSignals"
                :key="signal.label"
                :class="['chip', signal.tone === 'warn' ? 'chip-warn' : signal.tone === 'safe' ? 'chip-safe' : 'chip-neutral']"
              >
                {{ signal.label }}
              </span>
            </div>
          </article>
        </div>

        <details class="detail-panel">
          <summary>查看 IP 原始明细</summary>
          <pre>{{ formatJSON(riskData.ipInfo) }}</pre>
        </details>
      </section>

      <section v-if="whoisInfo" class="module-card">
        <div class="module-head">
          <div>
            <span class="section-title">Whois 情报</span>
            <p class="module-summary">域名注册时间、注册商、隐私保护和新注册风险已可视化展示。</p>
          </div>
          <span :class="['chip', whoisRiskTone === 'warn' ? 'chip-warn' : whoisRiskTone === 'safe' ? 'chip-safe' : 'chip-neutral']">
            {{ whoisRiskLabel }}
          </span>
        </div>

        <div class="intel-risk-strip">
          <div class="intel-score-block">
            <span class="mini-label">情报风险分</span>
            <div class="intel-score-row">
              <strong class="intel-score-value">{{ whoisIntelScore }}</strong>
              <span class="intel-score-unit">/ 100</span>
            </div>
          </div>
          <div class="intel-track-block">
            <div class="intel-track">
              <div :class="['intel-track-fill', `tone-${whoisRiskTone}`]" :style="{ width: `${whoisIntelScore}%` }"></div>
            </div>
            <div class="intel-lights" aria-hidden="true">
              <span
                v-for="light in intelLights"
                :key="`whois-${light.key}`"
                :class="['intel-light', `tone-${light.tone}`, { active: whoisIntelActiveLights >= light.step }]"
              ></span>
            </div>
            <p class="intel-caption">{{ whoisRiskLabel }}</p>
          </div>
        </div>

        <div class="subgrid intel-summary-grid">
          <div class="mini-card">
            <span class="mini-label">检测域名</span>
            <strong class="mini-value">{{ whoisDomainCards.length }}</strong>
          </div>
          <div class="mini-card accent-warn">
            <span class="mini-label">新注册域名</span>
            <strong class="mini-value">{{ recentDomainCount }}</strong>
          </div>
          <div class="mini-card">
            <span class="mini-label">隐私保护</span>
            <strong class="mini-value">{{ privacyProtectedCount }}</strong>
          </div>
          <div class="mini-card">
            <span class="mini-label">高关注项</span>
            <strong class="mini-value">{{ suspiciousWhoisCount }}</strong>
          </div>
        </div>

        <div v-if="whoisInfo.factors?.length" class="chip-list compact-top">
          <span v-for="factor in whoisInfo.factors" :key="factor" class="chip chip-warn">
            {{ factor }}
          </span>
        </div>

        <div v-if="whoisDomainCards.length" class="whois-domain-grid">
          <article
            v-for="domainCard in whoisDomainCards"
            :key="domainCard.domain"
            class="whois-domain-card"
            :class="`tone-${domainCard.riskTone}`"
          >
            <div class="whois-domain-head">
              <div>
                <p class="whois-domain-name">{{ domainCard.domain }}</p>
                <p class="whois-domain-age">{{ domainCard.ageText }}</p>
              </div>
              <span :class="['chip', domainCard.riskTone === 'warn' ? 'chip-warn' : domainCard.riskTone === 'safe' ? 'chip-safe' : 'chip-neutral']">
                {{ domainCard.riskLabel }}
              </span>
            </div>

            <div class="whois-meta-grid">
              <div class="whois-meta-item">
                <span class="metric-label">注册时间</span>
                <strong class="metric-value">{{ domainCard.createdDate || '-' }}</strong>
              </div>
              <div class="whois-meta-item">
                <span class="metric-label">注册商</span>
                <strong class="metric-value">{{ domainCard.registrar || '-' }}</strong>
              </div>
              <div class="whois-meta-item">
                <span class="metric-label">注册主体</span>
                <strong class="metric-value">{{ domainCard.registrant || '-' }}</strong>
              </div>
              <div class="whois-meta-item">
                <span class="metric-label">隐私保护</span>
                <strong class="metric-value">{{ domainCard.privacyProtected ? '是' : '否' }}</strong>
              </div>
            </div>

            <div v-if="domainCard.nameServers.length" class="chip-list compact-top">
              <span v-for="server in domainCard.nameServers" :key="server" class="chip chip-neutral">
                {{ server }}
              </span>
            </div>

            <p v-if="domainCard.errorMessage" class="whois-error">{{ domainCard.errorMessage }}</p>
          </article>
        </div>

        <details class="detail-panel">
          <summary>查看 Whois 原始明细</summary>
          <pre>{{ formattedWhoisInfo }}</pre>
        </details>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import AdvancedRiskAnalysisPanel from '@/components/AdvancedRiskAnalysisPanel.vue'
import LlmRiskReportPanel from '@/components/LlmRiskReportPanel.vue'
import RelationTopologyPanel from '@/components/RelationTopologyPanel.vue'
import type { BehaviorInfo, PayRiskAssessRespVO, RiskLevel, WhoisInfo } from '@/types'
import { formatJSON } from '@/utils/parser'

interface Props {
  riskData: PayRiskAssessRespVO
}

const props = defineProps<Props>()

const metaMap: Record<RiskLevel, { label: string; title: string }> = {
  LOW: {
    label: '低风险',
    title: '本次支付表现稳定，建议正常放行。'
  },
  MEDIUM: {
    label: '中风险',
    title: '存在异常信号，建议二次校验。'
  },
  HIGH: {
    label: '高风险',
    title: '风险较高，建议人工复核或追加验证。'
  },
  CRITICAL: {
    label: '严重风险',
    title: '多项高危规则命中，建议立即拦截。'
  }
}

const normalizedScore = computed(() => Math.min(Math.max(Number(props.riskData.riskScore) || 0, 0), 100))
const levelKey = computed(() => props.riskData.riskLevel.toLowerCase())
const levelMeta = computed(() => metaMap[props.riskData.riskLevel] || metaMap.MEDIUM)
const behaviorInfo = computed<BehaviorInfo | null>(() => props.riskData.behaviorInfo || null)
const behaviorExtraScore = computed(() => Math.max(Number(behaviorInfo.value?.extraScore) || 0, 0))

const parseWhoisInfo = (value: string | undefined): WhoisInfo | null => {
  if (!value) {
    return null
  }

  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' ? (parsed as WhoisInfo) : null
  } catch {
    return null
  }
}

const whoisInfo = computed<WhoisInfo | null>(() => parseWhoisInfo(props.riskData.whoisInfo))
const whoisExtraScore = computed(() => Math.max(Number(whoisInfo.value?.extraScore) || 0, 0))
const formattedWhoisInfo = computed(() => {
  if (whoisInfo.value) {
    return formatJSON(whoisInfo.value)
  }
  return props.riskData.whoisInfo || ''
})

const asRecord = (value: unknown): Record<string, unknown> | null => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return null
  }
  return value as Record<string, unknown>
}

const pickValue = (source: unknown, path: string[]): unknown => {
  let current: unknown = source
  for (const key of path) {
    const record = asRecord(current)
    if (!record || !(key in record)) {
      return undefined
    }
    current = record[key]
  }
  return current
}

const toText = (value: unknown): string | undefined => {
  if (value === null || value === undefined) {
    return undefined
  }
  const text = String(value).trim()
  return text ? text : undefined
}

const toStringArray = (value: unknown): string[] => {
  if (!Array.isArray(value)) {
    return []
  }
  return value.map((item) => toText(item)).filter((item): item is string => Boolean(item))
}

const parseAgeDays = (dateText: string | undefined) => {
  if (!dateText) {
    return null
  }
  const normalized = dateText.slice(0, 10)
  const createdAt = new Date(normalized)
  if (Number.isNaN(createdAt.getTime())) {
    return null
  }
  const now = new Date()
  const diff = now.getTime() - createdAt.getTime()
  if (diff < 0) {
    return null
  }
  return Math.floor(diff / (1000 * 60 * 60 * 24))
}

const formatAgeText = (ageDays: number | null) => {
  if (ageDays === null) {
    return '域名年龄未知'
  }
  if (ageDays < 30) {
    return `注册 ${ageDays} 天，属于新注册域名`
  }
  if (ageDays < 365) {
    return `注册 ${ageDays} 天`
  }
  const years = Math.floor(ageDays / 365)
  const remainDays = ageDays % 365
  return remainDays > 0 ? `注册 ${years} 年 ${remainDays} 天` : `注册 ${years} 年`
}

const isPrivacyProtected = (registrant: string | undefined) => {
  if (!registrant) {
    return false
  }
  const lower = registrant.toLowerCase()
  return (
    lower.includes('privacy') ||
    lower.includes('proxy') ||
    lower.includes('redacted') ||
    lower.includes('whoisguard') ||
    lower.includes('withheld')
  )
}

const whoisDomainCards = computed(() => {
  return (whoisInfo.value?.records || [])
    .map((record) => {
      const payload = asRecord(record.payload)
      const whoisRecord = asRecord(pickValue(payload, ['WhoisRecord']))
      const registrant = asRecord(pickValue(whoisRecord, ['registrant']))
      const nameServers = asRecord(pickValue(whoisRecord, ['nameServers']))

      const createdDate =
        toText(pickValue(whoisRecord, ['createdDateNormalized'])) || toText(pickValue(whoisRecord, ['createdDate']))
      const registrar = toText(pickValue(whoisRecord, ['registrarName']))
      const registrantName =
        toText(pickValue(registrant, ['organization'])) || toText(pickValue(registrant, ['name']))
      const errorMessage = toText(pickValue(payload, ['ErrorMessage', 'msg']))
      const hostNames = toStringArray(pickValue(nameServers, ['hostNames'])).slice(0, 3)
      const ageDays = parseAgeDays(createdDate)
      const privacyProtected = isPrivacyProtected(registrantName)
      const isRecent = ageDays !== null && ageDays < 30
      const riskTone = errorMessage || isRecent || privacyProtected ? 'warn' : ageDays !== null && ageDays > 365 ? 'safe' : 'neutral'
      const riskLabel = errorMessage ? '需关注' : isRecent ? '新注册' : privacyProtected ? '隐私保护' : ageDays !== null && ageDays > 365 ? '较稳定' : '一般'

      return {
        domain: record.domain || '未知域名',
        createdDate,
        registrar,
        registrant: registrantName,
        privacyProtected,
        ageDays,
        ageText: formatAgeText(ageDays),
        riskTone,
        riskLabel,
        nameServers: hostNames,
        errorMessage
      }
    })
    .filter((card) => Boolean(card.domain))
})

const recentDomainCount = computed(() => whoisDomainCards.value.filter((card) => card.ageDays !== null && card.ageDays < 30).length)
const privacyProtectedCount = computed(() => whoisDomainCards.value.filter((card) => card.privacyProtected).length)
const suspiciousWhoisCount = computed(() => whoisDomainCards.value.filter((card) => card.riskTone === 'warn').length)

const clampScore = (value: number) => Math.max(0, Math.min(Math.round(value), 100))

const resolveIntelStatus = (
  score: number,
  labels: { safe: string; neutral: string; warn: string }
): { tone: 'warn' | 'safe' | 'neutral'; label: string; activeLights: number } => {
  if (score >= 60) {
    return { tone: 'warn', label: labels.warn, activeLights: 3 }
  }
  if (score >= 25) {
    return { tone: 'neutral', label: labels.neutral, activeLights: 2 }
  }
  return { tone: 'safe', label: labels.safe, activeLights: 1 }
}

const intelLights = [
  { key: 'low', step: 1, tone: 'safe' },
  { key: 'mid', step: 2, tone: 'neutral' },
  { key: 'high', step: 3, tone: 'warn' }
] as const

const ipInfoRecord = computed<Record<string, unknown> | null>(() => asRecord(props.riskData.ipInfo))
const ipPrivacyRecord = computed<Record<string, unknown> | null>(() => asRecord(ipInfoRecord.value?.privacy))
const ipCompanyRecord = computed<Record<string, unknown> | null>(() => asRecord(ipInfoRecord.value?.company))
const ipCarrierRecord = computed<Record<string, unknown> | null>(() => asRecord(ipInfoRecord.value?.carrier))

const ipText = computed(() => toText(ipInfoRecord.value?.ip) || toText(ipInfoRecord.value?.query) || '-')
const ipCityText = computed(() => toText(ipInfoRecord.value?.city) || '-')
const ipRegionText = computed(() => toText(ipInfoRecord.value?.region) || '-')
const ipCountryText = computed(() => {
  const country = toText(ipInfoRecord.value?.country)
  const region = ipRegionText.value
  if (!country && region === '-') {
    return '-'
  }
  return [country, region !== '-' ? region : undefined].filter(Boolean).join(' / ')
})
const ipLocationText = computed(() => {
  const parts = [ipCityText.value !== '-' ? ipCityText.value : undefined, ipCountryText.value !== '-' ? ipCountryText.value : undefined].filter(Boolean)
  return parts.length ? parts.join(' · ') : '-'
})
const ipLocText = computed(() => toText(ipInfoRecord.value?.loc) || '-')
const ipTimezoneText = computed(() => toText(ipInfoRecord.value?.timezone) || '-')
const ipPostalText = computed(() => toText(ipInfoRecord.value?.postal) || '-')
const ipHostnameText = computed(() => toText(ipInfoRecord.value?.hostname) || '-')
const ipOrgText = computed(
  () =>
    toText(ipCompanyRecord.value?.name) ||
    toText(ipCarrierRecord.value?.name) ||
    toText(ipInfoRecord.value?.org) ||
    '-'
)

const toBooleanFlag = (value: unknown) => {
  if (typeof value === 'boolean') {
    return value
  }
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase()
    return normalized === 'true' || normalized === '1' || normalized === 'yes'
  }
  if (typeof value === 'number') {
    return value === 1
  }
  return false
}

const ipRiskSignals = computed(() => {
  const signals: Array<{ label: string; tone: 'warn' | 'safe' | 'neutral' }> = []
  if (toBooleanFlag(ipInfoRecord.value?.bogon)) {
    signals.push({ label: '保留地址 / Bogon', tone: 'warn' })
  }
  if (toBooleanFlag(ipPrivacyRecord.value?.vpn)) {
    signals.push({ label: 'VPN', tone: 'warn' })
  }
  if (toBooleanFlag(ipPrivacyRecord.value?.proxy)) {
    signals.push({ label: '代理', tone: 'warn' })
  }
  if (toBooleanFlag(ipPrivacyRecord.value?.tor)) {
    signals.push({ label: 'Tor', tone: 'warn' })
  }
  if (toBooleanFlag(ipPrivacyRecord.value?.relay)) {
    signals.push({ label: 'Relay', tone: 'warn' })
  }
  if (toBooleanFlag(ipPrivacyRecord.value?.hosting)) {
    signals.push({ label: '机房 / Hosting', tone: 'warn' })
  }
  if (!signals.length && ipInfoRecord.value) {
    signals.push({ label: '未发现明显匿名网络特征', tone: 'safe' })
  }
  return signals
})

const ipIntelScore = computed(() => {
  if (!ipInfoRecord.value) {
    return 0
  }
  let score = 0
  if (toBooleanFlag(ipInfoRecord.value?.bogon)) score += 36
  if (toBooleanFlag(ipPrivacyRecord.value?.vpn)) score += 24
  if (toBooleanFlag(ipPrivacyRecord.value?.proxy)) score += 18
  if (toBooleanFlag(ipPrivacyRecord.value?.tor)) score += 26
  if (toBooleanFlag(ipPrivacyRecord.value?.relay)) score += 14
  if (toBooleanFlag(ipPrivacyRecord.value?.hosting)) score += 18
  return clampScore(score)
})

const ipIntelStatus = computed(() =>
  resolveIntelStatus(ipIntelScore.value, {
    safe: '网络环境较稳定',
    neutral: '存在可疑网络特征',
    warn: '高危网络画像'
  })
)

const ipRiskTone = computed<'warn' | 'safe' | 'neutral'>(() => ipIntelStatus.value.tone)
const ipRiskLabel = computed(() => ipIntelStatus.value.label)
const ipIntelActiveLights = computed(() => ipIntelStatus.value.activeLights)

const whoisIntelScore = computed(() => {
  if (!whoisInfo.value) {
    return 0
  }
  const base =
    whoisExtraScore.value * 6 +
    recentDomainCount.value * 24 +
    privacyProtectedCount.value * 14 +
    suspiciousWhoisCount.value * 18 +
    Math.max((whoisInfo.value.factors?.length || 0) - suspiciousWhoisCount.value, 0) * 6
  return clampScore(base)
})

const whoisIntelStatus = computed(() =>
  resolveIntelStatus(whoisIntelScore.value, {
    safe: '域名背景较稳定',
    neutral: '存在可疑注册特征',
    warn: '域名风险较高'
  })
)

const whoisRiskTone = computed<'warn' | 'safe' | 'neutral'>(() => whoisIntelStatus.value.tone)
const whoisRiskLabel = computed(() => whoisIntelStatus.value.label)
const whoisIntelActiveLights = computed(() => whoisIntelStatus.value.activeLights)

const formatMetricValue = (value: unknown) => {
  if (typeof value === 'boolean') {
    return value ? '是' : '否'
  }
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return String(value)
}

const behaviorMetrics = computed(() => {
  const snapshot = behaviorInfo.value?.snapshot
  if (!snapshot || typeof snapshot !== 'object') {
    return []
  }

  const metricMap: Array<{ key: string; label: string }> = [
    { key: 'operationSpeed', label: '操作速度' },
    { key: 'cardNumberInputDurationMs', label: '敏感字段输入耗时(ms)' },
    { key: 'averageKeyIntervalMs', label: '按键平均间隔(ms)' },
    { key: 'keyIntervalStdMs', label: '按键波动标准差(ms)' },
    { key: 'mouseStraightness', label: '鼠标轨迹笔直度' },
    { key: 'pointerJumpCount', label: '指针跳点次数' },
    { key: 'mouseTrajectoryType', label: '轨迹类型' },
    { key: 'pasteDetected', label: '是否粘贴输入' },
    { key: 'emulatorDetected', label: '是否模拟器' },
    { key: 'scriptHint', label: '是否脚本提示' }
  ]

  return metricMap
    .filter((item) => item.key in snapshot)
    .map((item) => ({
      label: item.label,
      value: formatMetricValue((snapshot as Record<string, unknown>)[item.key])
    }))
})
</script>

<style scoped>
.risk-result {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.hero-panel,
.module-card {
  padding: 16px;
  border-radius: 20px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.98));
  border: 1px solid rgba(148, 163, 184, 0.18);
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.06);
}

.hero-panel {
  background: linear-gradient(180deg, rgba(255, 252, 248, 0.98), rgba(255, 255, 255, 0.98));
}

.hero-header,
.module-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
}

.eyebrow,
.section-title,
.mini-label,
.overview-label,
.metric-label {
  font-size: 12px;
  color: var(--text-muted);
}

.hero-title {
  margin: 6px 0 0;
  font-size: 18px;
  color: var(--text-primary);
}

.score-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: 14px;
}

.score-value {
  font-size: 42px;
  line-height: 1;
  color: var(--text-primary);
}

.score-unit {
  font-size: 13px;
  color: var(--text-muted);
}

.risk-badge,
.chip {
  padding: 8px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.score-track {
  margin-top: 14px;
  height: 10px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.16);
  overflow: hidden;
}

.score-bar {
  height: 100%;
  border-radius: inherit;
  transition: width 0.28s ease;
}

.overview-grid,
.subgrid,
.metrics-grid,
.intel-grid {
  display: grid;
  gap: 10px;
}

.overview-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-top: 14px;
}

.overview-card,
.mini-card,
.metric-card {
  padding: 12px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.84);
  border: 1px solid rgba(148, 163, 184, 0.12);
}

.overview-value,
.mini-value,
.metric-value {
  display: block;
  margin-top: 6px;
  color: var(--text-primary);
}

.overview-value {
  font-size: 16px;
}

.mini-value {
  font-size: 24px;
}

.mini-card.accent-warn .mini-value {
  color: #c2410c;
}

.analysis-text,
.module-summary {
  margin: 10px 0 0;
  line-height: 1.75;
  color: var(--text-secondary);
  font-size: 14px;
  white-space: pre-wrap;
}

.section-meta {
  font-size: 12px;
  color: var(--text-muted);
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.chip-list.compact-top {
  margin-top: 10px;
}

.chip-neutral {
  background: rgba(15, 23, 42, 0.06);
  color: var(--text-secondary);
}

.chip-warn {
  background: rgba(249, 115, 22, 0.12);
  color: #9a3412;
}

.chip-safe {
  background: rgba(34, 197, 94, 0.14);
  color: #15803d;
}

.module-behavior {
  background: linear-gradient(180deg, rgba(255, 247, 237, 0.88), rgba(255, 255, 255, 0.98));
}

.subgrid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 14px;
}

.metrics-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 14px;
}

.intel-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.intel-summary-grid {
  margin-top: 14px;
}

.intel-risk-strip {
  margin-top: 14px;
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 12px;
}

.intel-score-block,
.intel-track-block {
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.84);
  border: 1px solid rgba(148, 163, 184, 0.12);
}

.intel-score-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: 6px;
}

.intel-score-value {
  font-size: 30px;
  line-height: 1;
  color: var(--text-primary);
}

.intel-score-unit {
  font-size: 12px;
  color: var(--text-muted);
}

.intel-track {
  height: 10px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.16);
  overflow: hidden;
}

.intel-track-fill {
  height: 100%;
  border-radius: inherit;
  transition: width 0.28s ease;
}

.intel-track-fill.tone-safe {
  background: linear-gradient(90deg, #22c55e, #16a34a);
}

.intel-track-fill.tone-neutral {
  background: linear-gradient(90deg, #fbbf24, #f97316);
}

.intel-track-fill.tone-warn {
  background: linear-gradient(90deg, #f87171, #dc2626);
}

.intel-lights {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.intel-light {
  width: 12px;
  height: 12px;
  border-radius: 999px;
  opacity: 0.22;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.08);
}

.intel-light.active {
  opacity: 1;
  box-shadow: 0 0 0 4px rgba(255, 255, 255, 0.72);
}

.intel-light.tone-safe {
  background: #22c55e;
}

.intel-light.tone-neutral {
  background: #f59e0b;
}

.intel-light.tone-warn {
  background: #ef4444;
}

.intel-caption {
  margin: 10px 0 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.ip-mini-value {
  font-size: 20px;
}

.ip-visual-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.ip-visual-card {
  padding: 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.84);
  border: 1px solid rgba(148, 163, 184, 0.12);
}

.compact-head {
  gap: 8px;
}

.compact-grid {
  margin-top: 12px;
}

.whois-domain-grid {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

.whois-domain-card {
  padding: 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.84);
  border: 1px solid rgba(148, 163, 184, 0.12);
}

.whois-domain-card.tone-warn {
  background: linear-gradient(180deg, rgba(255, 247, 237, 0.92), rgba(255, 255, 255, 0.94));
  border-color: rgba(249, 115, 22, 0.22);
}

.whois-domain-card.tone-safe {
  background: linear-gradient(180deg, rgba(240, 253, 244, 0.92), rgba(255, 255, 255, 0.94));
  border-color: rgba(34, 197, 94, 0.2);
}

.whois-domain-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
}

.whois-domain-name {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
}

.whois-domain-age {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.whois-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.whois-meta-item {
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.04);
}

.whois-error {
  margin: 12px 0 0;
  color: #b45309;
  font-size: 13px;
  line-height: 1.7;
}

.detail-panel {
  margin-top: 14px;
}

.detail-panel summary {
  cursor: pointer;
  color: var(--text-secondary);
  font-size: 13px;
}

.detail-block {
  margin-top: 12px;
}

.detail-list {
  margin: 8px 0 0;
  padding-left: 18px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

pre {
  margin: 12px 0 0;
  padding: 12px;
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.06);
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.6;
  overflow-x: auto;
}

.level-low {
  background: rgba(34, 197, 94, 0.14);
  color: #15803d;
}

.score-bar.level-low {
  background: linear-gradient(90deg, #22c55e, #16a34a);
}

.level-medium {
  background: rgba(249, 115, 22, 0.14);
  color: #c2410c;
}

.score-bar.level-medium {
  background: linear-gradient(90deg, #fb923c, #f97316);
}

.level-high {
  background: rgba(239, 68, 68, 0.14);
  color: #dc2626;
}

.score-bar.level-high {
  background: linear-gradient(90deg, #f87171, #ef4444);
}

.level-critical {
  background: rgba(127, 29, 29, 0.14);
  color: #991b1b;
}

.score-bar.level-critical {
  background: linear-gradient(90deg, #dc2626, #7f1d1d);
}

@media (max-width: 640px) {
  .hero-header,
  .module-head {
    flex-direction: column;
  }

  .overview-grid,
  .subgrid,
  .metrics-grid,
  .intel-grid,
  .intel-risk-strip,
  .whois-meta-grid,
  .ip-visual-grid {
    grid-template-columns: 1fr;
  }
}
</style>
