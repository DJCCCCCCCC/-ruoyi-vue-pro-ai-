import axios from 'axios'

const API_PREFIX = `${import.meta.env.VITE_BASE_URL || 'http://localhost:48080'}${import.meta.env.VITE_API_URL || '/admin-api'}`

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
  whoisInfo?: any
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
  createTime: string
}

export interface PayRiskAssessRecordPageReqVO {
  pageNo: number
  pageSize: number
  riskLevel?: string
  scene?: string
  source?: string
  ip?: string
  createTime?: [string, string]
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
