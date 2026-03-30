import axios from 'axios'

const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:48080'

export const request = axios.create({
  baseURL: `${baseURL}/admin-api`,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

request.interceptors.response.use(
  response => {
    const data = response.data

    if (data && typeof data === 'object' && 'code' in data) {
      if (data.code !== 0) {
        return Promise.reject(new Error(data.msg || '接口返回异常'))
      }

      return 'data' in data ? data.data : data
    }

    return data
  },
  error => {
    if (axios.isAxiosError(error)) {
      const message =
        (typeof error.response?.data?.msg === 'string' && error.response.data.msg) ||
        error.message ||
        '网络请求失败'

      return Promise.reject(new Error(message))
    }

    return Promise.reject(error instanceof Error ? error : new Error('网络请求失败'))
  }
)
