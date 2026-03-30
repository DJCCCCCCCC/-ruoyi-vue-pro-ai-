export interface PayRiskAssessReqVO {
  ip?: string
  paymentData: Record<string, unknown>
}

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type ChatRole = 'self' | 'peer' | 'system'

export interface PayRiskAssessRespVO {
  riskScore: number
  riskLevel: RiskLevel
  deepAnalysis: string
  riskFactors?: string[]
  ipInfo?: unknown
}

export interface ChatMessage {
  id: string
  type: ChatRole
  content: string
  timestamp: Date
  senderName?: string
}
