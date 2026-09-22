/** 机构管理接口（/api/orgs/**，ADMIN） */
import request from '@/api/request'
import type { OrgSaveDTO, OrgTreeVO, OrgUpdateDTO } from '@/types/auth'

/** 机构树（后端已组装为递归树，前端直接使用） */
export function fetchOrgTree(): Promise<OrgTreeVO[]> {
  return request.get<unknown, OrgTreeVO[]>('/orgs/tree')
}

/** 新增机构，返回新机构 id */
export function submitOrgCreate(data: OrgSaveDTO): Promise<string> {
  return request.post<unknown, string>('/orgs/create', data)
}

/** 修改机构（仅名称/排序/状态；orgCode 与 parentId 后端不可改） */
export function submitOrgUpdate(id: string, data: OrgUpdateDTO): Promise<void> {
  return request.put<unknown, void>(`/orgs/update/${id}`, data)
}

/** 删除机构（仍有子机构 40027 / 在职员工 40028 时后端拦截） */
export function submitOrgRemove(id: string): Promise<void> {
  return request.delete<unknown, void>(`/orgs/delete/${id}`)
}
