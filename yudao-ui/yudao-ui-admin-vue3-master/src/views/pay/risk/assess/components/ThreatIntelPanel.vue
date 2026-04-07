<template>
  <section v-if="hasIntel" class="intel-panel">
    <div class="panel-head">
      <div>
        <p class="panel-kicker">Threat Intel</p>
        <h3>情报面板</h3>
        <p class="panel-desc">IP 情报和 Whois 安全检测按同一套风险语言可视化展示。</p>
      </div>
    </div>

    <div class="intel-grid">
      <article v-if="ipInfoRecord" class="module-card">
        <div class="module-head">
          <div>
            <span class="section-title">IP 情报</span>
            <p class="module-summary">结合地理位置、网络归属和匿名代理特征，快速判断网络环境是否可疑。</p>
          </div>
          <span :class="['status-pill', `tone-${ipRiskTone}`]">{{ ipRiskLabel }}</span>
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

        <div class="summary-grid">
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

        <div class="detail-grid">
          <div class="visual-card">
            <div class="compact-head">
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
          </div>

          <div class="visual-card">
            <div class="compact-head">
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
          </div>
        </div>

        <details class="detail-panel">
          <summary>查看 IP 原始明细</summary>
          <pre>{{ formatJson(props.ipInfo) }}</pre>
        </details>
      </article>

      <article v-if="showWhoisModule" class="module-card">
        <div class="module-head">
          <div>
            <span class="section-title">Whois 情报</span>
            <p class="module-summary">聚合域名注册时间、注册主体、隐私保护和新注册信号，辅助识别钓鱼或一次性域名。</p>
          </div>
          <span :class="['status-pill', `tone-${whoisRiskTone}`]">{{ whoisRiskLabel }}</span>
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

        <div v-if="!whoisInfoRecord && fallbackWhoisDomains.length" class="fallback-note">
          当前结果未返回 Whois 原始记录，以下域名由输入内容自动提取并先行展示。
        </div>

        <div class="summary-grid">
          <div class="mini-card">
            <span class="mini-label">检测域名</span>
            <strong class="mini-value">{{ resolvedWhoisDomainCards.length }}</strong>
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

        <div v-if="whoisFactorChips.length" class="chip-list compact-top">
          <span v-for="factor in whoisFactorChips" :key="factor" class="chip chip-warn">
            {{ factor }}
          </span>
        </div>

        <div v-if="resolvedWhoisDomainCards.length" class="domain-grid">
          <article
            v-for="domainCard in resolvedWhoisDomainCards"
            :key="domainCard.domain"
            class="domain-card"
            :class="`tone-${domainCard.riskTone}`"
          >
            <div class="domain-head">
              <div>
                <p class="domain-name">{{ domainCard.domain }}</p>
                <p class="domain-age">{{ domainCard.ageText }}</p>
              </div>
              <span :class="['chip', domainCard.riskTone === 'warn' ? 'chip-warn' : domainCard.riskTone === 'safe' ? 'chip-safe' : 'chip-neutral']">
                {{ domainCard.riskLabel }}
              </span>
            </div>

            <div class="meta-grid">
              <div class="meta-item">
                <span class="metric-label">注册时间</span>
                <strong class="metric-value">{{ domainCard.createdDate || '-' }}</strong>
              </div>
              <div class="meta-item">
                <span class="metric-label">注册商</span>
                <strong class="metric-value">{{ domainCard.registrar || '-' }}</strong>
              </div>
              <div class="meta-item">
                <span class="metric-label">注册主体</span>
                <strong class="metric-value">{{ domainCard.registrant || '-' }}</strong>
              </div>
              <div class="meta-item">
                <span class="metric-label">隐私保护</span>
                <strong class="metric-value">{{ domainCard.privacyProtected ? '是' : '否' }}</strong>
              </div>
            </div>

            <div v-if="domainCard.nameServers.length" class="chip-list compact-top">
              <span v-for="server in domainCard.nameServers" :key="server" class="chip chip-neutral">
                {{ server }}
              </span>
            </div>

            <p v-if="domainCard.errorMessage" class="domain-error">{{ domainCard.errorMessage }}</p>
          </article>
        </div>

        <details class="detail-panel">
          <summary>查看 Whois 原始明细</summary>
          <pre>{{ formatJson(whoisRawDetail) }}</pre>
        </details>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface WhoisInfoRecord {
  extraScore?: number
  factors?: string[]
  records?: Array<{ domain?: string; payload?: unknown }>
}

interface Props {
  ipInfo?: unknown
  whoisInfo?: unknown
  paymentData?: unknown
}

const props = defineProps<Props>()

const asRecord = (value: unknown): Record<string, any> | null => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return null
  }
  return value as Record<string, any>
}

const parseJsonMaybe = (value: unknown) => {
  if (typeof value !== 'string') {
    return value
  }
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
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
  return text || undefined
}

const toStringArray = (value: unknown): string[] => {
  if (!Array.isArray(value)) {
    return []
  }
  return value.map((item) => toText(item)).filter((item): item is string => Boolean(item))
}

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

const parseAgeDays = (dateText: string | undefined) => {
  if (!dateText) {
    return null
  }
  const normalized = dateText.slice(0, 10)
  const createdAt = new Date(normalized)
  if (Number.isNaN(createdAt.getTime())) {
    return null
  }
  const diff = Date.now() - createdAt.getTime()
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

const formatJson = (value: unknown) => {
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value ?? '')
  }
}

const intelLights = [
  { key: 'low', step: 1, tone: 'safe' },
  { key: 'mid', step: 2, tone: 'neutral' },
  { key: 'high', step: 3, tone: 'warn' }
] as const

const paymentDataRecord = computed<Record<string, any> | null>(() => asRecord(parseJsonMaybe(props.paymentData)))

const ipInfoRecord = computed<Record<string, any> | null>(() => asRecord(parseJsonMaybe(props.ipInfo)))
const ipPrivacyRecord = computed<Record<string, any> | null>(() => asRecord(ipInfoRecord.value?.privacy))
const ipCompanyRecord = computed<Record<string, any> | null>(() => asRecord(ipInfoRecord.value?.company))
const ipCarrierRecord = computed<Record<string, any> | null>(() => asRecord(ipInfoRecord.value?.carrier))

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
  const parts = [
    ipCityText.value !== '-' ? ipCityText.value : undefined,
    ipCountryText.value !== '-' ? ipCountryText.value : undefined
  ].filter(Boolean)
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

const ipRiskSignals = computed(() => {
  const signals: Array<{ label: string; tone: 'warn' | 'safe' | 'neutral' }> = []
  if (toBooleanFlag(ipInfoRecord.value?.bogon)) signals.push({ label: '保留地址 / Bogon', tone: 'warn' })
  if (toBooleanFlag(ipPrivacyRecord.value?.vpn)) signals.push({ label: 'VPN', tone: 'warn' })
  if (toBooleanFlag(ipPrivacyRecord.value?.proxy)) signals.push({ label: '代理', tone: 'warn' })
  if (toBooleanFlag(ipPrivacyRecord.value?.tor)) signals.push({ label: 'Tor', tone: 'warn' })
  if (toBooleanFlag(ipPrivacyRecord.value?.relay)) signals.push({ label: 'Relay', tone: 'warn' })
  if (toBooleanFlag(ipPrivacyRecord.value?.hosting)) signals.push({ label: '机房 / Hosting', tone: 'warn' })
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

const whoisInfoRecord = computed<WhoisInfoRecord | null>(() => {
  const parsed = parseJsonMaybe(props.whoisInfo)
  return asRecord(parsed) as WhoisInfoRecord | null
})

const urlPattern = /https?:\/\/[^\s]+/gi

const extractDomainFromUrl = (value: string) => {
  try {
    return new URL(value).hostname.replace(/^www\./i, '')
  } catch {
    return ''
  }
}

const fallbackWhoisDomains = computed(() => {
  const links = toStringArray(paymentDataRecord.value?.links)
  const messageLinks = Array.isArray(paymentDataRecord.value?.messages)
    ? paymentDataRecord.value.messages.flatMap((item: any) => {
        const content = toText(asRecord(item)?.content)
        return content ? content.match(urlPattern) || [] : []
      })
    : []
  const latestPeerLinks = (toText(paymentDataRecord.value?.latestPeerMessage) || '').match(urlPattern) || []
  const domains = [...links, ...messageLinks, ...latestPeerLinks]
    .map((item) => extractDomainFromUrl(item))
    .filter(Boolean)
  return Array.from(new Set(domains))
})

const fallbackDomainRiskTone = (domain: string): 'warn' | 'neutral' => {
  const normalized = domain.toLowerCase()
  return /(refund|verify|secure|wallet|pay|bonus|gift|support)/.test(normalized) ? 'warn' : 'neutral'
}

const whoisDomainCards = computed(() => {
  return (whoisInfoRecord.value?.records || [])
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
      const riskTone =
        errorMessage || isRecent || privacyProtected
          ? 'warn'
          : ageDays !== null && ageDays > 365
            ? 'safe'
            : 'neutral'
      const riskLabel = errorMessage
        ? '需关注'
        : isRecent
          ? '新注册'
          : privacyProtected
            ? '隐私保护'
            : ageDays !== null && ageDays > 365
              ? '较稳定'
              : '一般'

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

const fallbackWhoisDomainCards = computed(() =>
  fallbackWhoisDomains.value.map((domain) => {
    const riskTone = fallbackDomainRiskTone(domain)
    return {
      domain,
      createdDate: '',
      registrar: '',
      registrant: '',
      privacyProtected: false,
      ageDays: null,
      ageText: '等待 Whois 数据补齐',
      riskTone,
      riskLabel: riskTone === 'warn' ? '待重点核验' : '待查询',
      nameServers: [],
      errorMessage: '当前面板根据输入链接先行提取域名，后端尚未返回完整 Whois 记录。'
    }
  })
)

const resolvedWhoisDomainCards = computed(() =>
  whoisDomainCards.value.length ? whoisDomainCards.value : fallbackWhoisDomainCards.value
)

const recentDomainCount = computed(
  () => resolvedWhoisDomainCards.value.filter((card) => card.ageDays !== null && card.ageDays < 30).length
)
const privacyProtectedCount = computed(() =>
  resolvedWhoisDomainCards.value.filter((card) => card.privacyProtected).length
)
const suspiciousWhoisCount = computed(() =>
  resolvedWhoisDomainCards.value.filter((card) => card.riskTone === 'warn').length
)
const whoisExtraScore = computed(() => Math.max(Number(whoisInfoRecord.value?.extraScore) || 0, 0))
const whoisFactorChips = computed(() => {
  if (whoisInfoRecord.value?.factors?.length) {
    return whoisInfoRecord.value.factors
  }
  if (fallbackWhoisDomains.value.length) {
    return ['已从输入内容提取域名', '等待后端补充 Whois 明细']
  }
  return []
})

const whoisIntelScore = computed(() => {
  if (!whoisInfoRecord.value && !fallbackWhoisDomains.value.length) {
    return 0
  }
  const base =
    whoisExtraScore.value * 6 +
    recentDomainCount.value * 24 +
    privacyProtectedCount.value * 14 +
    suspiciousWhoisCount.value * 18 +
    Math.max((whoisInfoRecord.value?.factors?.length || 0) - suspiciousWhoisCount.value, 0) * 6
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
const showWhoisModule = computed(() => Boolean(whoisInfoRecord.value || fallbackWhoisDomains.value.length))
const whoisRawDetail = computed(() =>
  whoisInfoRecord.value || {
    extractedDomains: fallbackWhoisDomains.value,
    note: '当前结果未返回 whoisInfo，前端根据输入内容提取了域名作为占位展示。'
  }
)

const hasIntel = computed(() => Boolean(ipInfoRecord.value || showWhoisModule.value))
</script>

<style scoped>
.intel-panel {
  border-radius: 18px;
  padding: 20px;
  background:
    radial-gradient(circle at top left, rgba(14, 165, 233, 0.12), transparent 34%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(245, 249, 255, 0.98));
  border: 1px solid #d8e4ef;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.panel-kicker,
.section-title,
.mini-label,
.metric-label {
  margin: 0;
  font-size: 12px;
  color: #637c91;
}

.panel-head h3 {
  margin: 4px 0 0;
  font-size: 20px;
  color: #12202f;
}

.panel-desc,
.module-summary,
.intel-caption {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: #4b6477;
}

.fallback-note {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(245, 158, 11, 0.1);
  color: #b45309;
  font-size: 13px;
  line-height: 1.7;
}

.intel-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.module-card {
  padding: 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(148, 163, 184, 0.16);
  box-shadow: 0 10px 26px rgba(36, 58, 88, 0.06);
}

.module-head,
.compact-head,
.domain-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.status-pill,
.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.status-pill.tone-safe,
.chip-safe {
  background: rgba(34, 197, 94, 0.14);
  color: #15803d;
}

.status-pill.tone-neutral {
  background: rgba(245, 158, 11, 0.16);
  color: #b45309;
}

.status-pill.tone-warn,
.chip-warn {
  background: rgba(239, 68, 68, 0.14);
  color: #b91c1c;
}

.chip-neutral {
  background: rgba(15, 23, 42, 0.06);
  color: #38546a;
}

.intel-risk-strip {
  margin-top: 14px;
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 12px;
}

.intel-score-block,
.intel-track-block,
.mini-card,
.metric-card,
.visual-card,
.meta-item {
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.88);
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
  color: #12202f;
}

.intel-score-unit {
  font-size: 12px;
  color: #637c91;
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

.summary-grid,
.detail-grid,
.metrics-grid,
.meta-grid {
  display: grid;
  gap: 10px;
}

.summary-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-top: 14px;
}

.detail-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 14px;
}

.metrics-grid,
.meta-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 12px;
}

.mini-value,
.metric-value {
  display: block;
  margin-top: 6px;
  color: #12202f;
}

.mini-value {
  font-size: 22px;
}

.ip-mini-value {
  font-size: 18px;
}

.accent-warn .mini-value {
  color: #c2410c;
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.compact-top {
  margin-top: 10px;
}

.domain-grid {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

.domain-card {
  padding: 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(148, 163, 184, 0.12);
}

.domain-card.tone-warn {
  background: linear-gradient(180deg, rgba(255, 247, 237, 0.92), rgba(255, 255, 255, 0.96));
  border-color: rgba(249, 115, 22, 0.22);
}

.domain-card.tone-safe {
  background: linear-gradient(180deg, rgba(240, 253, 244, 0.92), rgba(255, 255, 255, 0.96));
  border-color: rgba(34, 197, 94, 0.2);
}

.domain-name {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #12202f;
}

.domain-age {
  margin: 6px 0 0;
  font-size: 13px;
  color: #4b6477;
}

.domain-error {
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
  font-size: 13px;
  color: #4b6477;
}

pre {
  margin: 12px 0 0;
  padding: 12px;
  border-radius: 14px;
  background: #0f172a;
  color: #dbeafe;
  font-size: 12px;
  line-height: 1.6;
  overflow-x: auto;
  white-space: pre-wrap;
}

@media (max-width: 1280px) {
  .intel-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .intel-risk-strip,
  .summary-grid,
  .detail-grid,
  .metrics-grid,
  .meta-grid {
    grid-template-columns: 1fr;
  }
}
</style>
