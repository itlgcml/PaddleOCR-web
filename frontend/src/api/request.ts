/**
 * 唯一 axios 实例（项目铁律：禁止二次 axios.create）。
 *
 * - 请求拦截器：自动注入 `Authorization: Bearer <accessToken>`
 * - 响应拦截器：`code === 0` 解包返回业务数据本身（调用方拿到的是 data，非 ApiResponse）
 * - 业务失败（HTTP 200 + code 非零）：统一 ElMessage 提示并 reject，业务代码只写成功分支
 * - HTTP 401 分支（按后端 §9.1.5 前端约定）：
 *   - 40102（令牌过期）且持有 refreshToken：single-flight 静默刷新并重放原请求
 *   - 40100 / 40101 / 40103 / 40023：清令牌跳登录页（40023 提示"账号已被禁用"）
 * - HTTP 403 / 40300：提示无权限
 */
import axios from 'axios'
import type { AxiosError, InternalAxiosRequestConfig } from 'axios'
import type { ApiResponse } from '@/types/api'
import { getAccessToken, getRefreshToken, setAccessToken } from '@/utils/token'

/** 业务成功码（非 HTTP 语义码 200） */
export const SUCCESS_CODE = 0

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

/** 需要鉴权类跳转登录的错误码 */
const TOKEN_EXPIRED = 40102

const service = axios.create({
  baseURL: BASE_URL,
  timeout: 60_000,
})

/** 允许标记"已重放"，防止刷新后二次 401 无限循环 */
type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean }

/** 并发 401 共享同一次刷新（single-flight） */
let refreshPromise: Promise<string> | null = null
/** 防止并发失效时重复弹提示/重复跳转 */
let forcingLogout = false

/**
 * 静默刷新 accessToken。
 * 用裸 axios 调用，避免走本实例拦截器造成递归；V1 不轮换 refreshToken。
 */
function refreshAccessToken(): Promise<string> {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      const refreshToken = getRefreshToken()
      if (!refreshToken) {
        throw new Error('缺少刷新令牌')
      }
      const resp = await axios.post<ApiResponse<{ accessToken: string }>>(
        `${BASE_URL}/auth/refresh`,
        { refreshToken },
        { timeout: 15_000 },
      )
      const body = resp.data
      if (body.code !== SUCCESS_CODE || !body.data) {
        throw new Error(body.message || '刷新令牌无效')
      }
      setAccessToken(body.data.accessToken)
      return body.data.accessToken
    })().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

/** 清空登录态并跳转登录页（携带 redirect 回跳参数）；返回 reject 终止原调用链 */
async function forceLogout(message: string): Promise<never> {
  if (forcingLogout) {
    return Promise.reject(new Error(message))
  }
  forcingLogout = true
  try {
    ElMessage.error(message)
    // 动态导入规避 request ↔ store/router 的模块循环
    const [{ useAuthStore }, { default: router }] = await Promise.all([
      import('@/stores/auth'),
      import('@/router'),
    ])
    useAuthStore().clearAuth()
    const current = router.currentRoute.value
    if (current.name !== 'Login') {
      const query = current.path === '/' ? {} : { redirect: current.fullPath }
      await router.push({ name: 'Login', query })
    }
  } finally {
    forcingLogout = false
  }
  return Promise.reject(new Error(message))
}

/** HTTP 层错误（含 401/403 鉴权分支）统一处理 */
async function handleHttpError(error: AxiosError<ApiResponse<unknown>>): Promise<unknown> {
  const status = error.response?.status
  const body = error.response?.data
  const code = body?.code
  const config = error.config as RetriableConfig | undefined

  if (status === 401) {
    // 令牌过期：尝试静默刷新并重放原请求（单飞，防并发重复刷新）
    if (code === TOKEN_EXPIRED && config && !config._retried && getRefreshToken()) {
      try {
        const token = await refreshAccessToken()
        config._retried = true
        config.headers.set('Authorization', `Bearer ${token}`)
        return service.request(config)
      } catch {
        return forceLogout('登录已失效，请重新登录')
      }
    }
    if (code === 40023) {
      return forceLogout('账号已被禁用')
    }
    return forceLogout(body?.message || '请先登录')
  }

  if (status === 403 || code === 40300) {
    ElMessage.error(body?.message || '无权限访问')
    return Promise.reject(error)
  }

  ElMessage.error(body?.message || error.message || '网络异常，请稍后重试')
  return Promise.reject(error)
}

service.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

service.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>
    if (body !== null && typeof body === 'object' && 'code' in body) {
      if (body.code === SUCCESS_CODE) {
        // 解包：调用方直接拿到业务数据（配合 request.post<unknown, T> 泛型），
        // 此处 as never 仅用于满足 axios 拦截器返回类型契约
        return body.data as never
      }
      const message = body.message || '请求失败'
      ElMessage.error(message)
      return Promise.reject(new Error(message))
    }
    return response.data
  },
  (error: AxiosError<ApiResponse<unknown>>) => handleHttpError(error),
)

export default service
