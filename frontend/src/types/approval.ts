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
  /** 子流程节点名（任务所属实例为子流程实例时非空，前端渲染「子流程」徽标） */
  subProcessName: string | null
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
  records: TaskRecordVO[]
  /** 当前用户在该实例的待办任务 ID（无则 null） */
  myPendingTaskId: string | null
  /** 当前用户是否可撤销（=发起人 且 RUNNING） */
  canCancel: boolean
  /** 父实例 ID（当前实例为子流程实例时非空，前端渲染返回父流程入口） */
  parentInstanceId: string | null
  /** 子流程节点名（当前实例为子流程实例时非空，前端渲染「子流程」徽标） */
  subProcessName: string | null
  /** 本实例直接派生的子流程实例摘要（按创建时间升序） */
  subInstances: SubInstanceSummaryVO[]
}

/** 子流程实例摘要（SubInstanceSummaryVO 镜像，父实例详情的子流程卡片） */
export interface SubInstanceSummaryVO {
  id: string
  /** 子实例流程名（父流程名·子流程节点名） */
  name: string
  /** 子流程节点名 */
  subProcessName: string
  /** 推进模式：WAIT=等待完成 IMMEDIATE=启动即继续（旁路） */
  advanceMode: 'WAIT' | 'IMMEDIATE'
  status: InstanceStatus
  createTime: string
  finishTime: string | null
}

/** 可退回目标节点（ReturnTargetVO 镜像，START=退回发起人重新提交） */
export interface ReturnTargetVO {
  nodeId: string
  nodeName: string
  /** 目标节点候选审批人（非空时退回须从中指定审批人；START 目标或空审批人节点为空列表） */
  assigneeOptions: UserBriefVO[]
  /** 目标节点多方模式：OR=或签 AND=会签（START 目标为 null；前端据此渲染审批人多选提示） */
  multiMode: 'OR' | 'AND' | null
}

/** 人工路由选项（ManualRouteOptionVO 镜像，当前节点通过后需人工选择下一步时非空） */
export interface ManualRouteOptionVO {
  /** 分支列 ID，PASS 时作为 routeTargetId 回传 */
  branchId: string
  /** 选项名称 */
  label: string
  /** 指定审批人目标节点的候选审批人（优先分支列内首个审批节点，空列回退容器后继链首个
   *  审批节点；跳过直通办理节点，遇嵌套容器为空）：
   *  非空时选中该分支后渲染可选的「审批人」下拉，选定值经 PASS routeAssignees 回传 */
  assigneeOptions: UserBriefVO[]
}

/** 子流程下一节点可指定审批人目标（SubProcessApproverOptionVO 镜像，下一节点为子流程「启动后继续」时非空） */
export interface SubProcessApproverOptionVO {
  /** 目标审批节点 ID（PASS 时作为 subProcessAssignees 的 key 回传） */
  nodeId: string
  /** 目标审批节点名称 */
  nodeName: string
  /** 归属：MAIN=主流程 SUB_PROCESS=子流程 */
  scopeType: 'MAIN' | 'SUB_PROCESS'
  /** 归属流程名称（主流程名/子流程节点名） */
  scopeName: string | null
  /** 候选审批人（非空时前端渲染指定审批人下拉，空则按节点默认解析） */
  assigneeOptions: UserBriefVO[]
}

/** 下一节点可指定审批人目标（NextApproverOptionVO 镜像，当前节点链后继为普通审批节点时非空，单元素） */
export interface NextApproverOptionVO {
  /** 目标审批节点 ID（引擎按该节点登记并消费指定审批人，无需回传 nodeId） */
  nodeId: string
  /** 目标审批节点名称 */
  nodeName: string
  /** 多方模式：OR=或签 AND=会签（前端据此渲染单选/多选） */
  multiMode: 'OR' | 'AND'
  /** 候选审批人（非空时 PASS 弹窗渲染「下一节点审批人」选择，选定值经 nextAssignees 回传） */
  assigneeOptions: UserBriefVO[]
}

/** 待办理加签任务（PendingSignTaskVO 镜像：同意前提示加签人未审，可删除减签） */
export interface PendingSignTaskVO {
  /** 加签任务 ID，REDUCE_SIGN 时作为 targetTaskId 回传 */
  taskId: string
  /** 派生类型：BEFORE_SIGN=向前加签 AFTER_SIGN=向后加签 */
  signType: string
  /** 加签人 ID */
  assigneeId: string
  /** 加签人姓名 */
  assigneeName: string
  /** 任务到达时间 */
  createTime: string
}

/** 任务办理视图（TaskDetailVO 镜像 = 实例详情 + 任务本体 / 按钮 / 退回目标 / 人工路由选项） */
export interface TaskDetailVO extends InstanceDetailVO {
  task: TaskVO
  buttons: ApproverButtonVO[]
  returnTargets: ReturnTargetVO[]
  /** 人工路由选项：非空时 PASS 弹窗渲染「下一步」下拉 */
  manualRouteOptions: ManualRouteOptionVO[]
  /** 人工路由是否多选：下一节点为人工并行（PARALLEL routing=MANUAL）时 true，PASS 携带 routeTargetIds 多选 */
  manualRouteMultiple: boolean
  /** 下一节点为子流程（启动后继续）时可指定审批人目标（子流程首节点 + 父链下一节点，PASS 弹窗分开展示并可分别指定） */
  subProcessApproverOptions: SubProcessApproverOptionVO[]
  /** 下一节点（普通审批节点，穿透直通办理节点）可指定审批人目标（单元素，PASS 弹窗渲染「下一节点审批人」选择；与人工路由/子流程选项按链后继类型互斥） */
  nextApproverOptions: NextApproverOptionVO[]
  /** 当前任务直接派生且 PENDING 的加签任务：非空时同意弹窗提示「加签人还没有同意」并提供删除减签 */
  pendingSignTasks: PendingSignTaskVO[]
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
  /** 节点归属作用域：MAIN=主流程 SUB_PROCESS=子流程（前端据此区分主/子流程审批人） */
  scopeType: 'MAIN' | 'SUB_PROCESS'
  /** 归属流程名称：主流程名或子流程节点名 */
  scopeName: string | null
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
  /** 发起时为第一审批阶段节点指定的审批人（nodeId → userIds，可选）：或签单元素、会签多元素 */
  startAssignees?: Record<string, string[]>
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
