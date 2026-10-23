/** 审批流节点类型 */
export enum NodeType {
  START = 'START',
  APPROVER = 'APPROVER',
  CONDITIONS = 'CONDITIONS',
  CONDITION = 'CONDITION',
  /** 并行节点：多分支列同时执行，无默认列 */
  PARALLEL = 'PARALLEL',
  /** 并行节点下的分支列 */
  PARALLEL_BRANCH = 'PARALLEL_BRANCH',
  TRANSACT = 'TRANSACT',
  /** 子流程：内嵌一份完整的子流程设计（props.subDoc），运行态建子实例独立流转 */
  SUB_PROCESS = 'SUB_PROCESS',
}

/** 子流程推进模式：WAIT=父流程等待子流程完成后继续；IMMEDIATE=子流程启动后父流程立即继续（旁路） */
export type SubProcessAdvanceMode = 'WAIT' | 'IMMEDIATE'

/** 子视图层级项：视图栈的一层（子流程节点 id + 名称，面包屑与钻入定位用） */
export interface SubProcessTrailItem {
  nodeId: string
  name: string
}

/** 子流程最大嵌套层数（主流程为第 0 层；与后端 approval.sub-process.max-depth 对齐） */
export const MAX_SUB_PROCESS_DEPTH = 3

/** 节点默认名称 */
export const DEFAULT_NODE_NAMES: Record<string, string> = {
  [NodeType.START]: '发起人',
  [NodeType.APPROVER]: '审批人',
  [NodeType.CONDITIONS]: '条件分支',
  [NodeType.CONDITION]: '条件',
  [NodeType.PARALLEL]: '并行节点',
  [NodeType.PARALLEL_BRANCH]: '并行分支',
  [NodeType.TRANSACT]: '办理人',
  [NodeType.SUB_PROCESS]: '子流程',
}

/** 推进模式文案（卡片徽标与属性抽屉单选共用） */
export const SUB_PROCESS_ADVANCE_LABELS: Record<SubProcessAdvanceMode, string> = {
  WAIT: '等待完成',
  IMMEDIATE: '启动即继续',
}

/** 推进模式说明（属性抽屉单选副文案） */
export const SUB_PROCESS_ADVANCE_HINTS: Record<SubProcessAdvanceMode, string> = {
  WAIT: '父流程等待子流程全部完成后，再继续下一节点',
  IMMEDIATE: '子流程启动后父流程立即继续下一节点，子流程旁路独立流转',
}

/** 审批人来源：指定（可同时圈定人员与角色）/ 主管 / 发起人自选 / 发起人本人 */
export type ApproverSource = 'MEMBER' | 'LEADER' | 'SELF_SELECT' | 'INITIATOR'

/** 多人审批方式：或签 / 会签 */
export type MultiApproverMode = 'OR' | 'AND'

/** 审批人为空时的策略 */
export type EmptyApproverPolicy = 'AUTO_PASS' | 'TO_LEADER' | 'TO_ADMIN'

/** 条件操作符 */
export type ConditionOperator =
  | 'EQ'
  | 'NEQ'
  | 'GT'
  | 'GTE'
  | 'LT'
  | 'LTE'
  | 'CONTAIN'
  | 'NOT_CONTAIN'

/** 条件逻辑连接符：且 / 或 */
export type ConditionLogic = 'AND' | 'OR'

/** 单条条件规则 */
export interface ConditionRule {
  fieldKey: string
  fieldName: string
  operator: ConditionOperator
  value: string
  /** 与同组上一条规则的连接符；组内首条忽略 */
  link: ConditionLogic
}

/** 条件组：组内规则按各自 link 依次串联，组与组之间按组的 link 串联 */
export interface ConditionGroup {
  /** 条件组名称（可编辑，默认「组n」） */
  name: string
  /** 与上一组的连接符；首个条件组忽略 */
  link: ConditionLogic
  rules: ConditionRule[]
}

/** 发起人节点属性 */
export interface StartProps {
  /** 发起范围：全体 / 指定（指定时可同时圈定人员与角色） */
  rangeType: 'ALL' | 'SPECIFIC'
  memberIds: string[]
  roleIds: string[]
}

/** 审批人的操作按钮标识：加签分为向前 / 向后两种 */
export type ApproverButtonKey =
  | 'PASS'
  | 'REJECT'
  | 'TRANSFER'
  | 'DELEGATE'
  | 'ADD_SIGN_BEFORE'
  | 'ADD_SIGN_AFTER'
  | 'RETURN'

/** 单个操作按钮配置：显示名称（可改）与是否启用 */
export interface ApproverButton {
  key: ApproverButtonKey
  label: string
  enabled: boolean
}

/** 操作按钮的固定名称（左侧「操作按钮」列展示） */
export const APPROVER_BUTTON_LABELS: Record<ApproverButtonKey, string> = {
  PASS: '通过',
  REJECT: '拒绝',
  TRANSFER: '转办',
  DELEGATE: '委派',
  ADD_SIGN_BEFORE: '向前加签',
  ADD_SIGN_AFTER: '向后加签',
  RETURN: '退回',
}

/** 操作按钮默认配置：全部启用，显示名称取默认中文 */
export function createApproverButtons(): ApproverButton[] {
  return (Object.keys(APPROVER_BUTTON_LABELS) as ApproverButtonKey[]).map((key) => ({
    key,
    label: APPROVER_BUTTON_LABELS[key],
    enabled: true,
  }))
}

/** 旧数据里按钮配置的宽松结构（key 可能已废弃，字段可能缺失） */
interface RawApproverButton {
  key: string
  label?: string
  enabled?: boolean
}

/**
 * 归并按钮配置：以默认配置为基准与顺序，回填已有配置的名称与启用状态，
 * 缺失的按钮补默认值，已废弃的按钮丢弃。
 * 旧数据里的「加签」会拆成「向前加签」「向后加签」两行，并沿用原启用状态。
 */
export function normalizeApproverButtons(buttons?: ReadonlyArray<RawApproverButton>): ApproverButton[] {
  if (!buttons?.length) {
    return createApproverButtons()
  }
  const byKey = new Map<string, RawApproverButton>()
  for (const button of buttons) {
    byKey.set(button.key, button)
  }
  const legacySign = byKey.get('ADD_SIGN')
  return createApproverButtons().map((item) => {
    const own = byKey.get(item.key)
    if (own) {
      return {
        key: item.key,
        label: own.label?.trim() ? own.label : item.label,
        enabled: own.enabled ?? item.enabled,
      }
    }
    // 旧「加签」拆分出的两行：显示名称取新的默认名，仅沿用原启用状态
    if (legacySign && item.key.startsWith('ADD_SIGN')) {
      return { key: item.key, label: item.label, enabled: legacySign.enabled ?? item.enabled }
    }
    return item
  })
}

/** 审批节点属性 */
export interface ApproverProps {
  source: ApproverSource
  memberIds: string[]
  roleIds: string[]
  leaderLevel: number
  multiMode: MultiApproverMode
  emptyPolicy: EmptyApproverPolicy
  canRejectToStart: boolean
  /** 退回目标节点 id（节点的父节点之一）；空串表示未指定 */
  rejectTargetId: string
  /** 操作按钮配置（按钮设置页签） */
  buttons: ApproverButton[]
}

/** 条件分支列属性 */
export interface ConditionProps {
  /** 多个条件组：组内、组间都可用「且 / 或」组合 */
  groups: ConditionGroup[]
  isDefault: boolean
  /** 条件优先级：按列位置从 1 递增；默认列（其他条件）恒为 0，不参与排序 */
  priority: number
  /** 人工路由模式下的选项名称（仅父容器 routing=MANUAL 时生效） */
  label?: string
}

/** 分支容器属性（CONDITIONS 条件分支 / PARALLEL 并行共用）：路由方式 */
export interface ConditionsProps {
  /**
   * 路由方式：AUTO=缺省自动（CONDITIONS 按条件自动求值单选；PARALLEL 全部分支无条件并行）；
   * MANUAL=人工（上游审批人在通过时选择：CONDITIONS 单选 routeTargetId，PARALLEL 多选 routeTargetIds）
   */
  routing: 'AUTO' | 'MANUAL'
}

/** 并行分支列属性 */
export interface ParallelBranchProps {
  /** 条件组：为空表示该分支默认执行（无需满足条件） */
  groups: ConditionGroup[]
}

/** 子流程节点属性：内嵌子流程设计 + 推进模式 */
export interface SubProcessProps {
  /** 内嵌子流程设计文档（完整 WorkflowDoc，root 为发起人节点） */
  subDoc: WorkflowDoc
  /** 推进模式；旧草稿缺失时按 WAIT 处理 */
  advanceMode: SubProcessAdvanceMode
}

/** 分支列移动动作：移到最左 / 左移一级 / 右移一级 / 移到最右 */
export type BranchMoveAction = 'FIRST' | 'PREV' | 'NEXT' | 'LAST'

/** 分支列删除动作：只删该列 / 删除整个条件分支节点 / 删除整个并行节点 / 不可删除 /
 *  仅清空列内审批节点（人工路由 ≤2 列时不解散容器，分支列与选项配置保留） */
export type BranchDeleteAction = 'COLUMN' | 'BRANCH' | 'PARALLEL' | 'CLEAR' | 'NONE'

export type NodeProps =
  | StartProps
  | ApproverProps
  | ConditionsProps
  | ConditionProps
  | ParallelBranchProps
  | SubProcessProps
  | Record<string, never>

/** 流程节点（递归结构：主干 childNode 串联，分支 conditionNodes 横向展开） */
export interface WorkflowNode {
  id: string
  name: string
  type: NodeType
  props: NodeProps
  childNode: WorkflowNode | null
  conditionNodes?: WorkflowNode[]
}

/** 流程文档（导出/保存的完整结构） */
export interface WorkflowDoc {
  name: string
  updatedAt: number
  root: WorkflowNode
}

/** 校验错误项 */
export interface WorkflowError {
  nodeId: string
  message: string
  /**
   * 错误所在的子视图路径：主流程为空数组，子流程内错误按外层到内层记录各层子流程节点，
   * 前端据此逐层钻入定位（nodeId 为子流程节点 id，name 为面包屑显示名）
   */
  path: SubProcessTrailItem[]
}

/** 节点默认属性工厂 */
export function createNodeProps(type: NodeType): NodeProps {
  switch (type) {
    case NodeType.START:
      return { rangeType: 'ALL', memberIds: [], roleIds: [] }
    case NodeType.APPROVER:
      return {
        source: 'MEMBER',
        memberIds: [],
        roleIds: [],
        leaderLevel: 1,
        multiMode: 'OR',
        emptyPolicy: 'AUTO_PASS',
        canRejectToStart: false,
        rejectTargetId: '',
        buttons: createApproverButtons(),
      }
    case NodeType.CONDITIONS:
    case NodeType.PARALLEL:
      return { routing: 'AUTO' }
    case NodeType.CONDITION:
      return { groups: [], isDefault: false, priority: 0, label: '' }
    case NodeType.PARALLEL_BRANCH:
      return { groups: [] }
    case NodeType.SUB_PROCESS:
      return { subDoc: createSubDoc(), advanceMode: 'WAIT' }
    default:
      return {}
  }
}

/** 节点 ID 生成：优先 crypto.randomUUID，不可用环境回退时间戳 + 随机串 */
export function createNodeId(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `wf-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

/** 子流程内嵌设计文档工厂：与主流程同构（root 为发起人节点，主干链为空） */
export function createSubDoc(name = '子流程'): WorkflowDoc {
  return {
    name,
    updatedAt: Date.now(),
    root: {
      id: createNodeId(),
      name: DEFAULT_NODE_NAMES[NodeType.START],
      type: NodeType.START,
      props: createNodeProps(NodeType.START),
      childNode: null,
    },
  }
}

/** 统计节点数（含自身、主链后继与全部分支列子树）：子流程卡片「内部 N 个节点」概要用 */
export function countNodes(node: WorkflowNode | null): number {
  if (!node) {
    return 0
  }
  let total = 1
  for (const branch of node.conditionNodes ?? []) {
    total += countNodes(branch)
  }
  return total + countNodes(node.childNode)
}

/** 是否为分支容器（条件分支 / 并行节点）：两者都以 conditionNodes 横向展开多列 */
export function isBranchContainer(type: NodeType): boolean {
  return type === NodeType.CONDITIONS || type === NodeType.PARALLEL
}

/** 是否为分支列（条件列 / 并行列）：列内首节点必须是普通节点，不允许直接再挂分支容器 */
export function isBranchColumn(type: NodeType): boolean {
  return type === NodeType.CONDITION || type === NodeType.PARALLEL_BRANCH
}

/** 加签方向：向前 / 向后 */
export type AddSignDirection = 'BEFORE' | 'AFTER'

/**
 * 审批运行时操作按钮标识（工具栏）：
 * 在审批节点可配置按钮（ApproverButtonKey）之外，另含评论、抄送、取消三个流程级按钮；
 * 节点配置里拆分的「向前/向后加签」在运行态合并为一个「加签」按钮。
 */
export type ApprovalActionKey =
  | 'PASS'
  | 'REJECT'
  | 'COMMENT'
  | 'COPY'
  | 'TRANSFER'
  | 'DELEGATE'
  | 'ADD_SIGN'
  | 'RETURN'
  | 'CANCEL'

/**
 * 审批操作提交载荷：按操作类型区分字段，调用方据此请求对应接口。
 * 意见类字段统一为 `comment`（通过/拒绝/转办/委派/加签为审批意见，抄送为抄送意见，退回为退回理由，取消为取消理由）。
 */
export type ApprovalActionPayload =
  | { action: 'PASS'; comment: string; routeTargetId?: string; routeTargetIds?: string[]; routeAssignees?: Record<string, string> }
  | { action: 'REJECT' | 'COMMENT'; comment: string }
  | { action: 'COPY'; receiverIds: string[]; comment: string }
  | { action: 'TRANSFER' | 'DELEGATE'; receiverId: string; comment: string }
  | { action: 'ADD_SIGN'; receiverId: string; comment: string; direction: AddSignDirection }
  | { action: 'REDUCE_SIGN'; targetTaskId: string; comment: string }
  | { action: 'RETURN'; targetNodeId: string; comment: string }
  | { action: 'CANCEL'; comment: string }
