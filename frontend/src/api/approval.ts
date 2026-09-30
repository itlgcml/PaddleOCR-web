/** 流程配置接口（/api/approval/process/**，ADMIN） */
import request from '@/api/request'
import type { PageQuery, PageResult } from '@/types/api'
import type {
  ProcessDesignSaveDTO,
  ProcessDesignVO,
  ProcessDetailVO,
  ProcessSaveDTO,
  ProcessStatus,
  ProcessUpdateDTO,
  ProcessVersionVO,
  ProcessVO,
} from '@/types/approval'

/** 流程分页查询参数 */
export interface ProcessPageQuery extends PageQuery {
  /** 按 code/name 前缀匹配 */
  keyword?: string
  /** 状态精确过滤 */
  status?: ProcessStatus
}

/** 流程分页查询 */
export function fetchProcessPage(params: ProcessPageQuery): Promise<PageResult<ProcessVO>> {
  return request.get<unknown, PageResult<ProcessVO>>('/approval/process/page', { params })
}

/** 流程详情 */
export function fetchProcessDetail(id: string): Promise<ProcessDetailVO> {
  return request.get<unknown, ProcessDetailVO>(`/approval/process/${id}`)
}

/** 查询流程设计（缺省草稿，可指定已发布版本） */
export function fetchProcessDesign(id: string, version?: string): Promise<ProcessDesignVO> {
  return request.get<unknown, ProcessDesignVO>(`/approval/process/${id}/design`, {
    params: version === undefined ? undefined : { version },
  })
}

/** 流程版本列表（按发布时间倒序） */
export function fetchProcessVersions(id: string): Promise<ProcessVersionVO[]> {
  return request.get<unknown, ProcessVersionVO[]>(`/approval/process/${id}/versions`)
}

/** 新增流程（初始 DRAFT），返回新流程 id */
export function submitProcessCreate(data: ProcessSaveDTO): Promise<string> {
  return request.post<unknown, string>('/approval/process/create', data)
}

/** 更新基本信息（仅 DRAFT 态，非 DRAFT 后端拒绝 40043） */
export function submitProcessUpdate(id: string, data: ProcessUpdateDTO): Promise<void> {
  return request.put<unknown, void>(`/approval/process/update/${id}`, data)
}

/** 保存流程设计草稿（任意状态可存；重复提交后端拒绝 40045） */
export function submitProcessDesign(id: string, data: ProcessDesignSaveDTO): Promise<void> {
  return request.put<unknown, void>(`/approval/process/${id}/design`, data)
}

/** 发布流程（设计不合法后端拒绝 40042），返回本次发布版本号 */
export function submitProcessPublish(id: string): Promise<string> {
  return request.post<unknown, string>(`/approval/process/${id}/publish`)
}

/** 停用流程（仅 PUBLISHED 态，否则后端拒绝 40043） */
export function submitProcessDisable(id: string): Promise<void> {
  return request.post<unknown, void>(`/approval/process/${id}/disable`)
}

/** 逻辑删除流程（仅 DRAFT 态，否则后端拒绝 40043） */
export function submitProcessRemove(id: string): Promise<void> {
  return request.delete<unknown, void>(`/approval/process/${id}`)
}
