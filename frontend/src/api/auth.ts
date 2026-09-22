/** 认证域接口（/api/auth/**） */
import request from '@/api/request'
import type { LoginDTO, LoginVO, LogoutDTO, RegisterDTO, TokenVO, UserInfoVO } from '@/types/auth'

/** 注册（匿名）；orgId 缺省挂 DEFAULT 默认机构 */
export function submitRegister(data: RegisterDTO): Promise<UserInfoVO> {
  return request.post<unknown, UserInfoVO>('/auth/register', data)
}

/** 登录，签发 accessToken / refreshToken */
export function submitLogin(data: LoginDTO): Promise<LoginVO> {
  return request.post<unknown, LoginVO>('/auth/login', data)
}

/** 刷新令牌（拦截器 401 静默续期走裸 axios，本函数供显式调用场景） */
export function submitRefresh(refreshToken: string): Promise<TokenVO> {
  return request.post<unknown, TokenVO>('/auth/refresh', { refreshToken })
}

/** 获取当前登录用户（实时查库，角色/禁用即时生效） */
export function fetchCurrentUser(): Promise<UserInfoVO> {
  return request.get<unknown, UserInfoVO>('/auth/me')
}

/** 登出：传入 refreshToken 则双令牌一并吊销 */
export function submitLogout(data?: LogoutDTO): Promise<void> {
  return request.post<unknown, void>('/auth/logout', data ?? {})
}
