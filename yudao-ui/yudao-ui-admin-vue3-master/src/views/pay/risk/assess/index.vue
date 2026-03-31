<template>
  <div class="risk-assess-page">
    <div class="hero">
      <div class="hero-copy">
        <p class="hero-kicker">支付风控工作台</p>
        <h1>支付风险评估</h1>
        <p class="hero-desc">输入支付上下文后快速评估风险等级，并在下方审阅提交记录。</p>
      </div>
      <div class="hero-stats">
        <div class="stat-chip">
          <span>当前风险等级</span>
          <strong>{{ result?.riskLevel || '未分析' }}</strong>
        </div>
        <div class="stat-chip">
          <span>已入库记录</span>
          <strong>{{ recordTotal }}</strong>
        </div>
      </div>
    </div>

    <div class="workspace">
      <ContentWrap class="panel input-panel">
        <el-form :model="form" label-position="top">
          <el-form-item label="请求 IP">
            <el-input v-model="form.ip" clearable placeholder="Optional, e.g. 8.8.8.8" />
          </el-form-item>
          <el-form-item label="待分析数据 JSON">
            <el-input v-model="form.paymentDataJson" type="textarea" :rows="16" />
          </el-form-item>
          <div class="action-row">
            <el-button type="primary" :loading="submitting" @click="handleAssess">开始分析</el-button>
            <el-button @click="fillExample('normal')">正常样例</el-button>
            <el-button @click="fillExample('chat')">聊天诈骗样例</el-button>
            <el-button @click="handleReset">重置</el-button>
          </div>
        </el-form>
      </ContentWrap>

      <div class="right-col">
        <ContentWrap class="panel metric-panel">
          <div class="metric-grid">
            <div class="metric-card">
              <span>风险评分</span>
              <strong>{{ riskScore }} <small>/100</small></strong>
              <el-progress :percentage="riskScore" :show-text="false" :stroke-width="10" />
            </div>
            <div class="metric-card">
              <span>风险等级</span>
              <el-tag :type="riskTagType(result?.riskLevel)" size="large">
                {{ result?.riskLevel || '未分析' }}
              </el-tag>
            </div>
            <div class="metric-card">
              <span>命中因子</span>
              <strong>{{ result?.riskFactors?.length || 0 }}</strong>
            </div>
          </div>
        </ContentWrap>

        <ContentWrap class="panel json-panel">
          <div class="json-grid">
            <div class="json-card">
              <h3>分析结果 JSON</h3>
              <pre class="json-block">{{ resultJson }}</pre>
            </div>
            <div class="json-card">
              <h3>输入数据 JSON</h3>
              <pre class="json-block">{{ parsedPreview }}</pre>
            </div>
          </div>
        </ContentWrap>
      </div>
    </div>

    <ContentWrap class="panel table-panel">
      <template #header>
        <div class="table-header">
          <h3>分析记录</h3>
          <span>最新记录优先显示</span>
        </div>
      </template>
      <el-table v-loading="recordLoading" :data="recordList" :stripe="true" size="small">
        <el-table-column label="ID" prop="id" width="90" />
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
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="handleUseRecord(scope.row)">使用</el-button>
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
import { computed, onActivated, onMounted, reactive, ref } from 'vue'
import { dateFormatter } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import {
  assessPayRisk,
  getPayRiskAssessRecordPage,
  type PayRiskAssessRecordVO,
  type PayRiskAssessReqVO,
  type PayRiskAssessRespVO
} from '@/api/pay/risk/assess'

defineOptions({ name: 'PayRiskAssess' })

const message = useMessage()
const submitting = ref(false)
const recordLoading = ref(false)
const recordTotal = ref(0)
const recordList = ref<PayRiskAssessRecordVO[]>([])
const recordQuery = reactive({
  pageNo: 1,
  pageSize: 10
})

const examples = {
  normal: {
    ip: '8.8.8.8',
    paymentData: {
      scene: 'MINI_APP',
      source: 'admin-risk-page',
      orderNo: 'PAY202603300001',
      amount: 99,
      city: 'Shanghai'
    }
  },
  chat: {
    ip: '103.24.10.23',
    paymentData: {
      scene: 'WECHAT_CHAT_RISK',
      source: 'chat-risk-test-page',
      linkCount: 1,
      latestPeerMessage: 'Please click refund link in 3 minutes',
      links: ['https://pay-safe-refund.example.com/verify?id=8831']
    }
  }
} as const

const form = reactive({
  ip: examples.chat.ip,
  paymentDataJson: JSON.stringify(examples.chat.paymentData, null, 2)
})

const result = ref<PayRiskAssessRespVO | null>(null)

const riskScore = computed(() => Math.min(Math.max(Number(result.value?.riskScore || 0), 0), 100))
const resultJson = computed(() => (result.value ? JSON.stringify(result.value, null, 2) : '// no result'))

const parsedPreview = computed(() => {
  try {
    return JSON.stringify(JSON.parse(form.paymentDataJson || '{}'), null, 2)
  } catch {
    return '// invalid json'
  }
})

const fillExample = (key: keyof typeof examples) => {
  form.ip = examples[key].ip
  form.paymentDataJson = JSON.stringify(examples[key].paymentData, null, 2)
}

const handleReset = () => {
  fillExample('chat')
  result.value = null
}

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
      return
    }
    recordList.value = page.list
    recordTotal.value = page.total
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
  form.ip = record.ip || ''
  form.paymentDataJson = JSON.stringify(parseJsonText(record.paymentDataJson, {}), null, 2)
  result.value = {
    riskScore: record.riskScore,
    riskLevel: record.riskLevel,
    deepAnalysis: record.deepAnalysis || '',
    riskFactors: parseJsonText(record.riskFactorsJson, []),
    ipInfo: parseJsonText(record.ipInfoJson, {})
  }
}

const handleAssess = async () => {
  let paymentData: Record<string, unknown>
  try {
    paymentData = JSON.parse(form.paymentDataJson || '{}')
  } catch {
    message.error('待分析数据 JSON 格式不正确')
    return
  }

  const req: PayRiskAssessReqVO = {
    ip: form.ip ? form.ip.trim() : undefined,
    paymentData
  }

  submitting.value = true
  try {
    result.value = await assessPayRisk(req)
    await refreshRecordList()
    message.success('风控分析完成')
  } catch (error: any) {
    message.error(error?.message || '风控分析失败')
  } finally {
    submitting.value = false
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

onMounted(async () => {
  await refreshRecordList()
})

onActivated(async () => {
  await refreshRecordList()
})
</script>

<style lang="scss" scoped>
.risk-assess-page {
  --risk-bg: linear-gradient(160deg, #f1f5ff 0%, #eefaf6 45%, #f9f5ec 100%);
  --risk-card: #ffffff;
  --risk-text: #12202f;
  --risk-muted: #587083;
  --risk-border: #d8e4ef;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  background: var(--risk-bg);
  border-radius: 16px;
  font-family:
    'PingFang SC',
    'Microsoft YaHei',
    'Noto Sans SC',
    'Hiragino Sans GB',
    sans-serif;
}

.hero {
  border-radius: 16px;
  padding: 20px 22px;
  background:
    radial-gradient(circle at 16% 0%, rgba(42, 181, 125, 0.22), transparent 40%),
    linear-gradient(135deg, #0f2337 0%, #17324b 48%, #0e2234 100%);
  color: #ecf3ff;
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.hero-copy h1 {
  margin: 6px 0;
  font-size: 30px;
  line-height: 1.1;
}

.hero-kicker {
  margin: 0;
  color: #9bc0dc;
  font-weight: 700;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-desc {
  margin: 0;
  color: #c5d9ea;
  font-size: 14px;
}

.hero-stats {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 200px;
}

.stat-chip {
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 12px;
  padding: 10px 12px;
}

.stat-chip span {
  display: block;
  color: #b8cee1;
  font-size: 12px;
}

.stat-chip strong {
  display: block;
  margin-top: 4px;
  font-size: 18px;
  color: #fff;
}

.workspace {
  display: grid;
  grid-template-columns: minmax(340px, 420px) minmax(0, 1fr);
  gap: 14px;
}

.right-col {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.panel {
  border-radius: 14px;
  border: 1px solid var(--risk-border);
  box-shadow: 0 12px 36px rgba(36, 58, 88, 0.08);
  background: var(--risk-card);
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  padding: 14px;
  border-radius: 12px;
  background: linear-gradient(180deg, #f8fbff 0%, #f3f9ff 100%);
  border: 1px solid #d8e7f3;
}

.metric-card span {
  color: var(--risk-muted);
  font-size: 12px;
}

.metric-card strong {
  display: block;
  font-size: 28px;
  margin: 6px 0 10px;
  color: var(--risk-text);
}

.metric-card strong small {
  font-size: 14px;
  color: #6f8599;
}

.json-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.json-card h3 {
  margin: 0 0 8px;
  font-size: 14px;
  color: #345066;
}

.json-block {
  margin: 0;
  min-height: 220px;
  max-height: 360px;
  overflow: auto;
  padding: 12px;
  border-radius: 10px;
  background: #0f172a;
  color: #dbeafe;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.table-panel :deep(.el-table) {
  border-radius: 10px;
  overflow: hidden;
}

.table-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.table-header h3 {
  margin: 0;
  font-size: 16px;
  color: var(--risk-text);
}

.table-header span {
  font-size: 12px;
  color: #7390a6;
}

@media (max-width: 1200px) {
  .workspace {
    grid-template-columns: 1fr;
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }

  .json-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .hero {
    flex-direction: column;
  }

  .hero-copy h1 {
    font-size: 24px;
  }
}
</style>
