import axios from 'axios'

const API_PREFIX = `${import.meta.env.VITE_BASE_URL || 'http://localhost:48080'}${import.meta.env.VITE_API_URL || '/admin-api'}`

export interface PayRiskAssessPaymentData {
  [key: string]: any
}

export interface PayRiskAssessReqVO {
  ip?: string
  paymentData: PayRiskAssessPaymentData
}

export interface PayRiskDecisionResult {
  policyVersion?: string
  recommendedAction?: 'ALLOW' | 'MANUAL_REVIEW' | 'BLOCK' | string
  reasonCodes?: string[]
  reasonMessages?: string[]
  requiresHumanReview?: boolean
  reviewHint?: string
}

export interface PayRiskAssessRespVO {
  riskScore: number
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  deepAnalysis: string
  riskFactors?: string[]
  ipInfo?: any
  whoisInfo?: any
  behaviorInfo?: any
  topologyInfo?: PayRiskRelationTopology
  llmReport?: PayRiskLlmReport
  advancedAnalysis?: PayRiskAdvancedAnalysis
  caseSimilarityBonus?: number
  decision?: PayRiskDecisionResult
  embeddedImageCount?: number
  imageOcrServiceEnabled?: boolean
  imageOcrApiCallCount?: number
  imageOcrValidTextCount?: number
  imageOcrSummary?: string
  imageOcrTextPreview?: string
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
  fraudFamily?: string
  variantLabel?: string
  noveltyLevel?: 'KNOWN_VARIANT' | 'EMERGING_PATTERN' | 'NOVEL_COMBO' | string
  noveltyScore?: number
  verdict?: string
  confidence?: 'LOW' | 'MEDIUM' | 'HIGH' | string
  evidence?: string[]
  suspiciousEntities?: string[]
  preventionFocus?: string[]
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
  caseMatches?: Array<{
    recordId?: number
    scene?: string
    source?: string
    riskLevel?: string
    riskScore?: number
    similarity?: number
    bonusScore?: number
    matchedReasons?: string
    summary?: string
  }>
  interventions?: Array<{
    priority?: string
    type?: string
    title?: string
    description?: string
    automationLevel?: string
  }>
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
    riskLevel?: string
    tags?: string[]
    meta?: Record<string, any>
  }>
  edges?: Array<{
    source: string
    target: string
    type: string
    label?: string
    riskLevel?: string
    count?: number
    amount?: number | string
    meta?: Record<string, any>
  }>
  signals?: Array<{
    code: string
    level: string
    title: string
    description: string
    score?: number
    relatedNodeIds?: string[]
  }>
}

export interface PayRiskAssessRecordVO {
  id: number
  source?: string
  scene?: string
  ip?: string
  riskScore: number
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  riskFactorsJson?: string
  deepAnalysis?: string
  paymentDataJson?: string
  ipInfoJson?: string
  whoisInfoJson?: string
  behaviorInfoJson?: string
  topologyInfoJson?: string
  llmReportJson?: string
  advancedAnalysisJson?: string
  decisionAction?: string
  decisionJson?: string
  reviewStatus?: string
  reviewRemark?: string
  reviewer?: string
  reviewTime?: string
  createTime: string
}

export interface PayRiskAssessRecordPageReqVO {
  pageNo: number
  pageSize: number
  /** 精确按评估记录编号筛选（用于从风险词穿透等入口直达工单） */
  id?: number
  riskLevel?: string
  scene?: string
  source?: string
  ip?: string
  reviewStatus?: string
  createTime?: [string, string]
}

export interface PayRiskTodayNewTermItem {
  term: string
  todayHitCount: number
  relatedRecordIds: number[]
}

export interface PayRiskTodayNewTermsResult {
  terms: PayRiskTodayNewTermItem[]
}

export interface PayRiskTermRelatedTicket {
  id: number
  scene?: string
  source?: string
  createTime?: string
  riskLevel?: string
  reviewStatus?: string
  decisionAction?: string
  conversationSummary?: string
}

export interface PayRiskTodayNewTermDetailResult {
  term: string
  tickets: PayRiskTermRelatedTicket[]
}

export interface PayRiskAssessRecordPageResult {
  list: PayRiskAssessRecordVO[]
  total: number
}

const unwrapPageResult = (resp: any): PayRiskAssessRecordPageResult => {
  if (resp?.list !== undefined && resp?.total !== undefined) {
    return resp
  }
  if (resp?.data?.list !== undefined && resp?.data?.total !== undefined) {
    return resp.data
  }
  if (resp?.data?.data?.list !== undefined && resp?.data?.data?.total !== undefined) {
    return resp.data.data
  }
  return { list: [], total: 0 }
}

export const assessPayRisk = async (data: PayRiskAssessReqVO) => {
  const resp = await axios.post(`${API_PREFIX}/pay/risk/assess`, data)
  return resp?.data?.data ?? resp?.data ?? resp
}

export const getPayRiskAssessRecordPage = async (params: PayRiskAssessRecordPageReqVO) => {
  const resp = await axios.get(`${API_PREFIX}/pay/risk/page`, { params })
  return unwrapPageResult(resp?.data)
}

export const deletePayRiskAssessRecord = async (id: number) => {
  const resp = await axios.post(`${API_PREFIX}/pay/risk/delete`, undefined, { params: { id } })
  return resp?.data?.data ?? resp?.data ?? resp
}

export const clearPayRiskAssessRecords = async () => {
  const resp = await axios.post(`${API_PREFIX}/pay/risk/clear`)
  return resp?.data?.data ?? resp?.data ?? resp
}

export interface PayRiskAssessReviewReqVO {
  id: number
  reviewAction: 'PASS' | 'BLOCK' | 'DISMISS'
  remark?: string
}

export const reviewPayRiskAssessRecord = async (data: PayRiskAssessReviewReqVO) => {
  const resp = await axios.post(`${API_PREFIX}/pay/risk/review`, data)
  return resp?.data?.data ?? resp?.data ?? resp
}

export const getPayRiskTodayNewTerms = async (): Promise<PayRiskTodayNewTermsResult> => {
  const resp = await axios.get(`${API_PREFIX}/pay/risk/today-new-risk-terms`)
  const data = resp?.data?.data ?? resp?.data ?? resp
  return { terms: data?.terms ?? [] }
}

export const getPayRiskTodayNewTermDetail = async (
  term: string
): Promise<PayRiskTodayNewTermDetailResult> => {
  const resp = await axios.post(`${API_PREFIX}/pay/risk/today-new-risk-term-detail`, { term })
  const data = resp?.data?.data ?? resp?.data ?? resp
  return {
    term: data?.term ?? term,
    tickets: data?.tickets ?? []
  }
}
