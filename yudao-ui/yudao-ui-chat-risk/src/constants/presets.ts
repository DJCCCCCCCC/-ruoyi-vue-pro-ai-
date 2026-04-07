import type { ChatRole } from '@/types'

export interface RiskPresetMessage {
  type: Exclude<ChatRole, 'system'>
  content: string
  senderName?: string
}

export interface RiskPresetSimulationParticipant {
  userId?: string
  memberId?: string
  merchantNo?: string
  accountNo?: string
  accountName?: string
  name?: string
  mobile?: string
  deviceId?: string
  ip?: string
  roleTag?: string
  relationToCounterparty?: string
}

export interface RiskPresetSimulation {
  relationLabel: string
  relationType: string
  amount: number
  currency?: string
  payer: RiskPresetSimulationParticipant
  payee: RiskPresetSimulationParticipant
}

export interface RiskPreset {
  id: string
  title: string
  summary: string
  messages: RiskPresetMessage[]
  simulation: RiskPresetSimulation
}

export const riskPresets: RiskPreset[] = [
  {
    id: 'friend-split-bill',
    title: '熟人AA场景',
    summary: '父亲和儿子之间的正常AA对话，低风险关系样本。',
    messages: [
      { type: 'peer', senderName: '爸爸', content: '昨晚火锅我先垫了，你把 96 元转我就行。' },
      { type: 'self', senderName: '儿子', content: '好，我等会直接微信转给你。' },
      { type: 'peer', senderName: '爸爸', content: '不用点链接，直接转就行，备注火锅AA。' }
    ],
    simulation: {
      relationLabel: '父子亲属AA',
      relationType: 'KINSHIP',
      amount: 96,
      currency: 'CNY',
      payer: {
        userId: 'son-1001',
        name: '儿子',
        mobile: '13800001111',
        deviceId: 'family-device-son',
        ip: '10.8.1.11',
        roleTag: '家庭成员',
        relationToCounterparty: '父亲'
      },
      payee: {
        userId: 'father-2001',
        accountNo: 'wx-father-01',
        accountName: '家庭零钱账户',
        name: '爸爸',
        mobile: '13800002222',
        deviceId: 'family-device-father',
        ip: '10.8.1.10',
        roleTag: '家庭成员',
        relationToCounterparty: '儿子'
      }
    }
  },
  {
    id: 'fake-customer-service',
    title: '客服退款',
    summary: '对方冒充平台客服，以退款为由发送验证链接。',
    messages: [
      { type: 'peer', senderName: '退款客服', content: '你好，我是平台客服，你的订单异常，需要马上处理退款。' },
      {
        type: 'peer',
        senderName: '退款客服',
        content: '请 3 分钟内打开这个链接完成验证：https://pay-safe-refund.example.com/verify?id=8831'
      },
      { type: 'peer', senderName: '退款客服', content: '超时系统会自动扣款，你先不要联系官方。' }
    ],
    simulation: {
      relationLabel: '冒充客服退款',
      relationType: 'FAKE_CUSTOMER_SERVICE',
      amount: 1888,
      currency: 'CNY',
      payer: {
        userId: 'victim-3001',
        name: '用户',
        mobile: '13900003333',
        deviceId: 'victim-device-01',
        ip: '103.24.10.23',
        roleTag: '退款申请人',
        relationToCounterparty: '平台客服'
      },
      payee: {
        merchantNo: 'fake-cs-01',
        accountNo: 'refund-verify-8831',
        accountName: '安全退款验证',
        name: '退款客服',
        deviceId: 'fraud-device-cs',
        ip: '103.24.10.88',
        roleTag: '冒充客服',
        relationToCounterparty: '待退款用户'
      }
    }
  },
  {
    id: 'urgent-transfer',
    title: '高压转账',
    summary: '对方不断施压，要求用户立刻完成大额转账。',
    messages: [
      { type: 'peer', senderName: '财务', content: '我是你朋友介绍的财务，现在公司账户被冻结了，你先帮我垫 5000。' },
      {
        type: 'peer',
        senderName: '财务',
        content: '直接点这个付款链接，填银行卡就能转：https://quick-pay-now.example.com/u/7788'
      },
      { type: 'peer', senderName: '财务', content: '这件事很急，别打电话确认，十分钟内搞定我马上还你。' }
    ],
    simulation: {
      relationLabel: '高压紧急转账',
      relationType: 'HIGH_PRESSURE_TRANSFER',
      amount: 5000,
      currency: 'CNY',
      payer: {
        userId: 'employee-4001',
        name: '用户',
        mobile: '13900004444',
        deviceId: 'employee-device-01',
        ip: '111.23.9.6',
        roleTag: '被施压转账人',
        relationToCounterparty: '陌生财务'
      },
      payee: {
        accountNo: 'quick-pay-7788',
        accountName: '紧急财务代收',
        name: '财务',
        mobile: '13600005555',
        deviceId: 'fraud-device-urgent',
        ip: '111.23.9.16',
        roleTag: '陌生收款方',
        relationToCounterparty: '临时转账对象'
      }
    }
  }
]
