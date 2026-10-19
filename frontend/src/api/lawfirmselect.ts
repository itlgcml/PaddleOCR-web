/** 律所选聘管理接口（/api/law-firm-select/**，登录即可访问） */
import request from '@/api/request'
import type { PageQuery, PageResult } from '@/types/api'
import type { LawFirmSelectSaveDTO, LawFirmSelectSubmitDTO, LawFirmSelectVO } from '@/types/lawfirmselect'

/** 律所选聘分页查询参数 */
export interface LawFirmSelectPageQuery extends PageQuery {
  /** 按标题前缀匹配 */
  title?: string
}

/** 律所选聘分页查询 */
export function fetchLawFirmSelectPage(params: LawFirmSelectPageQuery): Promise<PageResult<LawFirmSelectVO>> {
  return request.get<unknown, PageResult<LawFirmSelectVO>>('/law-firm-select/page', { params })
}

/** 律所选聘详情（记录不存在后端拒绝 40050） */
export function fetchLawFirmSelectDetail(id: string): Promise<LawFirmSelectVO> {
  return request.get<unknown, LawFirmSelectVO>(`/law-firm-select/detail/${id}`)
}

/** 新增律所选聘，返回新记录 id */
export function submitLawFirmSelectCreate(data: LawFirmSelectSaveDTO): Promise<string> {
  return request.post<unknown, string>('/law-firm-select/create', data)
}

/** 新增律所选聘并直接提交审批（后端单事务：建单+发起任一失败整体回滚），返回流程实例 ID */
export function submitLawFirmSelectCreateAndSubmit(data: LawFirmSelectSubmitDTO): Promise<string> {
  return request.post<unknown, string>('/law-firm-select/create-and-submit', data)
}

/** 修改律所选聘（记录不存在后端拒绝 40050） */
export function submitLawFirmSelectUpdate(id: string, data: LawFirmSelectSaveDTO): Promise<void> {
  return request.put<unknown, void>(`/law-firm-select/update/${id}`, data)
}

/** 删除律所选聘（逻辑删除） */
export function submitLawFirmSelectRemove(id: string): Promise<void> {
  return request.delete<unknown, void>(`/law-firm-select/delete/${id}`)
}
