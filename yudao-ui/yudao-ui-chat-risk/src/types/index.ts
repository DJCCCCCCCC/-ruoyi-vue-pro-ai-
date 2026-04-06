export interface PayRiskAssessReqVO {
  ip?: string
  paymentData: Record<string, unknown>
}

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type ChatRole = 'self' | 'peer' | 'system'

export interface BehaviorInfo {
  mocked?: boolean
  summary?: string
  extraScore?: number
  factors?: string[]
  notes?: string[]
  snapshot?: Record<string, unknown>
}

export interface PayRiskAssessRespVO {
  riskScore: number
  riskLevel: RiskLevel
  deepAnalysis: string
  riskFactors?: string[]
  ipInfo?: unknown
  behaviorInfo?: BehaviorInfo
}

export interface ChatMessage {
  id: string
  type: ChatRole
  content: string
  timestamp: Date
  senderName?: string
}
