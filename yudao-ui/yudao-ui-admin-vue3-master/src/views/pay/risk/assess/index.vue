<template>
  <div class="risk-assess-page">
    <section class="hero">
      <div class="hero-left">
        <p class="hero-kicker">支付风控工作台</p>
        <h1>风险评估中心</h1>
        <p class="hero-desc">
          聚焦结果查看与审计追踪。支持从历史记录快速回看评分、因子、情报与拓扑分析。
        </p>

      </div>
    </section>

    <section class="overview-grid">
      <ContentWrap class="panel metric-card">
        <span>风险评分</span>
        <strong>{{ riskScore }}<small>/100</small></strong>
        <el-progress :percentage="riskScore" :show-text="false" :stroke-width="10" />
      </ContentWrap>

      <ContentWrap class="panel metric-card">
        <span>风险等级</span>
        <el-tag class="risk-level-tag" :type="riskTagType(selectedResult?.riskLevel)" size="large">
          {{ selectedResult?.riskLevel || '未选择' }}
        </el-tag>
      </ContentWrap>

      <ContentWrap class="panel metric-card">
        <span>命中因子</span>
        <strong>{{ riskFactorsCount }}</strong>
      </ContentWrap>

      <ContentWrap class="panel metric-card">
        <span>历史案例加分</span>
        <strong>+{{ caseSimilarityBonus }}</strong>
      </ContentWrap>
    </section>

    <section class="dashboard-grid">
      <ContentWrap class="panel chart-card">
        <div class="panel-head">
          <div>
            <h3>风控等级分布</h3>
            <p>基于当前记录统计各风险等级占比</p>
          </div>
        </div>
        <div ref="riskLevelPieRef" class="chart-canvas"></div>
      </ContentWrap>

      <ContentWrap class="panel chart-card geo-panel">
        <div class="panel-head">
          <div>
            <h3>全球风险图（IP归属）</h3>
            <p>按经纬度聚合风险来源，体现地理位置风控</p>
          </div>
        </div>
        <div ref="globalRiskMapRef" class="chart-canvas geo-canvas"></div>
      </ContentWrap>

      <ContentWrap class="panel chart-card trend-panel">
        <div class="panel-head">
          <div>
            <h3>24小时实时拦截趋势</h3>
            <p>按小时统计高风险拦截事件（HIGH / CRITICAL）</p>
          </div>
        </div>
        <div ref="interceptTrendRef" class="chart-canvas"></div>
      </ContentWrap>
    </section>

    <section v-if="selectedResult" class="detail-stack">
      <LlmRiskReportPanel :report="selectedResult?.llmReport" />
      <ContentWrap v-if="selectedResult.caseSimilarityBonus" class="panel metric-inline">
        <span>历史案例加分</span>
        <strong>+{{ selectedResult.caseSimilarityBonus }}</strong>
      </ContentWrap>
      <AdvancedRiskAnalysisPanel :analysis="selectedResult?.advancedAnalysis" />

      <ThreatIntelPanel
        :ip-info="selectedResult?.ipInfo"
        :whois-info="selectedResult?.whoisInfo"
        :payment-data="selectedPaymentObject"
      />

      <PaymentTopologyPanel :topology="selectedResult?.topologyInfo" :payment-data="selectedPaymentObject" />

      <ContentWrap class="panel json-panel">
        <div class="panel-head">
          <h3>JSON 对照</h3>
          <p>用于回放风险分析全过程</p>
        </div>
        <div class="json-grid">
          <div class="json-card">
            <h4>分析结果</h4>
            <pre class="json-block">{{ selectedResultJson }}</pre>
          </div>
          <div class="json-card">
            <h4>输入数据</h4>
            <pre class="json-block">{{ selectedPaymentJson }}</pre>
          </div>
        </div>
      </ContentWrap>
    </section>

    <ContentWrap class="panel table-panel">
      <div class="panel-head table-head">
        <div>
          <h3>分析记录</h3>
          <p>点击“查看”即可加载详情面板</p>
        </div>
        <div class="table-actions">
          <el-button type="primary" :loading="recordLoading" @click="refreshRecordList">刷新记录</el-button>
          <el-button
            type="danger"
            plain
            :loading="clearing"
            :disabled="recordTotal === 0"
            @click="handleClearRecords"
          >
            一键清空
          </el-button>
        </div>
      </div>

      <el-table v-loading="recordLoading" :data="recordList" :stripe="true" size="small">
        <el-table-column label="序号" width="90">
          <template #default="scope">
            {{ recordTotal - ((recordQuery.pageNo - 1) * recordQuery.pageSize + scope.$index) }}
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="170" :formatter="dateFormatter" />
        <el-table-column label="场景" prop="scene" min-width="150" />
        <el-table-column label="来源" prop="source" min-width="150" />
        <el-table-column label="IP" prop="ip" width="130" />
        <el-table-column label="等级" prop="riskLevel" width="100">
          <template #default="scope">
            <el-tag :type="riskTagType(scope.row.riskLevel)">{{ scope.row.riskLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="分数" prop="riskScore" width="90" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="handleUseRecord(scope.row)">查看</el-button>
            <el-button link type="danger" @click="handleDeleteRecord(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <Pagination
        :total="recordTotal"
        v-model:page="recordQuery.pageNo"
        v-model:limit="recordQuery.pageSize"
        @pagination="getRecordList"
      />
    </ContentWrap>
  </div>
</template>

<script lang="ts" setup>
import * as echarts from 'echarts'
import worldGeoJson from '@/assets/world.geo.json'
import { computed, nextTick, onActivated, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { dateFormatter } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import AdvancedRiskAnalysisPanel from './components/AdvancedRiskAnalysisPanel.vue'
import LlmRiskReportPanel from './components/LlmRiskReportPanel.vue'
import PaymentTopologyPanel from './components/PaymentTopologyPanel.vue'
import ThreatIntelPanel from './components/ThreatIntelPanel.vue'
import {
  clearPayRiskAssessRecords,
  deletePayRiskAssessRecord,
  getPayRiskAssessRecordPage,
  type PayRiskAssessRecordVO,
  type PayRiskAssessRespVO
} from '@/api/pay/risk/assess'

defineOptions({ name: 'PayRiskAssess' })

echarts.registerMap('world', worldGeoJson as any)

const message = useMessage()
const recordLoading = ref(false)
const clearing = ref(false)
const recordTotal = ref(0)
const recordList = ref<PayRiskAssessRecordVO[]>([])
const selectedRecord = ref<PayRiskAssessRecordVO | null>(null)

const riskLevelPieRef = ref<HTMLElement>()
const interceptTrendRef = ref<HTMLElement>()
const globalRiskMapRef = ref<HTMLElement>()

let riskLevelPieChart: echarts.ECharts | null = null
let interceptTrendChart: echarts.ECharts | null = null
let globalRiskMapChart: echarts.ECharts | null = null

const recordQuery = reactive({
  pageNo: 1,
  pageSize: 10
})

const parseJsonText = (jsonText?: string, fallback: any = {}) => {
  if (!jsonText) return fallback
  try {
    return JSON.parse(jsonText)
  } catch {
    return fallback
  }
}

const unwrapRecordPage = (data: any): { list: PayRiskAssessRecordVO[]; total: number } => {
  if (data?.list !== undefined && data?.total !== undefined) {
    return { list: data.list || [], total: Number(data.total || 0) }
  }
  if (data?.data?.list !== undefined && data?.data?.total !== undefined) {
    return { list: data.data.list || [], total: Number(data.data.total || 0) }
  }
  return { list: [], total: 0 }
}

const selectedResult = computed<PayRiskAssessRespVO | null>(() => {
  if (!selectedRecord.value) return null
  const record = selectedRecord.value
  return {
    riskScore: record.riskScore,
    riskLevel: record.riskLevel,
    deepAnalysis: record.deepAnalysis || '',
    riskFactors: parseJsonText(record.riskFactorsJson, []),
    ipInfo: parseJsonText(record.ipInfoJson, {}),
    whoisInfo: record.whoisInfoJson || undefined,
    behaviorInfo: parseJsonText(record.behaviorInfoJson, undefined),
    topologyInfo: parseJsonText(record.topologyInfoJson, undefined),
    llmReport: parseJsonText(record.llmReportJson, undefined),
    advancedAnalysis: parseJsonText(record.advancedAnalysisJson, undefined)
  }
})

const selectedPaymentObject = computed(() => parseJsonText(selectedRecord.value?.paymentDataJson, {}))
const selectedPaymentJson = computed(() => JSON.stringify(selectedPaymentObject.value, null, 2))
const selectedResultJson = computed(() => JSON.stringify(selectedResult.value, null, 2))

const riskScore = computed(() => Math.min(Math.max(Number(selectedResult.value?.riskScore || 0), 0), 100))
const riskFactorsCount = computed(() => selectedResult.value?.riskFactors?.length || 0)
const caseSimilarityBonus = computed(() => {
  const directBonus = Number((selectedResult.value as any)?.caseSimilarityBonus || 0)
  if (directBonus > 0) return Math.min(20, directBonus)
  const matches = selectedResult.value?.advancedAnalysis?.caseMatches || []
  return Math.min(20, matches.reduce((sum, item) => sum + Number(item.bonusScore || 0), 0))
})

const ipGeoCache = new Map<string, { lng: number; lat: number; name?: string; countryCode?: string }>()
const ipGeoPending = new Map<string, Promise<{ lng: number; lat: number; name?: string; countryCode?: string } | null>>()

const normalizeIp = (ip?: string) => (ip || '').trim()
const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))
const countryCenterMap: Record<string, [number, number]> = {
  CN: [104.1954, 35.8617],
  US: [-98.5795, 39.8283],
  SG: [103.8198, 1.3521],
  JP: [138.2529, 36.2048],
  GB: [-3.436, 55.3781],
  DE: [10.4515, 51.1657],
  FR: [2.2137, 46.2276],
  IN: [78.9629, 20.5937],
  RU: [105.3188, 61.524],
  BR: [-51.9253, -14.235],
  AU: [133.7751, -25.2744],
  CA: [-106.3468, 56.1304],
  KR: [127.7669, 35.9078],
  HK: [114.1694, 22.3193],
  TW: [121.5654, 25.033],
  ID: [113.9213, -0.7893],
  VN: [108.2772, 14.0583],
  TH: [100.9925, 15.87],
  MY: [101.9758, 4.2105],
  PH: [121.774, 12.8797],
  AE: [53.8478, 23.4241],
  SA: [45.0792, 23.8859],
  ZA: [22.9375, -30.5595],
  NG: [8.6753, 9.082]
}

const resolveIpGeo = async (ip: string) => {
  const key = normalizeIp(ip)
  if (!key) return null
  if (ipGeoCache.has(key)) return ipGeoCache.get(key) || null
  if (ipGeoPending.has(key)) return ipGeoPending.get(key) || null

  const pending = (async () => {
    try {
      await sleep(120)
      const resp = await fetch(`https://ipwho.is/${encodeURIComponent(key)}`)
      if (!resp.ok) return null
      const data = await resp.json()
      if (!data?.success) return null

      const lng = Number(data.longitude)
      const lat = Number(data.latitude)
      if (!Number.isFinite(lng) || !Number.isFinite(lat)) return null

      const result = {
        lng,
        lat,
        name: data.city || data.region || data.country,
        countryCode: String(data.country_code || '').toUpperCase()
      }
      ipGeoCache.set(key, result)
      return result
    } catch {
      return null
    } finally {
      ipGeoPending.delete(key)
    }
  })()

  ipGeoPending.set(key, pending)
  return pending
}

const getHourLabel = (date: Date) => `${String(date.getHours()).padStart(2, '0')}:00`

const riskLevelDistribution = computed(() => {
  const levelCountMap: Record<string, number> = { LOW: 0, MEDIUM: 0, HIGH: 0, CRITICAL: 0 }
  recordList.value.forEach((item) => {
    const level = (item.riskLevel || '').toUpperCase()
    if (levelCountMap[level] !== undefined) levelCountMap[level] += 1
  })

  return [
    { name: 'LOW', value: levelCountMap.LOW },
    { name: 'MEDIUM', value: levelCountMap.MEDIUM },
    { name: 'HIGH', value: levelCountMap.HIGH },
    { name: 'CRITICAL', value: levelCountMap.CRITICAL }
  ]
})

const interceptTrendData = computed(() => {
  const now = new Date()
  const labels: string[] = []
  const values = new Array(24).fill(0)

  for (let i = 23; i >= 0; i--) {
    const d = new Date(now.getTime() - i * 3600 * 1000)
    labels.push(getHourLabel(d))
  }

  recordList.value.forEach((item) => {
    const level = (item.riskLevel || '').toUpperCase()
    if (!['HIGH', 'CRITICAL'].includes(level) || !item.createTime) return
    const eventDate = new Date(item.createTime)
    const diffHour = Math.floor((now.getTime() - eventDate.getTime()) / 3600_000)
    if (diffHour >= 0 && diffHour < 24) {
      const idx = 23 - diffHour
      values[idx] += 1
    }
  })

  return { labels, values }
})

const globalRiskPoints = computed(() => {
  const pointsMap = new Map<string, { name: string; value: [number, number, number] }>()

  recordList.value.forEach((item) => {
    const ip = normalizeIp(item.ip)
    if (!ip) return

    const ipInfo = parseJsonText(item.ipInfoJson, {})
    const lng = Number(
      ipInfo?.lng ??
        ipInfo?.lon ??
        ipInfo?.longitude ??
        ipInfo?.location?.lng ??
        ipInfo?.location?.lon ??
        ipInfo?.location?.longitude
    )
    const lat = Number(
      ipInfo?.lat ??
        ipInfo?.latitude ??
        ipInfo?.location?.lat ??
        ipInfo?.location?.latitude
    )

    const countryCode = String(ipInfo?.countryCode || ipInfo?.country_code || '').toUpperCase()
    const countryName = String(ipInfo?.country || '').toUpperCase()
    const countryCenter = countryCenterMap[countryCode] || countryCenterMap[countryName]
    const geo = ipGeoCache.get(ip)

    const finalLng = Number.isFinite(lng) ? lng : geo?.lng ?? countryCenter?.[0]
    const finalLat = Number.isFinite(lat) ? lat : geo?.lat ?? countryCenter?.[1]
    if (!Number.isFinite(finalLng) || !Number.isFinite(finalLat)) return

    const level = (item.riskLevel || '').toUpperCase()
    const weight = level === 'CRITICAL' ? 4 : level === 'HIGH' ? 3 : level === 'MEDIUM' ? 2 : 1
    const placeName =
      ipInfo?.city ||
      ipInfo?.regionName ||
      ipInfo?.country ||
      geo?.name ||
      countryCode ||
      ip ||
      '未知来源'

    const key = `${finalLng.toFixed(4)}_${finalLat.toFixed(4)}_${placeName}`
    const existing = pointsMap.get(key)
    if (existing) {
      existing.value[2] += weight
    } else {
      pointsMap.set(key, { name: placeName, value: [finalLng, finalLat, weight] })
    }
  })

  return [...pointsMap.values()]
})

const renderDashboardCharts = () => {
  if (riskLevelPieRef.value) {
    riskLevelPieChart?.dispose()
    riskLevelPieChart = echarts.init(riskLevelPieRef.value)
    riskLevelPieChart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, icon: 'circle' },
      series: [
        {
          type: 'pie',
          radius: ['44%', '72%'],
          center: ['50%', '46%'],
          avoidLabelOverlap: true,
          label: { formatter: '{b}\n{d}%' },
          labelLine: { length: 10, length2: 8 },
          itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
          data: riskLevelDistribution.value,
          color: ['#6bcf8d', '#ffb020', '#ff7a45', '#e5484d']
        }
      ]
    })
  }

  if (interceptTrendRef.value) {
    interceptTrendChart?.dispose()
    interceptTrendChart = echarts.init(interceptTrendRef.value)
    interceptTrendChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 24, right: 18, top: 18, bottom: 28, containLabel: true },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: interceptTrendData.value.labels,
        axisLabel: { color: '#607d9b', interval: 2 }
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: '#607d9b' },
        splitLine: { lineStyle: { color: 'rgba(133,164,199,0.22)' } }
      },
      series: [
        {
          name: '拦截量',
          type: 'line',
          smooth: true,
          symbol: 'circle',
          symbolSize: 7,
          data: interceptTrendData.value.values,
          lineStyle: { width: 3, color: '#4f87ff' },
          itemStyle: { color: '#4f87ff' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(79, 135, 255, 0.28)' },
              { offset: 1, color: 'rgba(79, 135, 255, 0.03)' }
            ])
          }
        }
      ]
    })
  }

  if (globalRiskMapRef.value) {
    globalRiskMapChart?.dispose()
    globalRiskMapChart = echarts.init(globalRiskMapRef.value)
    globalRiskMapChart.setOption({
      tooltip: {
        trigger: 'item',
        formatter: (params: any) => {
          if (Array.isArray(params.value)) {
            return `${params.name}<br/>经纬度: ${params.value[0].toFixed(4)}, ${params.value[1].toFixed(4)}<br/>风险权重: ${params.value[2]}`
          }
          return params.name
        }
      },
      geo: {
        map: 'world',
        roam: true,
        zoom: 1.14,
        aspectScale: 0.82,
        layoutCenter: ['50%', '54%'],
        layoutSize: '110%',
        itemStyle: {
          areaColor: '#e9f2ff',
          borderColor: '#8db1d9'
        },
        emphasis: {
          itemStyle: {
            areaColor: '#d1e6ff'
          }
        }
      },
      series: [
        {
          name: '风险来源',
          type: 'scatter',
          coordinateSystem: 'geo',
          symbolSize: (val: number[]) => Math.max(10, Math.min(22, val[2] * 3 + 8)),
          itemStyle: {
            color: '#ff5e7f'
          },
          label: {
            show: true,
            position: 'top',
            formatter: '{b}',
            color: '#2c3e50',
            fontSize: 11
          },
          data: globalRiskPoints.value
        },
        {
          name: '高危脉冲',
          type: 'effectScatter',
          coordinateSystem: 'geo',
          rippleEffect: {
            scale: 3,
            brushType: 'stroke'
          },
          symbolSize: (val: number[]) => Math.max(12, Math.min(26, val[2] * 4 + 10)),
          itemStyle: {
            color: '#ff3b30'
          },
          data: globalRiskPoints.value.filter((p) => p.value[2] >= 3)
        }
      ]
    })
  }
}

const handleResize = () => {
  riskLevelPieChart?.resize()
  interceptTrendChart?.resize()
  globalRiskMapChart?.resize()
}

const enrichRecordIpGeo = async () => {
  const ips = Array.from(new Set(recordList.value.map((item) => normalizeIp(item.ip)).filter(Boolean)))
  const tasks = ips.map(async (ip) => {
    if (ipGeoCache.has(ip) || ipGeoPending.has(ip)) return
    const geo = await resolveIpGeo(ip)
    if (geo) ipGeoCache.set(ip, geo)
  })
  await Promise.allSettled(tasks)
}

const getRecordList = async () => {
  recordLoading.value = true
  try {
    const data = await getPayRiskAssessRecordPage(recordQuery)
    const page = unwrapRecordPage(data)

    if (page.total > 0 && page.list.length === 0 && recordQuery.pageNo > 1) {
      recordQuery.pageNo = 1
      const firstPageData = await getPayRiskAssessRecordPage(recordQuery)
      const firstPage = unwrapRecordPage(firstPageData)
      recordList.value = firstPage.list
      recordTotal.value = firstPage.total
    } else {
      recordList.value = page.list
      recordTotal.value = page.total
    }

    if (!selectedRecord.value && recordList.value.length > 0) {
      selectedRecord.value = recordList.value[0]
    }

    await enrichRecordIpGeo()
    await nextTick()
    renderDashboardCharts()
  } catch (error: any) {
    recordList.value = []
    recordTotal.value = 0
    message.error(error?.message || '加载记录失败')
  } finally {
    recordLoading.value = false
  }
}

const refreshRecordList = async () => {
  recordQuery.pageNo = 1
  await getRecordList()
}

const handleUseRecord = (record: PayRiskAssessRecordVO) => {
  selectedRecord.value = record
  message.success(`已加载记录 #${record.id}`)
}

const handleDeleteRecord = async (record: PayRiskAssessRecordVO) => {
  try {
    await message.delConfirm(`确认删除记录 #${record.id} 吗？`)
  } catch {
    return
  }

  try {
    await deletePayRiskAssessRecord(record.id)
    if (selectedRecord.value?.id === record.id) {
      selectedRecord.value = null
    }
    await refreshRecordList()
    message.success('记录已删除')
  } catch (error: any) {
    message.error(error?.message || '删除记录失败')
  }
}

const handleClearRecords = async () => {
  try {
    await message.delConfirm('确认一键清空全部风险评估记录？该操作不可恢复。')
  } catch {
    return
  }

  clearing.value = true
  try {
    await clearPayRiskAssessRecords()
    selectedRecord.value = null
    await refreshRecordList()
    message.success('已一键清空')
  } catch (error: any) {
    message.error(error?.message || '一键清空失败')
  } finally {
    clearing.value = false
  }
}

const riskTagType = (riskLevel?: string) => {
  switch (riskLevel) {
    case 'LOW':
      return 'success'
    case 'MEDIUM':
      return 'warning'
    case 'HIGH':
    case 'CRITICAL':
      return 'danger'
    default:
      return 'info'
  }
}

watch(recordList, async () => {
  await nextTick()
  renderDashboardCharts()
})

onMounted(async () => {
  await refreshRecordList()
  window.addEventListener('resize', handleResize)
})

onActivated(async () => {
  await refreshRecordList()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  riskLevelPieChart?.dispose()
  interceptTrendChart?.dispose()
  globalRiskMapChart?.dispose()
  riskLevelPieChart = null
  interceptTrendChart = null
  globalRiskMapChart = null
})
</script>

<style lang="scss" scoped>
.risk-assess-page {
  min-height: 100%;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  background:
    radial-gradient(circle at 8% 0%, rgba(97, 167, 255, 0.2), transparent 30%),
    radial-gradient(circle at 100% 0%, rgba(77, 223, 215, 0.16), transparent 28%),
    linear-gradient(160deg, #f1f6ff 0%, #f6f9ff 48%, #fbfdff 100%);
  border-radius: 16px;
}

.hero {
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
  border-radius: 18px;
  padding: 22px;
  border: 1px solid rgba(136, 187, 246, 0.45);
  background:
    radial-gradient(circle at 90% 0%, rgba(96, 193, 255, 0.28), transparent 42%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.92), rgba(238, 248, 255, 0.86));
  box-shadow: 0 14px 34px rgba(56, 113, 185, 0.14);
}

.hero-kicker {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
  color: #4f7faf;
  letter-spacing: 0.12em;
}

.hero-left h1 {
  margin: 6px 0 10px;
  color: #1a3d64;
  font-size: 32px;
  line-height: 1.2;
}

.hero-desc {
  margin: 0;
  color: #5f7f9f;
  max-width: 680px;
}

.hero-actions {
  margin-top: 16px;
  display: flex;
  gap: 10px;
}


.overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.panel {
  border-radius: 14px;
  border: 1px solid rgba(140, 183, 232, 0.42);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(246, 251, 255, 0.86)),
    radial-gradient(circle at 100% 0%, rgba(120, 185, 255, 0.12), transparent 42%);
  box-shadow:
    0 10px 26px rgba(73, 131, 197, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.65);
  padding: 16px;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.panel:hover {
  transform: translateY(-2px);
  border-color: rgba(108, 168, 238, 0.62);
  box-shadow:
    0 14px 30px rgba(70, 129, 198, 0.18),
    inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.panel :deep(.el-card__body) {
  padding: 0 !important;
}

.metric-card span {
  font-size: 12px;
  color: #6283a7;
}

.metric-card strong {
  display: block;
  margin: 8px 0 12px;
  font-size: 30px;
  color: #1f446a;
  line-height: 1;
}

.metric-card strong small {
  font-size: 14px;
  color: #6d8bae;
}

.risk-level-tag {
  font-size: 18px !important;
  font-weight: 700 !important;
  padding: 0 14px !important;
  height: 36px !important;
  line-height: 34px !important;
  letter-spacing: 0.04em;
  border-radius: 10px;
  border: 1px solid #2fbe85 !important;
  color: #167a58 !important;
  background: transparent !important;
  box-shadow: none !important;
}

.metric-card :deep(.risk-level-tag.el-tag),
.metric-card :deep(.risk-level-tag.el-tag--large) {
  font-size: 18px !important;
  font-weight: 700 !important;
  height: 36px !important;
  line-height: 34px !important;
  padding: 0 14px !important;
  border: 1px solid #2fbe85 !important;
  background: transparent !important;
  box-shadow: none !important;
}

.metric-card :deep(.risk-level-tag .el-tag__content) {
  font-size: 18px !important;
  font-weight: 700 !important;
  line-height: 34px !important;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.chart-card {
  padding: 14px 14px 10px;
}

.chart-canvas {
  width: 100%;
  height: 300px;
  border-radius: 12px;
  background: linear-gradient(180deg, rgba(248, 252, 255, 0.76), rgba(239, 247, 255, 0.6));
}

.geo-panel {
  grid-column: auto;
}

.trend-panel {
  grid-column: 1 / -1;
}

.geo-canvas {
  height: 300px;
}

.trend-panel .chart-canvas {
  height: 380px;
}

.detail-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 10px;
}

.panel-head h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #24486f;
  letter-spacing: 0.01em;
}

.panel-head p {
  margin: 4px 0 0;
  color: #6f8cab;
  font-size: 12px;
}

.geo-panel .panel-head h3,
.trend-panel .panel-head h3 {
  position: relative;
  padding-left: 10px;
}

.geo-panel .panel-head h3::before,
.trend-panel .panel-head h3::before {
  content: '';
  position: absolute;
  left: 0;
  top: 3px;
  width: 3px;
  height: 16px;
  border-radius: 2px;
  background: linear-gradient(180deg, #4f87ff, #70b2ff);
}

.table-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.json-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.json-card h4 {
  margin: 0 0 8px;
  font-size: 14px;
  color: #375d86;
}

.json-block {
  margin: 0;
  min-height: 200px;
  max-height: 350px;
  overflow: auto;
  padding: 12px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.65;
  color: #1f446b;
  border: 1px solid rgba(140, 183, 232, 0.42);
  background: linear-gradient(180deg, #f7fbff 0%, #edf5ff 100%);
  white-space: pre-wrap;
}

.table-panel :deep(.el-table) {
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid rgba(128, 171, 224, 0.34);
  --el-table-bg-color: rgba(248, 252, 255, 0.92);
  --el-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
}

.table-panel :deep(.el-table th.el-table__cell) {
  background: linear-gradient(180deg, rgba(236, 246, 255, 0.95), rgba(225, 239, 255, 0.95));
  color: #31567e;
  font-weight: 700;
}

@media (max-width: 1200px) {
  .hero {
    grid-template-columns: 1fr;
  }

  .overview-grid {
    grid-template-columns: 1fr;
  }

  .dashboard-grid {
    grid-template-columns: 1fr;
  }

  .geo-panel,
  .trend-panel {
    grid-column: auto;
  }

  .chart-canvas {
    height: 280px;
  }

  .geo-canvas {
    height: 320px;
  }

  .json-grid {
    grid-template-columns: 1fr;
  }

  .table-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .table-actions {
    width: 100%;

    :deep(.el-button) {
      flex: 1;
    }
  }
}
</style>
