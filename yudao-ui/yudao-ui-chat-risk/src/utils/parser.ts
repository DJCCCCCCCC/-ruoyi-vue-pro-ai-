export function tryParseJSON(
  text: string
): { success: boolean; data?: Record<string, unknown>; error?: string } {
  try {
    const data = JSON.parse(text)

    if (!data || typeof data !== 'object' || Array.isArray(data)) {
      return {
        success: false,
        error: '请输入单个支付对象的 JSON，例如 { "amount": 99.8 }'
      }
    }

    return { success: true, data }
  } catch (error) {
    return {
      success: false,
      error: error instanceof Error ? error.message : '无效的 JSON 格式'
    }
  }
}

export function formatJSON(obj: unknown): string {
  return JSON.stringify(obj, null, 2)
}
