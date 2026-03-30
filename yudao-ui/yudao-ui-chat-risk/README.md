# AI 支付风险检测聊天界面

基于 Vue 3 + TypeScript + Vite 的聊天式支付风险检测前端。

## 功能特性

- 类似微信的聊天界面
- 对话式支付风险检测
- 实时风险评估结果展示
- 支持 JSON 格式的支付数据输入
- 风险等级可视化（LOW/MEDIUM/HIGH/CRITICAL）
- 响应式设计，支持移动端

## 技术栈

- Vue 3.5
- TypeScript 5.3
- Vite 5.1
- Pinia 2.1 (状态管理)
- Axios 1.9 (HTTP 客户端)
- Dayjs 1.11 (时间处理)
- Sass (样式预处理)

## 开发

```bash
# 安装依赖
pnpm install

# 启动开发服务器
pnpm dev

# 构建生产版本
pnpm build

# 预览生产构建
pnpm preview
```

## 环境配置

创建 `.env.development` 文件：

```env
VITE_API_BASE_URL=http://localhost:48080
```

创建 `.env.production` 文件：

```env
VITE_API_BASE_URL=https://your-production-domain.com
```

## 使用说明

1. 在输入框中输入 JSON 格式的支付数据
2. 点击发送按钮或按 Enter 键
3. 系统会自动调用后端 API 进行风险评估
4. 评估结果会以卡片形式展示在聊天界面中

### 输入示例

```json
{
  "orderNo": "TEST001",
  "userId": "user123",
  "amount": 99.00,
  "payType": "WECHAT"
}
```

## 项目结构

```
src/
├── api/              # API 接口
├── assets/           # 静态资源
├── components/       # Vue 组件
├── composables/      # 组合式函数
├── stores/           # Pinia 状态管理
├── types/            # TypeScript 类型定义
├── utils/            # 工具函数
├── views/            # 页面组件
├── App.vue           # 根组件
└── main.ts           # 入口文件
```

## API 接口

后端 API 地址：`POST /admin-api/pay/risk/assess`

请求格式：
```typescript
{
  ip?: string
  paymentData: Record<string, any>
}
```

响应格式：
```typescript
{
  riskScore: number
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  deepAnalysis: string
  riskFactors?: string[]
  ipInfo?: any
}
```

## License

MIT
