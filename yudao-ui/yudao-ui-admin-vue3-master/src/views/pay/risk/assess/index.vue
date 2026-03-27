<template>
  <ContentWrap>
    <el-card v-loading="loading" shadow="never">
      <template #header>
        <div class="flex items-center gap-2">
          <span>支付风险评估（Admin 测试页）</span>
        </div>
      </template>

      <el-form :model="form" label-width="110px" class="!max-w-900px">
        <el-form-item label="IP（可选）" prop="ip">
          <el-input v-model="form.ip" placeholder="不填则后端会从 paymentData 中提取第一个 IPv4" clearable />
        </el-form-item>

        <el-form-item label="paymentData（必填）" prop="paymentDataJson">
          <el-input
            v-model="form.paymentDataJson"
            type="textarea"
            :rows="8"
            placeholder='例如：{"orderNo":"TEST","userId":"user","amount":99,"payType":"WECHAT"}'
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleAssess">
            开始评估
          </el-button>
          <el-button @click="handleFillExample">
            填充示例
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="result" class="mt-4" shadow="never">
      <template #header>
        <div class="flex items-center gap-2">
          <span>评估结果</span>
          <el-tag :type="riskTagType(result.riskLevel)">{{ result.riskLevel }}</el-tag>
          <el-tag v-if="typeof result.riskScore === 'number'" type="info">score: {{ result.riskScore }}</el-tag>
        </div>
      </template>
      <pre style="margin: 0; word-break: break-word; white-space: pre-wrap">{{ resultString }}</pre>
    </el-card>

    <!-- 这里修复了 ✅ -->
    <el-empty v-else class="mt-4" description="还没有评估结果" />

  </ContentWrap>
</template>

<script lang="ts" setup>
import { computed, reactive, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { assessPayRisk, type PayRiskAssessReqVO, type PayRiskAssessRespVO } from '@/api/pay/risk/assess'

defineOptions({ name: 'PayRiskAssess' })

const message = useMessage()

const loading = ref(false)
const submitting = ref(false)

const form = reactive({
  ip: '',
  paymentDataJson: '{"orderNo":"TEST20260326001","userId":"user123","amount":99.00,"payType":"WECHAT"}'
})

const result = ref<PayRiskAssessRespVO | null>(null)

const resultString = computed(() => {
  return result.value ? JSON.stringify(result.value, null, 2) : ''
})

const handleFillExample = () => {
  form.ip = '8.8.8.8'
  form.paymentDataJson = '{"orderNo":"TEST20260326001","userId":"user123","amount":99.00,"payType":"WECHAT"}'
  result.value = null
}

const handleAssess = async () => {
  let paymentData: any
  try {
    paymentData = JSON.parse(form.paymentDataJson || '{}')
  } catch (e: any) {
    message.error('paymentData 不是合法 JSON，请检查后重试')
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
  } catch (e: any) {
    if (e?.message) message.error(e.message)
  } finally {
    submitting.value = false
    loading.value = false
  }
}

const riskTagType = (riskLevel: string) => {
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
</script>