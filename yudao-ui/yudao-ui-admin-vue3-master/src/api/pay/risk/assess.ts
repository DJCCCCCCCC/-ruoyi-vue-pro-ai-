import request from '@/config/axios'

export interface PayRiskAssessPaymentData {
  [key: string]: any
}

export interface PayRiskAssessReqVO {
  ip?: string
  paymentData: PayRiskAssessPaymentData
}

export interface PayRiskAssessRespVO {
  riskScore: number
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  deepAnalysis: string
  riskFactors?: string[]
  ipInfo?: any
}

/**
 * 支付风险评估（admin-api）
 *
 * 后端接口：POST /admin-api/pay/risk/assess
 */
export const assessPayRisk = async (data: PayRiskAssessReqVO) => {
  const resp: any = await request.post({ url: '/pay/risk/assess', data })
  // 兼容后端可能返回的两种结构：
  // 1) CommonResult: { code, msg, data: PayRiskAssessRespVO }
  // 2) 直接返回风险对象：{ riskScore, ... }
  return resp?.data ?? resp
}

