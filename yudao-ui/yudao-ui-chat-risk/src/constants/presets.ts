import type { ChatRole } from '@/types'

export interface RiskPresetMessage {
  type: Exclude<ChatRole, 'system'>
  content: string
}

export interface RiskPreset {
  id: string
  title: string
  summary: string
  messages: RiskPresetMessage[]
}

export const riskPresets: RiskPreset[] = [
  {
    id: 'friend-split-bill',
    title: '熟人AA场景',
    summary: '正常吃饭AA，对话自然',
    messages: [
      { type: 'peer', content: '昨晚火锅我先垫了，你把 96 元转我就行。' },
      { type: 'self', content: '好，你把收款码发我一下。' },
      { type: 'peer', content: '不用点链接，直接微信转给我就行。' }
    ]
  },
  {
    id: 'fake-customer-service',
    title: '客服退款链接',
    summary: '对方催你点退款链接',
    messages: [
      { type: 'peer', content: '你好，我是平台客服，你的订单异常，需要马上处理退款。' },
      {
        type: 'peer',
        content: '请 3 分钟内打开这个链接完成验证：https://pay-safe-refund.example.com/verify?id=8831'
      },
      { type: 'peer', content: '超时系统会自动扣款，你先别联系官方。' }
    ]
  },
  {
    id: 'urgent-transfer',
    title: '高压转账诱导',
    summary: '陌生人发收款链接并催促大额转账',
    messages: [
      { type: 'peer', content: '我是你朋友介绍的财务，现在公司账户被冻结了，你先帮我垫 5000。' },
      {
        type: 'peer',
        content: '直接点这个付款链接，填银行卡就能转：https://quick-pay-now.example.com/u/7788'
      },
      { type: 'peer', content: '这件事很急，别打电话确认，十分钟内搞定我马上还你。' }
    ]
  }
]
