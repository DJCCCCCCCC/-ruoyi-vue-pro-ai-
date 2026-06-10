import { ref } from 'vue'
import { generatePoliceReport } from '@/api/risk'
import type { PayRiskPoliceReportReqVO, PayRiskPoliceReportRespVO } from '@/types'

export function usePoliceReport() {
  const loading = ref(false)
  const error = ref<string | null>(null)

  const generate = async (data: PayRiskPoliceReportReqVO): Promise<PayRiskPoliceReportRespVO | null> => {
    loading.value = true
    error.value = null
    try {
      return await generatePoliceReport(data)
    } catch (err) {
      error.value = err instanceof Error ? err.message : '生成报警协助材料失败'
      return null
    } finally {
      loading.value = false
    }
  }

  return { loading, error, generate }
}
