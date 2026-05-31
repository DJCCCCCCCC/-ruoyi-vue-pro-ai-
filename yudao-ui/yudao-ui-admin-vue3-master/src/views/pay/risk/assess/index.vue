<template>
  <div class="risk-assess-page">
    <section class="hero hero-compact">
      <h1>AI 支付风险检测</h1>
    </section>

    <ContentWrap
      ref="recordTablePanelRef"
      class="panel table-panel"
      :body-style="{ padding: '0' }"
    >
      <div class="panel-head table-head">
        <h3 class="table-title">评估记录</h3>
        <div class="table-actions">
          <el-select
            v-model="recordQuery.reviewStatus"
            placeholder="复核状态"
            clearable
            class="table-filter-select"
            @change="refreshRecordList"
          >
            <el-option label="待复核" value="PENDING" />
            <el-option label="无需复核" value="NOT_REQUIRED" />
            <el-option label="已放行" value="RESOLVED_PASS" />
            <el-option label="已拦截" value="RESOLVED_BLOCK" />
            <el-option label="误报结案" value="DISMISSED" />
          </el-select>
          <el-button type="primary" :loading="recordLoading" @click="refreshRecordList">刷新</el-button>
          <el-button
            type="danger"
            plain
            :loading="clearing"
            :disabled="recordTotal === 0"
            @click="handleClearRecords"
          >
            清空
          </el-button>
        </div>
      </div>

      <div class="table-wrap">
        <el-table
          v-loading="recordLoading"
          :data="recordList"
          :stripe="true"
          fit
          class="record-table"
          highlight-current-row
          :row-class-name="recordRowClassName"
          @row-click="handleRecordRowClick"
        >
          <el-table-column label="时间" prop="createTime" width="168" align="center">
            <template #default="{ row }">
              <span class="cell-time">{{ formatRecordTime(row.createTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="场景" prop="scene" min-width="168" show-overflow-tooltip />
          <el-table-column label="IP" prop="ip" width="124" show-overflow-tooltip />
          <el-table-column label="等级" prop="riskLevel" width="92" align="center">
            <template #default="scope">
              <el-tag :type="riskTagType(scope.row.riskLevel)" size="small" effect="light">
                {{ scope.row.riskLevel || '—' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="分数" prop="riskScore" width="72" align="center">
            <template #default="{ row }">
              <span class="cell-score">{{ row.riskScore ?? '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="建议" prop="decisionAction" min-width="96" align="center">
            <template #default="scope">
              <el-tag
                v-if="scope.row.decisionAction"
                size="small"
                effect="light"
                :type="decisionTagType(scope.row.decisionAction)"
              >
                {{ decisionActionLabel(scope.row.decisionAction) }}
              </el-tag>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="220" align="center" class-name="col-ops">
            <template #default="scope">
              <div class="record-op-cell" @click.stop>
                <el-button link type="primary" @click="handleUseRecord(scope.row)">详情</el-button>
                <el-button
                  v-if="scope.row.reviewStatus === 'PENDING'"
                  link
                  type="warning"
                  @click="openReviewDialog(scope.row)"
                >
                  复核
                </el-button>
                <el-button link type="danger" @click="handleDeleteRecord(scope.row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-if="recordTotal > 0" class="table-pagination">
        <Pagination
          :total="recordTotal"
          v-model:page="recordQuery.pageNo"
          v-model:limit="recordQuery.pageSize"
          @pagination="getRecordList"
        />
      </div>
      <el-empty v-else-if="!recordLoading" class="table-empty" description="暂无评估记录" :image-size="72" />
    </ContentWrap>

    <section v-if="selectedResult" ref="detailPanelRef" class="detail-stack">
      <ContentWrap class="panel verdict-strip">
        <div class="verdict-main">
          <div class="verdict-score">
            <strong>{{ riskScore }}<small>/100</small></strong>
            <el-progress :percentage="riskScore" :show-text="false" :stroke-width="6" :color="riskProgressColor" />
          </div>
          <div class="verdict-tags">
            <el-tag class="risk-level-tag" :type="riskTagType(selectedResult?.riskLevel)" size="large" effect="dark">
              {{ selectedResult?.riskLevel || '—' }}
            </el-tag>
            <el-tag
              v-if="selectedResult?.decision?.recommendedAction"
              :type="decisionTagType(selectedResult.decision.recommendedAction)"
            >
              {{ decisionActionLabel(selectedResult.decision.recommendedAction) }}
            </el-tag>
            <el-tag v-if="selectedRecord?.reviewStatus" size="small" effect="plain">
              {{ reviewStatusLabel(selectedRecord.reviewStatus) }}
            </el-tag>
            <el-tag v-if="caseSimilarityBonus > 0" size="small" type="warning" effect="plain">
              案例+{{ caseSimilarityBonus }}
            </el-tag>
          </div>
          <span class="verdict-meta muted">#{{ selectedRecord?.id }} · {{ selectedRecord?.ip || '—' }}</span>
        </div>
        <p v-if="compactVerdictText" class="verdict-summary">{{ compactVerdictText }}</p>
        <div v-if="displayRiskFactorsPreview.length" class="factor-chips">
          <span v-for="f in displayRiskFactorsPreview" :key="f" class="factor-chip">{{ f }}</span>
        </div>
      </ContentWrap>

      <LlmRiskReportPanel :report="selectedResult?.llmReport" />

      <el-collapse v-model="detailCollapseActive" class="detail-collapse">
        <el-collapse-item v-if="selectedResult.decision" name="decision">
          <template #title>
            <span class="collapse-title">决策说明</span>
          </template>
          <p v-if="selectedResult.decision.reviewHint" class="decision-hint">{{ truncateText(selectedResult.decision.reviewHint, 200) }}</p>
          <ul class="reason-list compact-list">
            <li v-for="(m, idx) in selectedResult.decision.reasonMessages" :key="idx">{{ truncateText(m, 120) }}</li>
          </ul>
        </el-collapse-item>

        <el-collapse-item
          v-if="selectedResult.riskFactors?.length || selectedResult.deepAnalysis"
          name="factors"
        >
          <template #title>
            <span class="collapse-title">因子 / 深度分析</span>
            <span v-if="riskFactorsCount" class="collapse-count">{{ riskFactorsCount }}</span>
          </template>
          <ul v-if="selectedResult.riskFactors?.length" class="reason-list compact-list">
            <li v-for="(f, idx) in selectedResult.riskFactors" :key="idx">{{ truncateText(f, 120) }}</li>
          </ul>
          <pre v-if="selectedResult.deepAnalysis" class="analysis-pre">{{ truncateText(selectedResult.deepAnalysis, 600) }}</pre>
        </el-collapse-item>

        <el-collapse-item name="dashboard">
          <template #title>
            <span class="collapse-title">统计看板</span>
          </template>
          <section class="dashboard-grid dashboard-grid-nested">
            <ContentWrap class="panel chart-card">
              <div class="panel-head compact-head"><h3>等级</h3></div>
              <div ref="riskLevelPieRef" class="chart-canvas chart-canvas-sm"></div>
            </ContentWrap>
            <ContentWrap class="panel chart-card">
              <div class="panel-head compact-head"><h3>地域</h3></div>
              <div ref="globalRiskMapRef" class="chart-canvas chart-canvas-sm geo-canvas"></div>
            </ContentWrap>
            <ContentWrap class="panel chart-card trend-panel">
              <div class="panel-head compact-head"><h3>24h 趋势</h3></div>
              <div ref="interceptTrendRef" class="chart-canvas chart-canvas-sm"></div>
            </ContentWrap>
            <ContentWrap class="panel chart-card new-terms-panel">
              <div class="panel-head new-terms-head compact-head">
                <h3>今日话术</h3>
                <div class="new-terms-actions">
                  <el-button type="primary" link @click="goRiskTermLib">词库</el-button>
                  <el-button type="primary" link :loading="newTermsLoading" @click="loadTodayNewTerms">刷新</el-button>
                </div>
              </div>
              <div v-loading="newTermsLoading" class="new-terms-body">
                <el-empty v-if="!newTermsLoading && newTermsList.length === 0" description="今日无新增" />
                <div v-else class="new-terms-chips" :class="{ 'is-collapsed': !newTermsExpanded }">
                  <button
                    v-for="item in displayedNewTermsList"
                    :key="item.term"
                    type="button"
                    class="new-term-chip"
                    @click="openTodayNewTermDetail(item.term)"
                  >
                    <span class="new-term-text">{{ item.term }}</span>
                    <span class="new-term-count">{{ item.todayHitCount }} 单</span>
                  </button>
                </div>
                <div v-if="newTermsList.length > NEW_TERMS_COLLAPSE_LIMIT" class="new-terms-expand-bar">
                  <el-button type="primary" link @click="newTermsExpanded = !newTermsExpanded">
                    {{ newTermsExpanded ? '收起' : `展开全部（${newTermsList.length}）` }}
                  </el-button>
                </div>
              </div>
            </ContentWrap>
          </section>
        </el-collapse-item>

        <el-collapse-item v-if="selectedResult.advancedAnalysis || selectedResult.agentReflection" name="advanced">
          <template #title>
            <span class="collapse-title">高级 / Agent</span>
          </template>
          <AdvancedRiskAnalysisPanel :analysis="selectedResult?.advancedAnalysis" />
          <AgentReflectionPanel :reflection="selectedResult?.agentReflection" />
        </el-collapse-item>

        <el-collapse-item name="intel">
          <template #title>
            <span class="collapse-title">情报 / 拓扑 / OCR</span>
          </template>
          <ThreatIntelPanel
            :ip-info="selectedResult?.ipInfo"
            :whois-info="selectedResult?.whoisInfo"
            :payment-data="selectedPaymentObject"
          />
          <PaymentTopologyPanel :topology="selectedResult?.topologyInfo" :payment-data="selectedPaymentObject" />
          <ImageContentAnalysisPanel :payment-data="selectedPaymentObject" :record-id="selectedRecord?.id ?? null" />
        </el-collapse-item>

        <el-collapse-item name="json">
          <template #title>
            <span class="collapse-title">原始 JSON</span>
          </template>
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
        </el-collapse-item>
      </el-collapse>
    </section>

    <el-empty v-else class="panel empty-hint" description="请选择一条记录" />

    <el-collapse class="debug-collapse">
      <el-collapse-item name="debug">
        <template #title>
          <span class="collapse-title">调试 · 发起评估</span>
        </template>
        <ContentWrap class="panel invoke-assess-panel">
          <el-form label-width="100px" class="invoke-form">
            <el-form-item label="客户端 IP">
              <el-input v-model="assessIp" placeholder="如 8.8.8.8" clearable style="max-width: 320px" />
            </el-form-item>
            <el-form-item label="本人情况">
              <div class="profile-inline">
                <el-select v-model="userAgeBand" placeholder="年龄段" clearable style="width: 140px">
                  <el-option label="未成年" value="UNDER_18" />
                  <el-option label="青年" value="YOUNG_ADULT" />
                  <el-option label="中年" value="MIDDLE_AGED" />
                  <el-option label="中老年" value="SENIOR" />
                </el-select>
                <el-select v-model="userPersonality" placeholder="个性倾向" clearable style="width: 180px">
                  <el-option label="偏焦虑" value="ANXIOUS" />
                  <el-option label="偏谨慎" value="CAUTIOUS" />
                  <el-option label="数字新手" value="DIGITAL_NOVICE" />
                  <el-option label="易信权威" value="AUTHORITY_TRUSTING" />
                  <el-option label="易冲动" value="IMPULSIVE" />
                </el-select>
                <el-select v-model="userRiskLiteracy" placeholder="防诈了解" clearable style="width: 120px">
                  <el-option label="较少" value="LOW" />
                  <el-option label="一般" value="MEDIUM" />
                  <el-option label="熟悉" value="HIGH" />
                </el-select>
              </div>
            </el-form-item>
            <el-form-item label="paymentData">
              <el-input v-model="assessPaymentJson" type="textarea" :rows="10" class="json-textarea" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="assessSubmitting" @click="handleInvokeAssess">提交评估</el-button>
            </el-form-item>
          </el-form>
        </ContentWrap>
      </el-collapse-item>
    </el-collapse>

    <el-dialog v-model="reviewDialogVisible" title="人工复核" width="440px" destroy-on-close>
      <el-form label-width="88px">
        <el-form-item label="复核动作">
          <el-radio-group v-model="reviewForm.reviewAction">
            <el-radio-button label="PASS">放行</el-radio-button>
            <el-radio-button label="BLOCK">确认拦截</el-radio-button>
            <el-radio-button label="DISMISS">误报结案</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="reviewForm.remark" type="textarea" :rows="3" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="reviewSubmitting" @click="submitReview">提交</el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="newTermDrawerVisible"
      :title="newTermDrawerTitle"
      direction="rtl"
      size="92%"
      class="new-term-drawer-root"
      destroy-on-close
    >
      <div v-loading="newTermDetailLoading" class="new-term-drawer">
        <el-empty v-if="!newTermDetailLoading && newTermTickets.length === 0" description="暂无穿透数据" />
        <div v-for="t in newTermTickets" :key="t.id" class="ticket-block">
          <div class="ticket-head">
            <div class="ticket-meta">
              <strong>评估工单 #{{ t.id }}</strong>
              <span class="ticket-sub">{{ t.scene || '—' }} · {{ t.source || '—' }}</span>
            </div>
            <div class="ticket-tags">
              <el-tag size="small" :type="riskTagType(t.riskLevel)">{{ t.riskLevel || '—' }}</el-tag>
              <el-tag v-if="t.decisionAction" size="small" effect="plain" :type="decisionTagType(t.decisionAction)">
                {{ t.decisionAction }}
              </el-tag>
              <el-tag v-if="t.reviewStatus" size="small" type="info" effect="plain">
                {{ reviewStatusLabel(t.reviewStatus) }}
              </el-tag>
            </div>
          </div>
          <div class="ticket-actions">
            <el-button type="primary" link @click="openRecordFromTicket(t.id)">打开完整评估详情</el-button>
            <span v-if="t.createTime" class="ticket-time">{{ t.createTime }}</span>
          </div>
          <div class="conv-block">
            <div class="conv-label">沟通过程汇总</div>
            <pre class="conv-summary">{{ t.conversationSummary || '（无结构化对话摘要）' }}</pre>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script lang="ts" setup>
import * as echarts from 'echarts'
import worldGeoJson from '@/assets/world.geo.json'
import { computed, nextTick, onActivated, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { formatDate } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import AdvancedRiskAnalysisPanel from './components/AdvancedRiskAnalysisPanel.vue'
import AgentReflectionPanel from './components/AgentReflectionPanel.vue'
import ImageContentAnalysisPanel from './components/ImageContentAnalysisPanel.vue'
import LlmRiskReportPanel from './components/LlmRiskReportPanel.vue'
import PaymentTopologyPanel from './components/PaymentTopologyPanel.vue'
import ThreatIntelPanel from './components/ThreatIntelPanel.vue'
import {
  assessPayRisk,
  clearPayRiskAssessRecords,
  deletePayRiskAssessRecord,
  getPayRiskAssessRecordPage,
  getPayRiskTodayNewTermDetail,
  getPayRiskTodayNewTerms,
  reviewPayRiskAssessRecord,
  type PayRiskAssessRecordVO,
  type PayRiskAssessRespVO,
  type PayRiskTermRelatedTicket
} from '@/api/pay/risk/assess'

defineOptions({ name: 'PayRiskAssess' })

const router = useRouter()

const DEFAULT_ASSESS_PAYMENT_JSON = `{
  "scene": "ADMIN_DESK_RISK",
  "source": "pay-risk-assess-page",
  "messageCount": 2,
  "linkCount": 1,
  "links": ["https://example-phishing.test/verify"],
  "detectedSignals": ["链接", "转账催促"],
  "latestPeerMessage": "请点击链接完成验证并尽快转账，三分钟内有效。",
  "messages": [
    {
      "role": "peer",
      "senderName": "对方",
      "content": "请点击链接完成验证并尽快转账，三分钟内有效。",
      "timestamp": "2026-01-15T10:00:00.000Z"
    },
    {
      "role": "self",
      "senderName": "我",
      "content": "这是什么链接？",
      "timestamp": "2026-01-15T10:01:00.000Z"
    }
  ]
}`

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
  pageSize: 10,
  reviewStatus: '' as string
})

const reviewDialogVisible = ref(false)
const reviewSubmitting = ref(false)
const reviewTargetId = ref<number | null>(null)
const reviewForm = reactive({
  reviewAction: 'PASS' as 'PASS' | 'BLOCK' | 'DISMISS',
  remark: ''
})

const NEW_TERMS_COLLAPSE_LIMIT = 16
const newTermsLoading = ref(false)
const newTermsExpanded = ref(false)
const newTermsList = ref<Array<{ term: string; todayHitCount: number; relatedRecordIds: number[] }>>([])
const displayedNewTermsList = computed(() =>
  newTermsExpanded.value ? newTermsList.value : newTermsList.value.slice(0, NEW_TERMS_COLLAPSE_LIMIT)
)
const newTermDrawerVisible = ref(false)
const newTermDrawerTitle = ref('今日新增风险词')
const newTermDetailLoading = ref(false)
const newTermTickets = ref<PayRiskTermRelatedTicket[]>([])
const recordTablePanelRef = ref<{ $el?: HTMLElement } | null>(null)
const detailPanelRef = ref<HTMLElement | null>(null)
const detailCollapseActive = ref<string[]>([])

const VERDICT_PREVIEW_LEN = 96
const MAX_FACTOR_CHIPS = 3
const truncateText = (text?: string, max = 120) => {
  const s = (text || '').trim()
  if (!s) return ''
  return s.length > max ? `${s.slice(0, max)}…` : s
}

const assessSubmitting = ref(false)
const assessIp = ref('8.8.8.8')
const assessPaymentJson = ref(DEFAULT_ASSESS_PAYMENT_JSON)
const userAgeBand = ref('')
const userPersonality = ref('')
const userRiskLiteracy = ref('')

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
  const advancedAnalysis = parseJsonText(record.advancedAnalysisJson, undefined)
  let caseBonus = 0
  if (advancedAnalysis?.caseMatches?.length) {
    caseBonus = Math.min(
      20,
      advancedAnalysis.caseMatches.reduce((sum: number, item: { bonusScore?: number }) => {
        return sum + Number(item.bonusScore || 0)
      }, 0)
    )
  }
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
    advancedAnalysis,
    agentReflection: parseJsonText(record.agentReflectionJson, undefined),
    caseSimilarityBonus: caseBonus,
    decision: parseJsonText(record.decisionJson, undefined)
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

const compactVerdictText = computed(() => {
  const report = selectedResult.value?.llmReport
  const raw = report?.summary?.trim() || report?.verdict?.trim() || selectedResult.value?.deepAnalysis?.trim() || ''
  return truncateText(raw, VERDICT_PREVIEW_LEN)
})

const displayRiskFactorsPreview = computed(() => {
  const factors = selectedResult.value?.riskFactors || []
  return factors.slice(0, MAX_FACTOR_CHIPS).map((f) => truncateText(f, 36))
})

const riskProgressColor = computed(() => {
  const score = riskScore.value
  if (score >= 85) return '#ef4444'
  if (score >= 65) return '#f97316'
  if (score >= 35) return '#eab308'
  return '#22c55e'
})

const decisionActionLabel = (action?: string) => {
  switch ((action || '').toUpperCase()) {
    case 'ALLOW':
      return '放行'
    case 'MANUAL_REVIEW':
      return '人工复核'
    case 'BLOCK':
      return '拦截'
    default:
      return action || '—'
  }
}

const recordRowClassName = ({ row }: { row: PayRiskAssessRecordVO }) =>
  row.id === selectedRecord.value?.id ? 'is-current-record' : ''

const formatRecordTime = (value?: string | Date) => {
  if (!value) return '—'
  return formatDate(new Date(value), 'YYYY-MM-DD HH:mm')
}

const handleRecordRowClick = (row: PayRiskAssessRecordVO) => {
  handleUseRecord(row)
}

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
    const data = await getPayRiskAssessRecordPage({
      ...recordQuery,
      reviewStatus: recordQuery.reviewStatus || undefined
    })
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
    await loadTodayNewTerms()
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

const loadTodayNewTerms = async () => {
  newTermsLoading.value = true
  try {
    const res = await getPayRiskTodayNewTerms()
    newTermsList.value = res.terms || []
    newTermsExpanded.value = false
  } catch (e: unknown) {
    newTermsList.value = []
    newTermsExpanded.value = false
    message.error(e instanceof Error ? e.message : '加载今日新增风险词失败')
  } finally {
    newTermsLoading.value = false
  }
}

const goRiskTermLib = () => {
  router.push({ name: 'PayRiskTerm' })
}

const openTodayNewTermDetail = async (term: string) => {
  newTermDrawerTitle.value = `今日新增风险词：${term}`
  newTermDrawerVisible.value = true
  newTermDetailLoading.value = true
  newTermTickets.value = []
  try {
    const res = await getPayRiskTodayNewTermDetail(term)
    newTermTickets.value = res.tickets || []
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '加载穿透详情失败')
    newTermDrawerVisible.value = false
  } finally {
    newTermDetailLoading.value = false
  }
}

const openRecordFromTicket = async (id: number) => {
  try {
    const data = await getPayRiskAssessRecordPage({ pageNo: 1, pageSize: 1, id })
    const page = unwrapRecordPage(data)
    if (page.list?.length) {
      selectedRecord.value = page.list[0]
      newTermDrawerVisible.value = false
      await nextTick()
      scrollToDetailPanel()
    } else {
      message.warning('未找到该工单记录')
    }
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '加载工单失败')
  }
}

const handleInvokeAssess = async () => {
  let paymentData: Record<string, unknown>
  try {
    paymentData = JSON.parse(assessPaymentJson.value) as Record<string, unknown>
  } catch {
    message.error('paymentData 不是合法 JSON，请检查格式')
    return
  }

  const profile: Record<string, string> = {}
  if (userAgeBand.value) {
    profile.ageBand = userAgeBand.value
  }
  if (userPersonality.value) {
    profile.personalityHint = userPersonality.value
  }
  if (userRiskLiteracy.value) {
    profile.riskLiteracy = userRiskLiteracy.value
  }
  if (Object.keys(profile).length > 0) {
    paymentData.userProfile = profile
  }

  assessSubmitting.value = true
  try {
    await assessPayRisk({
      ip: (assessIp.value || '').trim() || undefined,
      paymentData
    })
    await refreshRecordList()
    if (recordList.value.length > 0) {
      selectedRecord.value = recordList.value[0]
      message.success(`评估已完成，已选中最新记录 #${recordList.value[0].id}`)
    } else {
      message.success('评估已完成')
    }
  } catch (error: any) {
    message.error(error?.message || '评估请求失败')
  } finally {
    assessSubmitting.value = false
  }
}

const handleUseRecord = async (record: PayRiskAssessRecordVO) => {
  selectedRecord.value = record
  detailCollapseActive.value = []
  await nextTick()
  detailPanelRef.value?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
}

const scrollToDetailPanel = () => {
  detailPanelRef.value?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
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

const decisionTagType = (action?: string) => {
  switch ((action || '').toUpperCase()) {
    case 'ALLOW':
      return 'success'
    case 'MANUAL_REVIEW':
      return 'warning'
    case 'BLOCK':
      return 'danger'
    default:
      return 'info'
  }
}

const reviewStatusLabel = (status?: string) => {
  switch ((status || '').toUpperCase()) {
    case 'PENDING':
      return '待复核'
    case 'NOT_REQUIRED':
      return '无需复核'
    case 'RESOLVED_PASS':
      return '已放行'
    case 'RESOLVED_BLOCK':
      return '已拦截'
    case 'DISMISSED':
      return '误报结案'
    default:
      return status || '—'
  }
}

const openReviewDialog = (record: PayRiskAssessRecordVO) => {
  reviewTargetId.value = record.id
  reviewForm.reviewAction = 'PASS'
  reviewForm.remark = ''
  reviewDialogVisible.value = true
}

const submitReview = async () => {
  if (reviewTargetId.value == null) return
  reviewSubmitting.value = true
  try {
    await reviewPayRiskAssessRecord({
      id: reviewTargetId.value,
      reviewAction: reviewForm.reviewAction,
      remark: reviewForm.remark
    })
    reviewDialogVisible.value = false
    message.success('复核已提交')
    await getRecordList()
    const updated = recordList.value.find((r) => r.id === reviewTargetId.value)
    if (updated) {
      selectedRecord.value = updated
    }
  } catch (error: any) {
    message.error(error?.message || '复核失败')
  } finally {
    reviewSubmitting.value = false
  }
}

watch(recordList, async () => {
  await nextTick()
  if (detailCollapseActive.value.includes('dashboard')) {
    renderDashboardCharts()
  }
})

watch(detailCollapseActive, async (active) => {
  if (active.includes('dashboard')) {
    await nextTick()
    renderDashboardCharts()
  }
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
.invoke-assess-panel {
  border-radius: 14px;
}

.invoke-head h3 {
  margin: 0 0 4px;
}

.invoke-form {
  max-width: 960px;
}

.profile-inline {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.json-textarea :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.45;
}

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

.hero-compact {
  padding: 14px 20px;
}

.hero-compact h1 {
  margin: 0;
  font-size: 22px;
  color: #1a3d64;
  line-height: 1.25;
}

.verdict-strip {
  padding: 14px 18px;
  border-color: rgba(59, 130, 246, 0.35);
  background:
    radial-gradient(circle at 100% 0%, rgba(96, 193, 255, 0.15), transparent 45%),
    linear-gradient(135deg, rgba(239, 246, 255, 0.95), rgba(255, 255, 255, 0.92));
  box-shadow: 0 8px 22px rgba(59, 130, 246, 0.1);
}

.verdict-main {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 14px;
}

.verdict-score {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 120px;
}

.verdict-score strong {
  font-size: 28px;
  color: #1f446a;
  line-height: 1;
}

.verdict-score strong small {
  font-size: 14px;
  color: #6d8bae;
}

.verdict-score :deep(.el-progress) {
  flex: 1;
  min-width: 72px;
  max-width: 120px;
}

.verdict-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  flex: 1;
}

.verdict-strip .verdict-meta {
  margin: 0;
  font-size: 12px;
  white-space: nowrap;
}

.verdict-summary {
  margin: 10px 0 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.78);
  border-left: 3px solid #3b82f6;
  color: #1e3a5f;
  font-size: 13px;
  font-weight: 400;
  line-height: 1.55;
}

.factor-chips {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
}

.factor-chip {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(254, 226, 226, 0.7);
  color: #991b1b;
  font-size: 12px;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.collapse-count {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 500;
  color: #94a3b8;
}

.compact-list {
  font-size: 13px;
  line-height: 1.5;
}

.compact-list li {
  margin-bottom: 4px;
}

.table-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #24486f;
  padding-left: 12px;
  position: relative;
}

.table-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 2px;
  width: 3px;
  height: 18px;
  border-radius: 2px;
  background: linear-gradient(180deg, #3b82f6, #60a5fa);
}

.detail-collapse {
  border: none;
  background: transparent;
}

.detail-collapse :deep(.el-collapse-item) {
  margin-bottom: 8px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid rgba(140, 183, 232, 0.35);
  background: rgba(255, 255, 255, 0.88);
}

.detail-collapse :deep(.el-collapse-item__header) {
  padding: 0 16px;
  height: 46px;
  border: none;
  background: rgba(248, 252, 255, 0.9);
  font-size: 14px;
}

.detail-collapse :deep(.el-collapse-item__wrap) {
  border: none;
}

.detail-collapse :deep(.el-collapse-item__content) {
  padding: 12px 16px 16px;
}

.collapse-title {
  font-weight: 600;
  color: #1a3d64;
}

.collapse-badge {
  margin-left: 10px;
}

.analysis-pre {
  margin: 12px 0 0;
  padding: 12px;
  border-radius: 10px;
  background: #f8fafc;
  color: #334155;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 320px;
  overflow: auto;
}

.dashboard-grid-nested {
  margin-top: 0;
}

.chart-canvas-sm {
  height: 240px;
}

.compact-head {
  margin-bottom: 8px;
}

.compact-head h3 {
  margin: 0;
  font-size: 15px;
  color: #1a3d64;
}

.debug-collapse {
  border: none;
  background: transparent;
}

.debug-collapse :deep(.el-collapse-item) {
  border-radius: 12px;
  border: 1px dashed rgba(148, 163, 184, 0.5);
  background: rgba(248, 250, 252, 0.6);
}

.debug-collapse :deep(.el-collapse-item__header) {
  padding: 0 14px;
  border: none;
  height: 42px;
}

.empty-hint {
  padding: 32px 16px;
}

.detail-stack :deep(.panel:not(.table-panel):hover) {
  transform: translateY(-2px);
}

.detail-stack :deep(.llm-panel) {
  border-color: rgba(99, 102, 241, 0.35);
  box-shadow: 0 10px 28px rgba(99, 102, 241, 0.12);
}

.detail-stack :deep(.advanced-panel),
.detail-stack :deep(.reflection-panel),
.detail-stack :deep(.intel-panel),
.detail-stack :deep(.image-ocr-panel) {
  margin-top: 0;
}

.table-panel :deep(.el-table__body tr.is-current-record > td.el-table__cell) {
  background-color: rgba(239, 246, 255, 0.9) !important;
}

.table-panel :deep(.el-table__body tr.current-row > td.el-table__cell) {
  background-color: rgba(239, 246, 255, 0.9) !important;
}

/* 评估记录：沿用 panel 渐变，表格区不随卡片上浮 */
.table-panel.panel {
  padding: 0;
}

.table-panel.panel:hover {
  transform: none;
}

.table-panel :deep(.el-card__body) {
  padding: 18px 18px 14px !important;
}

.table-head {
  flex-wrap: wrap;
  align-items: center;
  margin-bottom: 0;
  padding-bottom: 14px;
  border-bottom: 1px solid #eef2f6;
}

.table-head-text h3::before {
  content: '';
  position: absolute;
  left: 0;
  top: 2px;
  width: 3px;
  height: 18px;
  border-radius: 2px;
  background: linear-gradient(180deg, #3b82f6, #60a5fa);
}

.table-head-text {
  min-width: 0;
}

.table-filter-select {
  width: 140px;
}

.table-wrap {
  width: 100%;
  margin-top: 14px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e8eef4;
}

.record-table {
  width: 100% !important;
}

.table-panel :deep(.record-table .el-table__cell) {
  padding: 12px 0;
}

.table-panel :deep(.record-table .cell) {
  line-height: 22px;
  padding: 0 12px;
}

.table-panel :deep(.record-table .col-ops .cell) {
  padding: 0 10px;
}

.record-op-cell {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 4px 10px;
  row-gap: 4px;
}

.record-op-cell :deep(.el-button) {
  margin: 0;
  padding: 4px 6px;
}

.cell-time {
  font-variant-numeric: tabular-nums;
  font-size: 13px;
  color: #3d5f82;
  white-space: nowrap;
}

.cell-score {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: #1f446a;
}

.table-pagination {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.table-empty {
  padding: 24px 0 8px;
}

.table-panel :deep(.el-table) {
  --el-table-border-color: #e8eef4;
  --el-table-bg-color: transparent;
  --el-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: #f1f5f9;
  --el-table-row-height: 48px;
  border: none;
  border-radius: 12px;
  background: transparent;
}

.table-panel :deep(.el-table::before),
.table-panel :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.table-panel :deep(.el-table th.el-table__cell) {
  background: #f1f5f9;
  color: #475569;
  font-weight: 600;
  font-size: 13px;
  border-bottom: 1px solid #e8eef4;
}

.table-panel :deep(.el-table td.el-table__cell) {
  border-bottom: 1px solid #eef2f6;
}

.table-panel :deep(.el-table__body tr:last-child td.el-table__cell) {
  border-bottom: none;
}

.table-panel :deep(.el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell) {
  background: rgba(255, 255, 255, 0.55);
}

.table-panel :deep(.el-table__body tr:hover > td.el-table__cell) {
  background-color: rgba(241, 245, 249, 0.95) !important;
}

@media (max-width: 1200px) {
  .table-panel :deep(.el-table__body-wrapper) {
    overflow-x: auto;
  }
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

.decision-metric-card .decision-metric {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.decision-metric .muted,
.muted {
  color: #8aa3bc;
  font-size: 13px;
}

.review-pill {
  margin-left: 4px;
}

.decision-panel .decision-head {
  align-items: flex-start;
}

.decision-hint {
  margin: 6px 0 0;
  color: #5f7f9f;
  font-size: 13px;
  line-height: 1.5;
}

.reason-list {
  margin: 0;
  padding-left: 18px;
  color: #2c4a66;
  font-size: 13px;
  line-height: 1.65;
}

.verdict-strip .risk-level-tag {
  font-size: 16px !important;
  font-weight: 700 !important;
  height: 34px !important;
  line-height: 32px !important;
  padding: 0 12px !important;
  letter-spacing: 0.04em;
  border-radius: 10px;
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

.new-terms-panel {
  grid-column: 1 / -1;
}

.new-terms-head {
  align-items: center;
}

.new-terms-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.new-terms-body {
  min-height: 64px;
}

.new-terms-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.new-terms-chips.is-collapsed {
  max-height: 92px;
  overflow: hidden;
}

.new-terms-expand-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed rgba(79, 135, 255, 0.22);
}

.new-terms-summary {
  color: #7a8ba6;
  font-size: 12px;
}

.new-term-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid rgba(79, 135, 255, 0.45);
  background: linear-gradient(180deg, #ffffff, #f0f6ff);
  color: #24486f;
  font-size: 13px;
  cursor: pointer;
  transition: box-shadow 0.15s ease, transform 0.12s ease;
}

.new-term-chip:hover {
  box-shadow: 0 4px 14px rgba(79, 135, 255, 0.22);
  transform: translateY(-1px);
}

.new-term-text {
  max-width: 420px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: left;
}

.new-term-count {
  font-size: 12px;
  font-weight: 700;
  color: #4f87ff;
}

.new-term-drawer {
  padding-bottom: 20px;
}

.ticket-block {
  border: 1px solid rgba(140, 183, 232, 0.45);
  border-radius: 12px;
  padding: 12px 14px;
  margin-bottom: 12px;
  background: linear-gradient(180deg, #fbfdff, #f2f7ff);
}

.ticket-head {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 8px;
}

.ticket-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ticket-meta strong {
  color: #1f446b;
  font-size: 15px;
}

.ticket-sub {
  font-size: 12px;
  color: #6f8cab;
}

.ticket-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.ticket-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.ticket-time {
  font-size: 12px;
  color: #6f8cab;
}

.conv-block {
  border-top: 1px dashed rgba(140, 183, 232, 0.5);
  padding-top: 10px;
}

.conv-label {
  font-size: 12px;
  font-weight: 700;
  color: #375d86;
  margin-bottom: 6px;
}

.conv-summary {
  margin: 0;
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.65;
  color: #1f446b;
  border: 1px solid rgba(140, 183, 232, 0.35);
  background: rgba(255, 255, 255, 0.85);
  white-space: pre-wrap;
  max-height: 280px;
  overflow: auto;
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
    align-items: stretch;
  }

  .table-actions {
    width: 100%;
    flex-wrap: wrap;
    justify-content: flex-start;

    :deep(.el-button) {
      flex: 0 0 auto;
    }
  }

  .table-filter-select {
    width: 100%;
    max-width: 100%;
  }
}
</style>
