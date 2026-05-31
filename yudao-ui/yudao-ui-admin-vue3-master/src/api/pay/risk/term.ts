import axios from 'axios'

const API_PREFIX = `${import.meta.env.VITE_BASE_URL || 'http://localhost:48080'}${import.meta.env.VITE_API_URL || '/admin-api'}`

export interface PayRiskTermVO {
  id?: number
  term: string
  category?: string
  status: number
  description?: string
  sourceType?: string
  hitCount?: number
  firstSeenTime?: string
  lastHitTime?: string
  firstRecordId?: number
  createTime?: string
}

export interface PayRiskTermPageReq {
  pageNo?: number
  pageSize?: number
  term?: string
  category?: string
  status?: number
  sourceType?: string
  firstSeenTime?: string[]
}

export const getPayRiskTermPage = async (params: PayRiskTermPageReq) => {
  const resp = await axios.get(`${API_PREFIX}/pay/risk/term/page`, { params })
  return resp.data?.data ?? resp.data
}

export const getPayRiskTerm = async (id: number) => {
  const resp = await axios.get(`${API_PREFIX}/pay/risk/term/get`, { params: { id } })
  return resp.data?.data ?? resp.data
}

export const createPayRiskTerm = async (data: PayRiskTermVO) => {
  const resp = await axios.post(`${API_PREFIX}/pay/risk/term/create`, data)
  return resp.data?.data ?? resp.data
}

export const updatePayRiskTerm = async (data: PayRiskTermVO) => {
  const resp = await axios.put(`${API_PREFIX}/pay/risk/term/update`, data)
  return resp.data?.data ?? resp.data
}

export const deletePayRiskTerm = async (id: number) => {
  const resp = await axios.delete(`${API_PREFIX}/pay/risk/term/delete`, { params: { id } })
  return resp.data?.data ?? resp.data
}

export const TERM_CATEGORIES = [
  { label: '诈骗话术', value: 'FRAUD_SCRIPT' },
  { label: '链接/域名', value: 'LINK' },
  { label: '行为特征', value: 'BEHAVIOR' },
  { label: '支付转账', value: 'PAYMENT' },
  { label: '其他', value: 'OTHER' }
] as const

export const TERM_SOURCE_LABEL: Record<string, string> = {
  MANUAL: '人工录入',
  AUTO_ASSESS: '聊天自动'
}
