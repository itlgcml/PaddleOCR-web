/** 认证状态（token + 当前用户），Pinia setup store */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { fetchCurrentUser, submitLogin, submitLogout } from '@/api/auth'
import type { LoginDTO, UserInfoVO } from '@/types/auth'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from '@/utils/token'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(getAccessToken())
  const refreshToken = ref<string | null>(getRefreshToken())
  const user = ref<UserInfoVO | null>(null)

  const isLoggedIn = computed(() => accessToken.value !== null)
  const isAdmin = computed(() => user.value?.roles.includes('ADMIN') ?? false)

  /** 登录成功：持久化双令牌并写入用户态 */
  async function login(payload: LoginDTO): Promise<void> {
    const data = await submitLogin(payload)
    setTokens(data.accessToken, data.refreshToken)
    accessToken.value = data.accessToken
    refreshToken.value = data.refreshToken
    user.value = data.user
  }

  /** 刷新当前用户信息（角色/机构/禁用状态即时生效） */
  async function fetchMe(): Promise<void> {
    user.value = await fetchCurrentUser()
  }

  /** 登出：双令牌吊销（网络失败也保证本地态清空） */
  async function logout(): Promise<void> {
    const token = refreshToken.value
    try {
      await submitLogout(token ? { refreshToken: token } : undefined)
    } finally {
      clearAuth()
    }
  }

  /** 清空本地登录态（request.ts 401 分支也会调用） */
  function clearAuth(): void {
    clearTokens()
    accessToken.value = null
    refreshToken.value = null
    user.value = null
  }

  return { accessToken, refreshToken, user, isLoggedIn, isAdmin, login, fetchMe, logout, clearAuth }
})
