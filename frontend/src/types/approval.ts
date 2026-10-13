/**
 * 审批流程配置域类型，与后端 `com.paddleocr.web.model.{dto,vo}` 逐字段镜像。
 * 时间字段为后端全局 LocalDateTime 序列化格式 `yyyy-MM-dd HH:mm:ss` 字符串。
 */

/** 流程状态（后端大写枚举值） */
export type ProcessStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED'

/** 发起范围 */
export type InitiatorType = 'ALL' | 'SPECIFIC'

/** 流程配置列表行（ProcessVO 镜像） */
export interface ProcessVO {
  id: string
  code: string
  name: string
  status: ProcessStatus
  /** 当前已发布版本号（如 v1），从未发布为 null */
  currentVersion: string | null
  updateTime: string
}

/** 流程详情（ProcessDetailVO 镜像） */
export interface ProcessDetailVO {
  id: string
  code: string
  name: string
  description: string | null
  startPath: string
  detailPath: string
  initiatorType: InitiatorType
  initiatorMembers: string[]
  initiatorRoles: string[]
  status: ProcessStatus
  currentVersion: string | null
}

/** 流程设计查询结果（ProcessDesignVO 镜像） */
export interface ProcessDesignVO {
  /** 查草稿时为草稿所基于的已发布版本；指定版本查询时为该版本号 */
  version: string | null
  /** DRAFT=草稿 PUBLISHED=版本快照 */
  status: 'DRAFT' | 'PUBLISHED'
  /** WorkflowDoc JSON 字符串，从未保存过设计时为 null */
  designJson: string | null
  updateTime: string | null
}

/** 流程版本列表行（ProcessVersionVO 镜像） */
export interface ProcessVersionVO {
  version: string
  updateTime: string
}

/** 流程新增入参（ProcessSaveDTO 镜像） */
export interface ProcessSaveDTO {
  code: string
  name: string
  startPath: string
  detailPath: string
  description?: string
  initiatorType: InitiatorType
  initiatorMembers?: string[]
  initiatorRoles?: string[]
}

/** 流程基本信息更新入参（ProcessUpdateDTO 镜像，code 建后不可改） */
export type ProcessUpdateDTO = Omit<ProcessSaveDTO, 'code'>

/** 流程设计保存入参（ProcessDesignSaveDTO 镜像） */
export interface ProcessDesignSaveDTO {
  designJson: string
}

/* ==================== 运行态（实例 / 任务 / 抄送） ==================== */

/** 实例状态 */
export type InstanceStatus = 'RUNNING' | 'COMPLETED' | 'REJECTED' | 'CANCELED'

/** 任务状态 */
export type TaskStatus = 'PENDING' | 'PASSED' | 'REJECTED' | 'CANCELED' | 'TRANSFERRED'

/** 任务派生类型：普通 / 前加签 / 后加签 / 转办 / 委托 */
export type TaskSignType = 'NORMAL' | 'BEFORE_SIGN' | 'AFTER_SIGN' | 'TRANSFER' | 'DELEGATE'

/** 节点进度状态 */
export type NodeProgressStatus = 'CURRENT' | 'PENDING' | 'PASSED' | 'REJECTED' | 'SKIPPED'

/** 用户简要（审批人 / 抄送人展示，UserBriefVO 镜像） */
export interface UserBriefVO {
  userId: string
  name: string
}

/** 审批动作按钮（节点配置快照透传，ApproverButtonVO 镜像） */
export interface ApproverButtonVO {
  key: string
  label: string
  enabled: boolean
}

/** 审批任务列表行（待办 / 已办共用，TaskVO 镜像） */
export interface TaskVO {
  id: string
  instanceId: string
  processName: string
  bizType: string
  bizId: string
  bizTitle: string
  nodeName: string
  /** 多实例模式：OR=或签 AND=会签 */
  multiMode: 'OR' | 'AND'
  signType: TaskSignType
  startUserName: string
  createTime: string
  status: TaskStatus
  action: string | null
  finishTime: string | null
  comment: string | null
}

/** 我发起的实例列表行（InstanceItemVO 镜像） */
export interface InstanceItemVO {
  id: string
  processName: string
  bizType: string
  bizId: string
  bizTitle: string
  status: InstanceStatus
  startTime: string
  updateTime: string
  finishTime: string | null
  /** 当前待办人姓名（RUNNING 时） */
  currentApproverNames: string[]
}

/** 实例节点进度（NodeProgressVO 镜像） */
export interface NodeProgressVO {
  nodeId: string
  nodeName: string
  nodeType: string
  status: NodeProgressStatus
  assigneeNames: string[]
  finishTime: string | null
}

/** 审批记录时间线行（含 START/AUTO_PASS/ENGINE 系统动作，TaskRecordVO 镜像） */
export interface TaskRecordVO {
  id: string
  action: string
  nodeName: string
  operatorName: string
  comment: string | null
  detail: string | null
  createTime: string
}

/** 实例详情（InstanceDetailVO 镜像） */
export interface InstanceDetailVO {
  id: string
  processId: string
  processCode: string
  processName: string
  processVersion: string
  bizType: string
  bizId: string
  bizTitle: string
  status: InstanceStatus
  startUserId: string
  startUserName: string
  startTime: string
  finishTime: string | null
  /** 表单快照 JSON（业务表单渲染） */
  formSnapshotJson: string | null
  /** 版本快照 WorkflowDoc JSON */
  designJson: string | null
  nodes: NodeProgressVO[]
  records: TaskRecordVO[]
  /** 当前用户在该实例的待办任务 ID（无则 null） */
  myPendingTaskId: string | null
  /** 当前用户是否可撤销（=发起人 且 RUNNING） */
  canCancel: boolean
}

/** 可退回目标节点（ReturnTargetVO 镜像，START=退回发起人重新提交） */
export interface ReturnTargetVO {
  nodeId: string
  nodeName: string
}

/** 任务办理视图（TaskDetailVO 镜像 = 实例详情 + 任务本体 / 按钮 / 退回目标） */
export interface TaskDetailVO extends InstanceDetailVO {
  task: TaskVO
  buttons: ApproverButtonVO[]
  returnTargets: ReturnTargetVO[]
  /** 当前用户=assignee 且任务 PENDING */
  actionable: boolean
}

/** 抄送记录行（待阅 / 已阅共用，CcVO 镜像） */
export interface CcVO {
  id: string
  instanceId: string
  processName: string
  bizType: string
  bizId: string
  bizTitle: string
  fromUserName: string
  comment: string | null
  createTime: string
  readTime: string | null
}

/** 业务单据审批状态（BizApprovalStatusVO 镜像，取最新一条实例） */
export interface BizApprovalStatusVO {
  bizId: string
  instanceId: string | null
  status: InstanceStatus
  currentApproverNames: string[]
}

/** 可发起流程（StartableProcessVO 镜像） */
export interface StartableProcessVO {
  id: string
  code: string
  name: string
  description: string | null
  detailPath: string | null
}

/** 预览节点审批人（NodeApproverPreviewVO 镜像） */
export interface NodeApproverPreviewVO {
  nodeId: string
  nodeName: string
  multiMode: 'OR' | 'AND'
  approvers: UserBriefVO[]
}

/** 下一节点审批人预览结果（NextApproverVO 镜像） */
export interface NextApproverVO {
  ended: boolean
  nodes: NodeApproverPreviewVO[]
}

/** 节点按钮预览结果（NodeButtonsVO 镜像） */
export interface NodeButtonsVO {
  nodeId: string
  nodeName: string
  nodeType: string
  buttons: ApproverButtonVO[]
}

/** 发起审批请求（InstanceStartDTO 镜像） */
export interface InstanceStartDTO {
  processCode: string
  bizType: string
  bizId: string
  ccUserIds?: string[]
  comment?: string
}

/** 下一审批人预览查询（NextApproverQueryDTO 镜像） */
export interface NextApproverQueryDTO {
  processId: string
  nodeId?: string
  /** 条件值 Map：键为条件规则英文 fieldKey，用于 CONDITIONS 真实求值 */
  conditions?: Record<string, string>
}

/** 节点按钮预览查询（NodeButtonsQueryDTO 镜像） */
export interface NodeButtonsQueryDTO {
  processId: string
  nodeId?: string
  conditions?: Record<string, string>
}
