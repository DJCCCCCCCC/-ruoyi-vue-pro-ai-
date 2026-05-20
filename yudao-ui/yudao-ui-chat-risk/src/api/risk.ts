import { request } from '@/utils/request'
import type { PayRiskAssessReqVO, PayRiskAssessRespVO } from '@/types'

export interface PayRiskSpeechTranscribeRespVO {
  text: string
  model?: string
}

export const assessPayRisk = async (data: PayRiskAssessReqVO): Promise<PayRiskAssessRespVO> => {
  return await request.post('/pay/risk/assess', data)
}

export const transcribeSpeech = async (file: Blob, filename = 'recording.webm'): Promise<PayRiskSpeechTranscribeRespVO> => {
  const form = new FormData()
  form.append('file', file, filename)
  // 勿手动设置 Content-Type，由浏览器自动附带 multipart boundary
  return await request.post('/pay/risk/speech-transcribe', form, {
    timeout: 120000
  })
}
