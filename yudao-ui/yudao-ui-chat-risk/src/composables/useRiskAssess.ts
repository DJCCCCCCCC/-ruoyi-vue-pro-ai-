import { ref } from 'vue'
import { assessPayRisk } from '@/api/risk'
import type { PayRiskAssessReqVO, PayRiskAssessRespVO } from '@/types'

export function useRiskAssess() {
  const loading = ref(false)
  const error = ref<string | null>(null)

  const assess = async (data: PayRiskAssessReqVO): Promise<PayRiskAssessRespVO | null> => {
    loading.value = true
    error.value = null

    try {
      return await assessPayRisk(data)
    } catch (err) {
      error.value = err instanceof Error ? err.message : '风险评估失败'
      return null
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    error,
    assess
  }
}
