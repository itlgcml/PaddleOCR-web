/** 角色管理接口（/api/roles/**，ADMIN） */
import request from '@/api/request'
import type { PageQuery, PageResult } from '@/types/api'
import type { RoleSaveDTO, RoleUpdateDTO, RoleVO } from '@/types/auth'

/** 角色分页查询参数 */
export interface RolePageQuery extends PageQuery {
  /** 按 roleCode/roleName 模糊匹配 */
  keyword?: string
}

/** 角色分页查询 */
export function fetchRolePage(params: RolePageQuery): Promise<PageResult<RoleVO>> {
  return request.get<unknown, PageResult<RoleVO>>('/roles/page', { params })
}

/** 全量启用角色（供用户分配角色下拉框） */
export function fetchAllRoles(): Promise<RoleVO[]> {
  return request.get<unknown, RoleVO[]>('/roles/all')
}

/** 新增角色，返回新角色 id */
export function submitRoleCreate(data: RoleSaveDTO): Promise<string> {
  return request.post<unknown, string>('/roles/create', data)
}

/** 修改角色（内置角色后端拒绝 40031） */
export function submitRoleUpdate(id: string, data: RoleUpdateDTO): Promise<void> {
  return request.put<unknown, void>(`/roles/update/${id}`, data)
}

/** 删除角色（被绑定 40030 / 内置角色 40031 时后端拦截） */
export function submitRoleRemove(id: string): Promise<void> {
  return request.delete<unknown, void>(`/roles/delete/${id}`)
}
