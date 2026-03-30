<template>
  <div class="risk-assess-page" v-loading="loading">
    <ContentWrap class="risk-wrap">
      <div class="hero-section">
        <div class="hero-copy">
          <span class="hero-kicker">AI 风控工作台</span>
          <h1>支付风险分析中心</h1>
          <p>
            把聊天记录、付款链接、订单 JSON 或支付场景贴进来，系统会输出风险分、命中因子、IP 情报和 AI
            深度分析，适合客服复核和风控演示。
          </p>
        </div>
        <div class="hero-side">
          <div class="hero-pill">
            <span>当前状态</span>
            <strong>{{ result ? '已生成分析结果' : '等待提交分析' }}</strong>
          </div>
          <div class="hero-pill">
            <span>分析来源</span>
            <strong>/admin-api/pay/risk/assess</strong>
          </div>
        </div>
      </div>

      <div class="workspace-grid">
        <el-card shadow="never" class="input-panel">
          <template #header>
            <div class="card-header">
              <div>
                <span class="card-title">输入区</span>
                <p>支持订单对象、聊天记录对象、链接诱导场景等任意 JSON</p>
              </div>
              <el-button text @click="handleFillExample('chat-scam')">诈骗样例</el-button>
            </div>
          </template>

          <div class="preset-list">
            <button
              v-for="preset in presetList"
              :key="preset.key"
              type="button"
              class="preset-item"
              @click="handleFillExample(preset.key)"
            >
              <strong>{{ preset.title }}</strong>
              <span>{{ preset.summary }}</span>
            </button>
          </div>

          <el-form :model="form" label-position="top" class="risk-form">
            <el-form-item label="请求 IP（可选）">
              <el-input
                v-model="form.ip"
                clearable
                placeholder="不填则由后端从 paymentData 中自动识别，例如 8.8.8.8"
              />
            </el-form-item>

            <el-form-item label="待分析数据">
              <el-input
                v-model="form.paymentDataJson"
                type="textarea"
                :rows="14"
                placeholder='例如：{"orderNo":"TEST","userId":"user123","amount":99,"payType":"WECHAT"}'
              />
            </el-form-item>

            <div class="action-row">
              <el-button type="primary" :loading="submitting" @click="handleAssess">
                开始分析
              </el-button>
              <el-button @click="handleFillExample('payment-normal')">填充正常支付样例</el-button>
              <el-button @click="handleReset">重置</el-button>
            </div>
          </el-form>
        </el-card>

        <div class="result-panel">
          <div class="metrics-grid">
            <el-card shadow="never" class="metric-card score-card">
              <span class="metric-label">风险评分</span>
              <div class="metric-main">
                <strong>{{ riskScore }}</strong>
                <span>/ 100</span>
              </div>
              <el-progress
                :percentage="riskScore"
                :show-text="false"
                :stroke-width="12"
                :color="progressColor"
              />
            </el-card>

            <el-card shadow="never" class="metric-card">
              <span class="metric-label">风险等级</span>
              <div class="level-row">
                <el-tag size="large" effect="dark" :type="riskTagType(result?.riskLevel)">
                  {{ riskLevelLabel }}
                </el-tag>
                <span class="level-tip">{{ levelDescription }}</span>
              </div>
            </el-card>

            <el-card shadow="never" class="metric-card">
              <span class="metric-label">命中因子</span>
              <strong class="metric-number">{{ riskFactorsCount }}</strong>
              <span class="metric-hint">可用于人工复核和风控规则回溯</span>
            </el-card>
          </div>

          <el-card v-if="result" shadow="never" class="analysis-card">
            <template #header>
              <div class="card-header">
                <div>
                  <span class="card-title">AI 深度分析</span>
                  <p>给运营和风控同学看的解释性结果</p>
                </div>
                <el-tag :type="riskTagType(result.riskLevel)">{{ result.riskLevel }}</el-tag>
              </div>
            </template>

              <el-alert
              :title="levelDescription"
              :type="riskAlertType(result.riskLevel)"
              :closable="false"
              show-icon
              class="analysis-alert"
            />

            <div class="analysis-content">
              <p>{{ result.deepAnalysis }}</p>
            </div>

            <div v-if="result.riskFactors?.length" class="factor-section">
              <div class="section-title">风险因子</div>
              <div class="factor-list">
                <span v-for="factor in result.riskFactors" :key="factor" class="factor-chip">
                  {{ factor }}
                </span>
              </div>
            </div>
          </el-card>

          <div class="details-grid">
            <el-card shadow="never" class="detail-card">
              <template #header>
                <div class="card-header">
                  <div>
                    <span class="card-title">输入数据预览</span>
                    <p>校验当前待分析内容是否符合预期</p>
                  </div>
                </div>
              </template>

              <pre class="json-block">{{ parsedPreview }}</pre>
            </el-card>

            <el-card shadow="never" class="detail-card">
              <template #header>
                <div class="card-header">
                  <div>
                    <span class="card-title">IP 情报</span>
                    <p>后端返回的脱敏 IP 相关信息</p>
                  </div>
                </div>
              </template>

              <pre class="json-block">{{ ipInfoPreview }}</pre>
            </el-card>
          </div>

          <el-card v-if="result" shadow="never" class="raw-card">
            <template #header>
              <div class="card-header">
                <div>
                  <span class="card-title">原始返回</span>
                  <p>便于联调后端响应结构</p>
                </div>
              </div>
            </template>
            <pre class="json-block">{{ resultString }}</pre>
          </el-card>

          <el-empty v-else description="提交待分析数据后，这里会显示风控结果和 AI 分析" class="empty-panel" />
        </div>
      </div>
    </ContentWrap>
  </div>
</template>

<script lang="ts" setup>
import { computed, reactive, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { assessPayRisk, type PayRiskAssessReqVO, type PayRiskAssessRespVO } from '@/api/pay/risk/assess'

defineOptions({ name: 'PayRiskAssess' })

type RiskLevel = PayRiskAssessRespVO['riskLevel']

interface PresetItem {
  key: 'payment-normal' | 'payment-high' | 'chat-scam'
  title: string
  summary: string
  ip: string
  payload: string
}

const message = useMessage()
const loading = ref(false)
const submitting = ref(false)

const presetMap: Record<PresetItem['key'], Omit<PresetItem, 'key'>> = {
  'payment-normal': {
    title: '正常支付',
    summary: '小额支付、常见设备、常规城市',
    ip: '8.8.8.8',
    payload: JSON.stringify(
      {
        orderNo: 'PAY202603300001',
        userId: 'user_1024',
        amount: 99,
        currency: 'CNY',
        payType: 'WECHAT',
        merchantId: 'MCH10001',
        scene: 'MINI_APP',
        city: 'Shanghai',
        deviceId: 'iphone-15-pro',
        behaviorTag: 'normal-pay'
      },
      null,
      2
    )
  },
  'payment-high': {
    title: '异常支付',
    summary: '大额、陌生设备、短时频繁尝试',
    ip: '185.210.44.99',
    payload: JSON.stringify(
      {
        orderNo: 'PAY202603309999',
        userId: 'guest_7788',
        amount: 16888,
        currency: 'CNY',
        payType: 'WECHAT',
        merchantId: 'MCH90001',
        scene: 'SCAN',
        city: 'Unknown',
        deviceId: 'unknown-simulator',
        retryCount: 5,
        cardBindCount24h: 3,
        behaviorTag: 'high-risk-pay'
      },
      null,
      2
    )
  },
  'chat-scam': {
    title: '聊天诈骗',
    summary: '客服退款链接、强时限、阻断官方核实',
    ip: '103.24.10.23',
    payload: JSON.stringify(
      {
        scene: 'WECHAT_CHAT_RISK',
        peerName: '平台客服',
        linkCount: 1,
        latestPeerMessage: '请 3 分钟内完成退款验证，否则系统会自动扣款',
        messages: [
          {
            role: 'peer',
            senderName: '平台客服',
            content: '你好，我是平台客服，你的订单异常，需要马上处理退款。',
            timestamp: '2026-03-30T18:20:00+08:00'
          },
          {
            role: 'peer',
            senderName: '平台客服',
            content: '请打开这个链接完成验证：https://pay-safe-refund.example.com/verify?id=8831',
            timestamp: '2026-03-30T18:20:30+08:00'
          },
          {
            role: 'peer',
            senderName: '平台客服',
            content: '超时系统会自动扣款，你先别联系官方。',
            timestamp: '2026-03-30T18:21:00+08:00'
          }
        ]
      },
      null,
      2
    )
  }
}

const presetList = computed<PresetItem[]>(() =>
  Object.entries(presetMap).map(([key, value]) => ({
    key: key as PresetItem['key'],
    ...value
  }))
)

const defaultPreset = presetMap['chat-scam']

const form = reactive({
  ip: defaultPreset.ip,
  paymentDataJson: defaultPreset.payload
})

const result = ref<PayRiskAssessRespVO | null>(null)

const resultString = computed(() => (result.value ? JSON.stringify(result.value, null, 2) : ''))

const parsedPaymentData = computed<Record<string, unknown> | null>(() => {
  try {
    return JSON.parse(form.paymentDataJson || '{}')
  } catch {
    return null
  }
})

const parsedPreview = computed(() => {
  if (!form.paymentDataJson.trim()) {
    return '// 暂无待分析数据'
  }

  if (!parsedPaymentData.value) {
    return '// JSON 解析失败，请检查格式'
  }

  return JSON.stringify(parsedPaymentData.value, null, 2)
})

const ipInfoPreview = computed(() =>
  result.value?.ipInfo ? JSON.stringify(result.value.ipInfo, null, 2) : '// 本次分析暂无 IP 情报返回'
)

const riskScore = computed(() => Math.min(Math.max(Number(result.value?.riskScore || 0), 0), 100))

const riskFactorsCount = computed(() => result.value?.riskFactors?.length || 0)

const riskLevelLabelMap: Record<RiskLevel, string> = {
  LOW: '低风险',
  MEDIUM: '中风险',
  HIGH: '高风险',
  CRITICAL: '严重风险'
}

const riskLevelLabel = computed(() => {
  if (!result.value) {
    return '待分析'
  }
  return riskLevelLabelMap[result.value.riskLevel] || result.value.riskLevel
})

const levelDescriptionMap: Record<RiskLevel, string> = {
  LOW: '交易行为稳定，建议正常放行',
  MEDIUM: '出现可疑信号，建议二次校验',
  HIGH: '多项异常命中，建议人工复核',
  CRITICAL: '高危场景明显，建议直接拦截'
}

const levelDescription = computed(() => {
  if (!result.value) {
    return '等待提交分析'
  }
  return levelDescriptionMap[result.value.riskLevel] || '请结合业务人工判断'
})

const progressColor = computed(() => {
  if (!result.value) {
    return '#c0c4cc'
  }
  switch (result.value.riskLevel) {
    case 'LOW':
      return '#16a34a'
    case 'MEDIUM':
      return '#ea580c'
    case 'HIGH':
      return '#dc2626'
    case 'CRITICAL':
      return '#7f1d1d'
    default:
      return '#409eff'
  }
})

const handleFillExample = (presetKey: PresetItem['key']) => {
  const preset = presetMap[presetKey]
  form.ip = preset.ip
  form.paymentDataJson = preset.payload
  result.value = null
}

const handleReset = () => {
  handleFillExample('chat-scam')
}

const handleAssess = async () => {
  let paymentData: Record<string, unknown>
  try {
    paymentData = JSON.parse(form.paymentDataJson || '{}')
  } catch {
    message.error('待分析数据不是合法 JSON，请检查后重试')
    return
  }

  const req: PayRiskAssessReqVO = {
    ip: form.ip ? form.ip.trim() : undefined,
    paymentData
  }

  submitting.value = true
  loading.value = true
  try {
    result.value = await assessPayRisk(req)
    message.success('风控分析已生成')
  } catch (error: any) {
    if (error?.message) {
      message.error(error.message)
    }
  } finally {
    submitting.value = false
    loading.value = false
  }
}

const riskTagType = (riskLevel?: string) => {
  switch (riskLevel) {
    case 'LOW':
      return 'success'
    case 'MEDIUM':
      return 'warning'
    case 'HIGH':
      return 'danger'
    case 'CRITICAL':
      return 'danger'
    default:
      return 'info'
  }
}

const riskAlertType = (riskLevel?: string) => {
  switch (riskLevel) {
    case 'LOW':
      return 'success'
    case 'MEDIUM':
      return 'warning'
    case 'HIGH':
    case 'CRITICAL':
      return 'error'
    default:
      return 'info'
  }
}
</script>

<style lang="scss" scoped>
.risk-assess-page {
  min-height: calc(100vh - 120px);
}

:deep(.risk-wrap .content-wrap) {
  background: transparent;
  padding: 0;
}

.hero-section {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 28px 32px;
  margin-bottom: 20px;
  border-radius: 24px;
  background:
    radial-gradient(circle at top right, rgba(34, 197, 94, 0.2), transparent 22%),
    linear-gradient(135deg, #0f172a 0%, #16243f 52%, #1e293b 100%);
  color: #fff;
}

.hero-copy {
  max-width: 720px;
}

.hero-kicker {
  display: inline-flex;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.18);
  font-size: 12px;
  letter-spacing: 0.08em;
}

.hero-copy h1 {
  margin: 14px 0 10px;
  font-size: 32px;
  line-height: 1.15;
  color: #f8fafc;
}

.hero-copy p {
  margin: 0;
  max-width: 640px;
  color: rgba(226, 232, 240, 0.92);
  line-height: 1.8;
}

.hero-side {
  min-width: 260px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.hero-pill {
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(15, 23, 42, 0.34);
  border: 1px solid rgba(148, 163, 184, 0.15);
}

.hero-pill span {
  display: block;
  color: rgba(148, 163, 184, 0.92);
  font-size: 12px;
}

.hero-pill strong {
  display: block;
  margin-top: 6px;
  font-size: 15px;
  color: #fff;
  word-break: break-word;
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(360px, 420px) minmax(520px, 1fr);
  gap: 20px;
}

.input-panel,
.metric-card,
.analysis-card,
.detail-card,
.raw-card {
  border: 0;
  border-radius: 22px;
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.08);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.card-title {
  display: block;
  color: #0f172a;
  font-size: 17px;
  font-weight: 700;
}

.card-header p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
}

.preset-list {
  display: grid;
  gap: 10px;
  margin-bottom: 18px;
}

.preset-item {
  width: 100%;
  padding: 14px 16px;
  text-align: left;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  background: linear-gradient(180deg, #fff, #f8fafc);
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.preset-item:hover {
  transform: translateY(-1px);
  border-color: #86efac;
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.06);
}

.preset-item strong,
.preset-item span {
  display: block;
}

.preset-item strong {
  color: #0f172a;
  font-size: 14px;
}

.preset-item span {
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
}

.risk-form :deep(.el-form-item__label) {
  color: #334155;
  font-weight: 600;
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.result-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.metric-card {
  min-height: 152px;
}

.metric-label {
  display: block;
  color: #64748b;
  font-size: 13px;
}

.metric-main {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 12px 0 18px;
}

.metric-main strong,
.metric-number {
  font-size: 40px;
  line-height: 1;
  color: #0f172a;
}

.metric-main span,
.metric-hint,
.level-tip {
  color: #64748b;
  font-size: 13px;
}

.level-row {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 18px;
}

.analysis-alert {
  margin-bottom: 16px;
}

.analysis-content p {
  margin: 0;
  color: #334155;
  line-height: 1.9;
  white-space: pre-wrap;
  word-break: break-word;
}

.factor-section {
  margin-top: 18px;
}

.section-title {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 700;
  color: #334155;
}

.factor-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.factor-chip {
  padding: 7px 10px;
  border-radius: 999px;
  background: #fff7ed;
  color: #c2410c;
  font-size: 12px;
  font-weight: 600;
}

.details-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.json-block {
  margin: 0;
  min-height: 220px;
  max-height: 360px;
  overflow: auto;
  padding: 16px;
  border-radius: 18px;
  background: #0f172a;
  color: #dbeafe;
  font-size: 12px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.empty-panel {
  border-radius: 22px;
  background: #fff;
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.08);
}

@media (max-width: 1200px) {
  .workspace-grid,
  .metrics-grid,
  .details-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .hero-section {
    padding: 22px 20px;
  }

  .hero-copy h1 {
    font-size: 26px;
  }

  .hero-section,
  .card-header {
    flex-direction: column;
  }
}
</style>
