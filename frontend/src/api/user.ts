/** 用户管理接口（/api/users/**，ADMIN） */
import request from '@/api/request'
import type { PageQuery, PageResult } from '@/types/api'
import type { UserOrgAssignDTO, UserPageVO, UserRoleAssignDTO, UserStatusDTO } from '@/types/auth'

/** 用户分页查询参数 */
export interface UserPageQuery extends PageQuery {
  /** 用户名/昵称模糊匹配 */
  username?: string
  /** 按机构精确过滤 */
  orgId?: string
  /** 1=启用 0=禁用 */
  status?: number
}

/** 用户分页查询 */
export function fetchUserPage(params: UserPageQuery): Promise<PageResult<UserPageVO>> {
  return request.get<unknown, PageResult<UserPageVO>>('/users/page', { params })
}

/** 调整用户所属机构（目标机构须存在且启用） */
export function submitUserOrg(id: string, data: UserOrgAssignDTO): Promise<void> {
  return request.put<unknown, void>(`/users/org/${id}`, data)
}

/** 分配角色（全量替换；不允许对当前登录用户自己操作，后端 40300） */
export function submitUserRoles(id: string, data: UserRoleAssignDTO): Promise<void> {
  return request.put<unknown, void>(`/users/roles/${id}`, data)
}

/** 启用/禁用用户（禁用即时生效；不允许禁用自己，后端 40300） */
export function submitUserStatus(id: string, data: UserStatusDTO): Promise<void> {
  return request.put<unknown, void>(`/users/status/${id}`, data)
}
