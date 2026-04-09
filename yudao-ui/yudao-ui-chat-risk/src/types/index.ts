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

export interface WhoisRecord {
  domain?: string
  payload?: unknown
}

export interface WhoisInfo {
  extraScore?: number
  factors?: string[]
  notes?: string[]
  records?: WhoisRecord[]
}

export interface PayRiskRelationTopology {
  summary?: {
    nodeCount?: number
    edgeCount?: number
    participantCount?: number
    payerCount?: number
    payeeCount?: number
    transactionCount?: number
    signalCount?: number
    sharedAttributeCount?: number
    highRiskNodeCount?: number
    highRiskEdgeCount?: number
    suspiciousClusterCount?: number
  }
  nodes?: Array<{
    id: string
    label: string
    type: string
    role?: string
    riskLevel?: RiskLevel
    tags?: string[]
    meta?: Record<string, unknown>
  }>
  edges?: Array<{
    source: string
    target: string
    type: string
    label?: string
    riskLevel?: RiskLevel
    count?: number
    amount?: number | string
    meta?: Record<string, unknown>
  }>
  signals?: Array<{
    code: string
    level: RiskLevel | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
    title: string
    description: string
    score?: number
    relatedNodeIds?: string[]
  }>
}

export interface PayRiskLlmReport {
  mode?: string
  summary?: string
  verdict?: string
  confidence?: RiskLevel | 'LOW' | 'MEDIUM' | 'HIGH'
  evidence?: string[]
  suspiciousEntities?: string[]
  recommendations?: string[]
}

export interface PayRiskAdvancedAnalysis {
  timeline?: Array<{
    stage?: string
    title?: string
    description?: string
    riskDelta?: number
    evidenceLevel?: string
  }>
  counterfactuals?: Array<{
    title?: string
    hypothesis?: string
    expectedRiskScore?: number
    delta?: number
    reason?: string
  }>
  universe?: {
    summary?: string
    repeatedIndicators?: string[]
    watchList?: string[]
    campaignHints?: string[]
  }
  interventions?: Array<{
    priority?: string
    type?: string
    title?: string
    description?: string
    automationLevel?: string
  }>
}

export interface PayRiskAssessRespVO {
  riskScore: number
  riskLevel: RiskLevel
  deepAnalysis: string
  riskFactors?: string[]
  ipInfo?: unknown
  behaviorInfo?: BehaviorInfo
  whoisInfo?: string
  topologyInfo?: PayRiskRelationTopology
  llmReport?: PayRiskLlmReport
  advancedAnalysis?: PayRiskAdvancedAnalysis
}

export interface ChatMessage {
  id: string
  type: ChatRole
  content: string
  timestamp: Date
  senderName?: string
}
