/**
 * 全局唯一 axios 实例（禁止在别处二次 axios.create）
 * 契约：后端 HTTP 恒为 200，业务成败由 body.code 表达（0 = 成功）
 */
import axios from 'axios'
import type { AxiosError, AxiosInstance, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import type { ApiResponse } from '@/types/api'

/** 后端 ResultCode.SUCCESS */
const SUCCESS_CODE = 0

const instance: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 60_000,
})

instance.interceptors.response.use(
  (response: AxiosResponse<ApiResponse<unknown>>) => {
    const body = response.data
    if (body.code !== SUCCESS_CODE) {
      ElMessage.error(body.message || '请求失败，请稍后重试')
      return Promise.reject(new Error(body.message || `业务错误码：${body.code}`))
    }
    // 解包：配合 request.get<T, T> 用法，调用方直接拿到 data
    return body.data as unknown as AxiosResponse
  },
  (error: AxiosError<ApiResponse<null>>) => {
    if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请稍后重试')
    } else if (error.response?.data?.message) {
      // 未被全局异常处理器覆盖的 HTTP 层错误响应
      ElMessage.error(error.response.data.message)
    } else {
      ElMessage.error('网络异常，请检查网络后重试')
    }
    console.error('[api] 请求失败:', error.message)
    return Promise.reject(error)
  },
)

export default instance
