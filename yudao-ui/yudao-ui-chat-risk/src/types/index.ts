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

export interface PayRiskLlmPersonaProfile {
  summary?: string
  claimedOrImpliedRole?: string
  inferredArchetype?: string
  communicationTraits?: string[]
  pressureAndControlSignals?: string[]
}

export interface PayRiskLlmTailoredUserGuidance {
  whyLikelyScamPlainLanguage?: string
  preventionTipsForThisUser?: string[]
  reassuranceLine?: string
}

export interface PayRiskLlmReport {
  mode?: string
  summary?: string
  verdict?: string
  confidence?: RiskLevel | 'LOW' | 'MEDIUM' | 'HIGH'
  evidence?: string[]
  suspiciousEntities?: string[]
  recommendations?: string[]
  personaProfile?: PayRiskLlmPersonaProfile
  tailoredUserGuidance?: PayRiskLlmTailoredUserGuidance
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
  /** 内嵌图片数量；无图时通常不传 */
  embeddedImageCount?: number
  imageOcrServiceEnabled?: boolean
  imageOcrApiCallCount?: number
  imageOcrValidTextCount?: number
  /** 图片 OCR 处理说明（中文） */
  imageOcrSummary?: string
  /** OCR 正文预览（截断） */
  imageOcrTextPreview?: string
}

/** 单条聊天里的图片（压缩后为 JPEG data URL，供后端 OCR 扫描） */
export interface ChatMessageImage {
  mime: string
  dataUrl: string
}

/** 语音消息（本地录音 data URL，可播放；转写文字在 content） */
export interface ChatMessageVoice {
  mime: string
  dataUrl: string
  durationSec: number
}

export interface ChatMessage {
  id: string
  type: ChatRole
  content: string
  timestamp: Date
  senderName?: string
  /** 消息来源，提交 assess 时写入 paymentData.messages[].source */
  source?: 'voice_asr' | 'text' | 'image'
  /** 语音条，与 content（转写）一起展示 */
  voice?: ChatMessageVoice
  /** 与文字同气泡展示；提交分析时会写入 paymentData.messages[].imageDataUrls */
  images?: ChatMessageImage[]
}
