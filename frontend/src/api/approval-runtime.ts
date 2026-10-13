/**
 * 审批运行态接口（/api/approval/{instance,task,cc}/**，登录即可访问）。
 * 与后端 FlowInstanceController / FlowTaskController / FlowCcController 一一对应。
 */
import request from '@/api/request'
import type { PageQuery, PageResult } from '@/types/api'
import type { ApprovalActionPayload } from '@/types/workflow'
import type {
  BizApprovalStatusVO,
  CcVO,
  InstanceDetailVO,
  InstanceItemVO,
  InstanceStartDTO,
  NextApproverQueryDTO,
  NextApproverVO,
  NodeButtonsQueryDTO,
  NodeButtonsVO,
  StartableProcessVO,
  TaskDetailVO,
  TaskVO,
  UserBriefVO,
} from '@/types/approval'

/** 我发起的实例分页查询参数 */
export interface InstancePageQuery extends PageQuery {
  /** 实例状态精确过滤 */
  status?: string
  keyword?: string
}

/** 任务分页查询参数（待办 / 已办共用） */
export interface TaskPageQuery extends PageQuery {
  keyword?: string
  /** 已办动作过滤：PASS/REJECT/RETURN/SUBMIT/TRANSFER/DELEGATE */
  action?: string
}

/** 抄送分页查询参数（待阅 / 已阅共用） */
export interface CcPageQuery extends PageQuery {
  keyword?: string
}

/** 任务动作载荷：CANCEL 走实例撤销接口，不在任务动作之列 */
export type TaskActionPayload = Exclude<ApprovalActionPayload, { action: 'CANCEL' }>

/* ---------------- 实例 ---------------- */

/** 可发起流程列表（PUBLISHED + 发起范围过滤） */
export function fetchStartableProcesses(): Promise<StartableProcessVO[]> {
  return request.get<unknown, StartableProcessVO[]>('/approval/instance/startable')
}

/** 发起流程（返回实例 ID） */
export function submitInstanceStart(data: InstanceStartDTO): Promise<string> {
  return request.post<unknown, string>('/approval/instance/start', data)
}

/** 我发起的实例分页 */
export function fetchMyInstancePage(params: InstancePageQuery): Promise<PageResult<InstanceItemVO>> {
  return request.get<unknown, PageResult<InstanceItemVO>>('/approval/instance/mine', { params })
}

/** 实例详情（参与人可见：发起人 / 审批人 / 抄送人） */
export function fetchInstanceDetail(id: string): Promise<InstanceDetailVO> {
  return request.get<unknown, InstanceDetailVO>(`/approval/instance/${id}`)
}

/** 撤销实例（仅发起人 + RUNNING） */
export function submitInstanceCancel(id: string, comment?: string): Promise<void> {
  return request.post<unknown, void>(`/approval/instance/${id}/cancel`, { comment: comment ?? '' })
}

/** 预览下一审批节点审批人（发起前 / 办理中联动） */
export function fetchNextApprovers(data: NextApproverQueryDTO): Promise<NextApproverVO> {
  return request.post<unknown, NextApproverVO>('/approval/instance/next-approvers', data)
}

/** 预览节点审批动作按钮（任务未生成场景） */
export function fetchNodeButtons(data: NodeButtonsQueryDTO): Promise<NodeButtonsVO> {
  return request.post<unknown, NodeButtonsVO>('/approval/instance/node-buttons', data)
}

/** 单据审批状态批量查询（返回 bizId → 状态） */
export function fetchBizApprovalStatus(
  bizType: string,
  bizIds: string[],
): Promise<Record<string, BizApprovalStatusVO>> {
  return request.get<unknown, Record<string, BizApprovalStatusVO>>('/approval/instance/biz-status', {
    params: { bizType, bizIds: bizIds.join(',') },
  })
}

/** 运行态用户选项（登录即可，转办/加签/抄送选人；前缀匹配用户名/昵称，仅启用用户） */
export function fetchPrincipalUsers(keyword = '', limit = 50): Promise<UserBriefVO[]> {
  return request.get<unknown, UserBriefVO[]>('/approval/instance/principal-users', {
    params: { keyword: keyword || undefined, limit },
  })
}

/* ---------------- 任务 ---------------- */

/** 我的待办分页 */
export function fetchTodoPage(params: TaskPageQuery): Promise<PageResult<TaskVO>> {
  return request.get<unknown, PageResult<TaskVO>>('/approval/task/todo', { params })
}

/** 我的已办分页 */
export function fetchDonePage(params: TaskPageQuery): Promise<PageResult<TaskVO>> {
  return request.get<unknown, PageResult<TaskVO>>('/approval/task/done', { params })
}

/** 任务办理视图（实例详情 + 任务 + 按钮 + 退回目标） */
export function fetchTaskDetail(taskId: string): Promise<TaskDetailVO> {
  return request.get<unknown, TaskDetailVO>(`/approval/task/${taskId}`)
}

/**
 * 审批动作统一入口。载荷字段与后端 TaskActionDTO 同名同义，
 * `ApprovalActionPayload` 各分支直接透传，零类型转换。
 */
export function submitTaskAction(taskId: string, payload: TaskActionPayload): Promise<void> {
  return request.post<unknown, void>(`/approval/task/${taskId}/action`, payload)
}

/* ---------------- 抄送 ---------------- */

/** 我的抄送分页（read=false 待阅 / read=true 已阅） */
export function fetchCcPage(read: boolean, params: CcPageQuery): Promise<PageResult<CcVO>> {
  return request.get<unknown, PageResult<CcVO>>(`/approval/cc/${read ? 'read' : 'unread'}`, { params })
}

/** 标记已阅（幂等） */
export function submitCcRead(ccId: string): Promise<void> {
  return request.post<unknown, void>(`/approval/cc/${ccId}/read`)
}
