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
}

/** 审批人来源 */
export type ApproverSource = 'MEMBER' | 'LEADER' | 'ROLE' | 'SELF_SELECT' | 'INITIATOR'

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
  rangeType: 'ALL' | 'MEMBER' | 'ROLE'
  memberIds: string[]
}

/** 审批节点属性 */
export interface ApproverProps {
  source: ApproverSource
  memberIds: string[]
  leaderLevel: number
  multiMode: MultiApproverMode
  emptyPolicy: EmptyApproverPolicy
  canRejectToStart: boolean
}

/** 条件分支列属性 */
export interface ConditionProps {
  /** 多个条件组：组内、组间都可用「且 / 或」组合 */
  groups: ConditionGroup[]
  isDefault: boolean
}

/** 并行分支列属性 */
export interface ParallelBranchProps {
  /** 条件组：为空表示该分支默认执行（无需满足条件） */
  groups: ConditionGroup[]
}

/** 分支列移动动作：移到最左 / 左移一级 / 右移一级 / 移到最右 */
export type BranchMoveAction = 'FIRST' | 'PREV' | 'NEXT' | 'LAST'

/** 分支列删除动作：只删该列 / 删除整个条件分支节点 / 删除整个并行节点 / 不可删除 */
export type BranchDeleteAction = 'COLUMN' | 'BRANCH' | 'PARALLEL' | 'NONE'

export type NodeProps = StartProps | ApproverProps | ConditionProps | ParallelBranchProps | Record<string, never>

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
}

/** 节点默认属性工厂 */
export function createNodeProps(type: NodeType): NodeProps {
  switch (type) {
    case NodeType.START:
      return { rangeType: 'ALL', memberIds: [] }
    case NodeType.APPROVER:
      return {
        source: 'MEMBER',
        memberIds: [],
        leaderLevel: 1,
        multiMode: 'OR',
        emptyPolicy: 'AUTO_PASS',
        canRejectToStart: false,
      }
    case NodeType.CONDITION:
      return { groups: [], isDefault: false }
    case NodeType.PARALLEL_BRANCH:
      return { groups: [] }
    default:
      return {}
  }
}

/** 是否为分支容器（条件分支 / 并行节点）：两者都以 conditionNodes 横向展开多列 */
export function isBranchContainer(type: NodeType): boolean {
  return type === NodeType.CONDITIONS || type === NodeType.PARALLEL
}

/** 是否为分支节点（分支容器或其分支列）：这类节点之下不允许直接再挂分支容器 */
export function isBranchNode(type: NodeType): boolean {
  return isBranchContainer(type)
    || type === NodeType.CONDITION
    || type === NodeType.PARALLEL_BRANCH
}
