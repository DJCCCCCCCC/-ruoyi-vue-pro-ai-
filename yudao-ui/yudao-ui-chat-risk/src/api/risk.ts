import { request } from '@/utils/request'
import type { PayRiskAssessReqVO, PayRiskAssessRespVO } from '@/types'

export const assessPayRisk = async (data: PayRiskAssessReqVO): Promise<PayRiskAssessRespVO> => {
  return await request.post('/pay/risk/assess', data)
}
