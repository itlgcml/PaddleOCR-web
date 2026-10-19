import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { deepClone } from '@/utils/clone'
import { createNodeProps, isBranchContainer, NodeType, normalizeApproverButtons, type BranchDeleteAction, type BranchMoveAction, type ConditionLogic, type ConditionProps, type ConditionRule, type ConditionsProps, type NodeProps, type NodeType as NodeTypeT, type StartProps, type WorkflowDoc, type WorkflowNode } from '@/types/workflow'

/** 默认节点名 */
const DEFAULT_NAMES: Record<string, string> = {
  [NodeType.START]: '发起人',
  [NodeType.APPROVER]: '审批人',
  [NodeType.CONDITIONS]: '条件分支',
  [NodeType.CONDITION]: '条件',
  [NodeType.PARALLEL]: '并行节点',
  [NodeType.PARALLEL_BRANCH]: '并行分支',
  [NodeType.TRANSACT]: '办理人',
}

function genId(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `wf-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

function createNode(type: NodeTypeT, overrides: Partial<WorkflowNode> = {}): WorkflowNode {
  return {
    id: genId(),
    name: DEFAULT_NAMES[type] ?? type,
    type,
    props: createNodeProps(type),
    childNode: null,
    ...overrides,
  }
}

function createDefaultDoc(): WorkflowDoc {
  return { name: '未命名流程', updatedAt: Date.now(), root: createNode(NodeType.START) }
}

/** 先序查找节点：当前 → 主链 → 分支列 */
function findNode(node: WorkflowNode | null, id: string): WorkflowNode | null {
  if (!node) {
    return null
  }
  if (node.id === id) {
    return node
  }
  const inChild = findNode(node.childNode, id)
  if (inChild) {
    return inChild
  }
  for (const branch of node.conditionNodes ?? []) {
    const hit = findNode(branch, id)
    if (hit) {
      return hit
    }
  }
  return null
}

/** 查找父节点（childNode 或 conditionNodes 中持有目标者） */
function findParent(node: WorkflowNode | null, id: string): WorkflowNode | null {
  if (!node) {
    return null
  }
  if (node.childNode?.id === id) {
    return node
  }
  for (const branch of node.conditionNodes ?? []) {
    if (branch.id === id) {
      return node
    }
  }
  return findParent(node.childNode, id) ?? findParentInList(node.conditionNodes ?? [], id)
}

function findParentInList(list: WorkflowNode[], id: string): WorkflowNode | null {
  for (const item of list) {
    const hit = findParent(item, id)
    if (hit) {
      return hit
    }
  }
  return null
}

/**
 * 分支容器保留的最小列数：删至该规模再删单列时分支结构已无意义（条件分支 = 1 条件 + 1 默认），
 * 此时删除该列将解散整个分支节点（仅删被点列，剩余列子流程提升到主干），避免留下单列死状态。
 */
const MIN_BRANCH_COLUMNS = 2

/** 分支列移动的目标下标：FIRST/LAST 取两端，PREV/NEXT 取相邻一格 */
function resolveBranchTarget(from: number, length: number, action: BranchMoveAction): number {
  if (action === 'FIRST') {
    return 0
  }
  if (action === 'LAST') {
    return length - 1
  }
  return action === 'PREV' ? from - 1 : from + 1
}

/**
 * 按位置重建条件列的默认身份与优先级：末列为「其他条件」（优先级恒为 0，不参与排序），
 * 其余列按顺序为优先级 1..n。
 * 分支列名称不再重排，保留用户自定义的名称（新增列只取一次默认名）。
 * 并行节点无优先级概念，直接跳过。
 */
function normalizeBranches(containerType: NodeTypeT, branches: WorkflowNode[]): void {
  if (containerType !== NodeType.CONDITIONS) {
    return
  }
  let priority = 1
  branches.forEach((branch, index) => {
    const props = branch.props as ConditionProps
    props.isDefault = index === branches.length - 1
    props.priority = props.isDefault ? 0 : priority
    if (!props.isDefault) {
      priority += 1
    }
  })
}

/**
 * 新分支列的默认名：在现有最大编号上递增（如已有「条件2」→ 新增「条件3」）。
 * 编号只增不回收：删除列后新增不会复用被删列的编号。
 */
function nextBranchName(branches: WorkflowNode[], parallel: boolean): string {
  const prefix = parallel ? '并行' : '条件'
  const pattern = new RegExp(`^${prefix}(\\d+)$`)
  let max = 0
  for (const branch of branches) {
    const matched = pattern.exec(branch.name)
    if (matched) {
      max = Math.max(max, Number(matched[1]))
    }
  }
  return `${prefix}${max + 1}`
}

/** 深拷贝子树并重新生成全部 id */
function cloneWithNewIds(node: WorkflowNode): WorkflowNode {
  const copy: WorkflowNode = { ...node, id: genId(), props: deepClone(node.props), childNode: null }
  if (node.conditionNodes?.length) {
    copy.conditionNodes = node.conditionNodes.map(cloneWithNewIds)
  }
  if (node.childNode) {
    copy.childNode = cloneWithNewIds(node.childNode)
  }
  return copy
}

/** 受支持的节点类型集合：枚举中已移除的旧类型不在其中 */
const SUPPORTED_NODE_TYPES = new Set<string>(Object.values(NodeType))

/** 旧草稿里的条件结构：单层 rules；link 为后加字段，旧数据可能缺失 */
interface LegacyConditionRule {
  fieldKey: string
  fieldName: string
  operator: ConditionRule['operator']
  value: string
  link?: ConditionLogic
}

interface LegacyConditionGroup {
  name?: string
  link?: ConditionLogic
  rules: LegacyConditionRule[]
}

interface LegacyConditionProps {
  rules?: LegacyConditionRule[]
  groups?: LegacyConditionGroup[]
  isDefault?: boolean
  priority?: number
}

/**
 * 旧草稿兼容：条件属性由「单层 rules」升级为「条件组 groups」。
 * 旧数据整体迁移为一个条件组，并补齐缺失的组名与连接符（默认「组1」/「且」）。
 */
function migrateConditionProps(node: WorkflowNode): void {
  const legacy = node.props as LegacyConditionProps
  const normalizeRule = (rule: LegacyConditionRule): ConditionRule => ({ ...rule, link: rule.link ?? 'AND' })
  if (legacy.groups) {
    node.props = {
      groups: legacy.groups.map((group, index) => ({
        name: group.name ?? `组${index + 1}`,
        link: group.link ?? 'AND',
        rules: group.rules.map(normalizeRule),
      })),
      isDefault: legacy.isDefault === true,
      priority: legacy.priority ?? 0,
    }
    return
  }
  const rules = legacy.rules?.map(normalizeRule) ?? []
  node.props = {
    groups: rules.length > 0 ? [{ name: '组1', link: 'AND', rules }] : [],
    isDefault: legacy.isDefault === true,
    priority: legacy.priority ?? 0,
  }
}

/** 旧草稿里的发起人结构：rangeType 曾为三态（全体 / 成员 / 角色），且无 roleIds */
interface LegacyStartProps {
  rangeType?: string
  memberIds?: string[]
  roleIds?: string[]
}

/**
 * 旧草稿兼容：发起范围收敛为「全体 / 指定」，旧的成员、角色值分别归位到 memberIds / roleIds。
 */
function migrateStartProps(node: WorkflowNode): void {
  const legacy = node.props as LegacyStartProps
  const memberIds = legacy.memberIds ?? []
  const roleIds = legacy.roleIds ?? []
  if (legacy.rangeType === 'ALL') {
    node.props = { rangeType: 'ALL', memberIds: [], roleIds: [] }
    return
  }
  node.props = legacy.rangeType === 'ROLE'
    ? { rangeType: 'SPECIFIC', memberIds: [], roleIds: roleIds.length > 0 ? roleIds : memberIds }
    : { rangeType: 'SPECIFIC', memberIds, roleIds }
}

/** 旧草稿里的审批人结构：来源曾含 'ROLE'（成员 / 角色二选一），且缺 roleIds / rejectTargetId / buttons */
interface LegacyApproverProps {
  source?: string
  memberIds?: string[]
  roleIds?: string[]
  rejectTargetId?: string
  buttons?: Array<{ key: string; label?: string; enabled?: boolean }>
}

/**
 * 旧草稿兼容：审批人来源收敛为「指定」，旧的单选角色值归位到 roleIds，并补齐缺失的 roleIds 与退回目标。
 */
function migrateApproverProps(node: WorkflowNode): void {
  const legacy = node.props as LegacyApproverProps
  const roleOnly = legacy.source === 'ROLE'
  const memberIds = legacy.memberIds ?? []
  const roleIds = legacy.roleIds ?? []
  node.props = {
    ...(node.props as object),
    source: roleOnly ? 'MEMBER' : (legacy.source ?? 'MEMBER'),
    memberIds: roleOnly ? [] : memberIds,
    roleIds: roleOnly && roleIds.length === 0 ? memberIds : roleIds,
    rejectTargetId: legacy.rejectTargetId ?? '',
    buttons: normalizeApproverButtons(legacy.buttons),
  } as NodeProps
}

/** 先序查找从根到目标节点的路径（含目标自身）；命中返回 true，trail 中即为路径 */
function findTrail(node: WorkflowNode | null, id: string, trail: WorkflowNode[]): boolean {
  if (!node) {
    return false
  }
  trail.push(node)
  if (node.id === id) {
    return true
  }
  if (findTrail(node.childNode, id, trail)) {
    return true
  }
  for (const branch of node.conditionNodes ?? []) {
    if (findTrail(branch, id, trail)) {
      return true
    }
  }
  trail.pop()
  return false
}

/**
 * 旧草稿兼容：剔除已被移除类型的节点，并把它的后继接驳到父级，避免主链断裂。
 * 先清理子树再判定自身，这样自身被剔除时可以直接把已清理的后继顶上来。
 */
function sanitizeNode(node: WorkflowNode | null): WorkflowNode | null {
  if (!node) {
    return null
  }
  if (node.type === NodeType.CONDITION) {
    migrateConditionProps(node)
  }
  if (node.type === NodeType.START) {
    migrateStartProps(node)
  }
  if (node.type === NodeType.APPROVER) {
    migrateApproverProps(node)
  }
  node.childNode = sanitizeNode(node.childNode)
  if (node.conditionNodes?.length) {
    node.conditionNodes = node.conditionNodes.filter((branch) => {
      if (!SUPPORTED_NODE_TYPES.has(branch.type)) {
        return false
      }
      branch.childNode = sanitizeNode(branch.childNode)
      return true
    })
    // 旧草稿可能缺优先级：按当前列顺序重算一次
    normalizeBranches(node.type, node.conditionNodes)
  }
  return SUPPORTED_NODE_TYPES.has(node.type) ? node : node.childNode
}

/** 根节点兜底：整棵树都被剔除时退回一个全新的发起人节点 */
function sanitizeDoc(doc: WorkflowDoc): WorkflowDoc {
  doc.root = sanitizeNode(doc.root) ?? createNode(NodeType.START)
  return doc
}

export const useWorkflowStore = defineStore('workflow', () => {
  const doc = ref<WorkflowDoc>(createDefaultDoc())
  const zoom = ref(1)
  const activeNodeId = ref<string | null>(null)
  /** 当前展开的「添加节点」菜单所属 parentId（全局唯一，点击外部统一清空） */
  const addMenuId = ref<string | null>(null)

  const activeNode = computed<WorkflowNode | null>(() =>
    activeNodeId.value ? findNode(doc.value.root, activeNodeId.value) : null,
  )

  /** 插入节点：新节点挂到 parent.childNode 前（分支列则插到列链头部） */
  function insertNode(parentId: string, type: NodeTypeT): WorkflowNode | null {
    const parent = findNode(doc.value.root, parentId)
    if (!parent || type === NodeType.START) {
      return null
    }
    const node = createNode(type)
    if (type === NodeType.CONDITIONS) {
      // 钉钉式初始形态：条件1 + 其他条件两列，且每列预置一个审批节点
      const first = createNode(NodeType.CONDITION, { name: '条件1' })
      const defaultBranch = createNode(NodeType.CONDITION, { name: '其他条件' })
      ;(defaultBranch.props as ConditionProps).isDefault = true
      first.childNode = createNode(NodeType.APPROVER)
      defaultBranch.childNode = createNode(NodeType.APPROVER)
      node.conditionNodes = [first, defaultBranch]
      normalizeBranches(NodeType.CONDITIONS, node.conditionNodes)
    } else if (type === NodeType.PARALLEL) {
      // 并行节点初始形态：固定 2 列（无默认列，两列同时执行），每列预置一个审批节点
      node.conditionNodes = [1, 2].map((index) => {
        const branch = createNode(NodeType.PARALLEL_BRANCH, { name: `并行${index}` })
        branch.childNode = createNode(NodeType.APPROVER)
        return branch
      })
    }
    // 统一挂到 parent.childNode 前，原后继接在新节点之后（条件分支容器同理：容器主链为汇合后主干）
    node.childNode = parent.childNode
    parent.childNode = node
    return node
  }

  /** 删除节点：后继接驳到父级 */
  function deleteNode(id: string): boolean {
    if (id === doc.value.root.id) {
      return false
    }
    const parent = findParent(doc.value.root, id)
    const target = findNode(doc.value.root, id)
    if (!parent || !target) {
      return false
    }
    if (parent.childNode?.id === id) {
      parent.childNode = target.childNode
    } else {
      const idx = parent.conditionNodes?.findIndex((b) => b.id === id) ?? -1
      if (idx >= 0 && parent.conditionNodes) {
        parent.conditionNodes.splice(idx, 1)
      }
    }
    if (activeNodeId.value === id) {
      activeNodeId.value = null
    }
    return true
  }

  /** 复制节点（含子树），插到原节点之后 */
  function copyNode(id: string): WorkflowNode | null {
    const parent = findParent(doc.value.root, id)
    const target = findNode(doc.value.root, id)
    if (!target || !parent || target.type === NodeType.START) {
      return null
    }
    const copy = cloneWithNewIds(target)
    copy.childNode = target.childNode
    if (parent.childNode?.id === id) {
      parent.childNode = copy
    }
    return copy
  }

  /** 更新节点属性与名称 */
  function updateNode(id: string, props: NodeProps, name?: string): void {
    const node = findNode(doc.value.root, id)
    if (!node) {
      return
    }
    node.props = props
    if (name !== undefined && name.trim() !== '') {
      node.name = name.trim()
    }
  }

  /** 分支容器加列：插入到默认列之前（默认列固定最右），随后按位置重建条件优先级 */
  function addBranch(containerId: string): WorkflowNode | null {
    const container = findNode(doc.value.root, containerId)
    if (!container || !isBranchContainer(container.type)) {
      return null
    }
    const branches = container.conditionNodes ?? []
    const parallel = container.type === NodeType.PARALLEL
    const branch = createNode(parallel ? NodeType.PARALLEL_BRANCH : NodeType.CONDITION, {
      name: nextBranchName(branches, parallel),
    })
    // 与初始形态保持一致：新列预置一个审批节点，便于直接连线放卡片
    branch.childNode = createNode(NodeType.APPROVER)
    // 默认列固定最右：条件分支插到默认列之前；并行节点无默认列，直接尾插
    const defaultIdx = branches.findIndex((b) => (b.props as ConditionProps).isDefault)
    if (defaultIdx >= 0) {
      branches.splice(defaultIdx, 0, branch)
    } else {
      branches.push(branch)
    }
    container.conditionNodes = [...branches]
    normalizeBranches(container.type, container.conditionNodes)
    return branch
  }

  /**
   * 判定分支列的删除动作（供 UI 决定确认文案与提示）：
   * 已达最小规模（≤2 列）时——人工路由（routing=MANUAL）返回 CLEAR：仅清空该列内审批节点，
   * 分支列与选项配置保留（发布校验要求 MANUAL ≥2 列，不删列不解散容器）；
   * 自动路由返回 BRANCH/PARALLEL：删除该列将解散整个分支容器（仅删被点列及其子流程，剩余列子流程提升到主干）。
   * 多列形态下默认条件列不可删（NONE），其余列只删自身（COLUMN）。
   */
  function resolveBranchDeleteAction(id: string): BranchDeleteAction {
    const container = findParent(doc.value.root, id)
    const branches = container?.conditionNodes
    if (!container || !branches) {
      return 'NONE'
    }
    const idx = branches.findIndex((b) => b.id === id)
    if (idx < 0) {
      return 'NONE'
    }
    if (branches.length <= MIN_BRANCH_COLUMNS) {
      if ((container.props as ConditionsProps).routing === 'MANUAL') {
        return 'CLEAR'
      }
      return container.type === NodeType.PARALLEL ? 'PARALLEL' : 'BRANCH'
    }
    return (branches[idx].props as ConditionProps).isDefault ? 'NONE' : 'COLUMN'
  }

  /** 删除分支列；仅剩 2 列时删列 = 删该列及其子流程并解散分支结构：剩余列子流程提升到主干，未删列内容不丢失；
   *  人工路由（MANUAL）仅剩 2 列时不删列不解散容器，仅清空被点列内的审批节点子链（CLEAR）。 */
  function deleteBranch(id: string): boolean {
    const action = resolveBranchDeleteAction(id)
    if (action === 'NONE') {
      return false
    }
    const container = findParent(doc.value.root, id)
    if (!container) {
      return false
    }
    const branches = container.conditionNodes ?? []
    const idx = branches.findIndex((b) => b.id === id)
    if (idx < 0) {
      return false
    }
    if (action === 'CLEAR') {
      // 人工路由：仅删除该列内的审批节点（含子链），分支列与选项 label 保留，可重新添加审批节点
      branches[idx].childNode = null
      if (activeNodeId.value && !findNode(doc.value.root, activeNodeId.value)) {
        activeNodeId.value = null
      }
      return true
    }
    if (action === 'BRANCH' || action === 'PARALLEL') {
      // 先摘除被删列（连同其子流程）
      branches.splice(idx, 1)
      const remain = branches[0]
      if (remain) {
        // 剩列子流程提升到主干：其链尾接驳容器原汇合后继（容器的 childNode）
        let tail = remain.childNode ?? null
        if (tail) {
          while (tail.childNode) {
            tail = tail.childNode
          }
          tail.childNode = container.childNode ?? null
        }
        // 容器只会出现在链位置（父级 childNode）：整棵替换为剩列子流程；剩列为空链时直接接容器后继
        const parent = findParent(doc.value.root, container.id)
        if (!parent || parent.childNode?.id !== container.id) {
          return false
        }
        parent.childNode = remain.childNode ?? container.childNode ?? null
      } else {
        // 无剩列（异常兜底）：等价整体移除容器
        return deleteNode(container.id)
      }
      if (activeNodeId.value && !findNode(doc.value.root, activeNodeId.value)) {
        activeNodeId.value = null
      }
      return true
    }
    branches.splice(idx, 1)
    normalizeBranches(container.type, branches)
    return true
  }

  /** 调整分支列位置：按目标位置重排后统一重建默认身份与条件优先级（默认列恒在最右） */
  function moveBranch(id: string, action: BranchMoveAction): boolean {
    const container = findParent(doc.value.root, id)
    const branches = container?.conditionNodes
    if (!container || !branches) {
      return false
    }
    const from = branches.findIndex((b) => b.id === id)
    if (from < 0) {
      return false
    }
    const target = resolveBranchTarget(from, branches.length, action)
    if (target < 0 || target >= branches.length || target === from) {
      return false
    }
    const [moved] = branches.splice(from, 1)
    branches.splice(target, 0, moved)
    normalizeBranches(container.type, branches)
    return true
  }

  /** 分支列（CONDITION/PARALLEL_BRANCH）所属容器（CONDITIONS/PARALLEL）的路由方式；非分支列或找不到返回 null（旧数据缺省按 AUTO） */
  function conditionsRoutingOf(conditionId: string): 'AUTO' | 'MANUAL' | null {
    const parent = findParent(doc.value.root, conditionId)
    if (!parent || (parent.type !== NodeType.CONDITIONS && parent.type !== NodeType.PARALLEL)) {
      return null
    }
    return (parent.props as ConditionsProps).routing ?? 'AUTO'
  }

  /** 可退回的父节点：从根到该节点路径上的发起人 / 审批人节点（不含自身与分支容器） */
  function getRejectTargets(id: string): WorkflowNode[] {
    const trail: WorkflowNode[] = []
    if (!findTrail(doc.value.root, id, trail)) {
      return []
    }
    return trail.filter((node) =>
      node.id !== id && (node.type === NodeType.START || node.type === NodeType.APPROVER),
    )
  }

  /** 同步「谁可以发起」到起始节点（第 1 步基本信息填写后写入，第 2 步发起人节点仅展示） */
  function updateStartInitiators(payload: StartProps): void {
    const start = doc.value.root
    if (!start || start.type !== NodeType.START) {
      return
    }
    start.props = deepClone(payload)
  }

  function setActive(id: string | null): void {
    activeNodeId.value = id
  }

  /** 打开指定「添加节点」菜单（同一时刻只允许一个） */
  function openAddMenu(id: string): void {
    addMenuId.value = id
  }

  /** 关闭菜单：点击画布/外部区域时调用 */
  function closeAddMenu(): void {
    addMenuId.value = null
  }

  function setZoom(value: number): void {
    zoom.value = Math.min(2, Math.max(0.5, Math.round(value * 10) / 10))
  }

  function resetDoc(): void {
    doc.value = createDefaultDoc()
    activeNodeId.value = null
    addMenuId.value = null
    zoom.value = 1
  }

  /** 导入 JSON（含基础结构校验） */
  function loadDoc(json: string): boolean {
    try {
      const parsed: unknown = JSON.parse(json)
      if (typeof parsed !== 'object' || parsed === null || !('root' in parsed)) {
        return false
      }
      const candidate = parsed as WorkflowDoc
      if (!candidate.root || candidate.root.type !== NodeType.START) {
        return false
      }
      doc.value = sanitizeDoc(candidate)
      activeNodeId.value = null
      return true
    } catch {
      return false
    }
  }

  function exportDoc(): string {
    doc.value.updatedAt = Date.now()
    return JSON.stringify(doc.value, null, 2)
  }

  return {
    doc,
    zoom,
    activeNodeId,
    activeNode,
    addMenuId,
    insertNode,
    deleteNode,
    copyNode,
    updateNode,
    addBranch,
    deleteBranch,
    resolveBranchDeleteAction,
    moveBranch,
    getRejectTargets,
    conditionsRoutingOf,
    updateStartInitiators,
    setActive,
    setZoom,
    openAddMenu,
    closeAddMenu,
    resetDoc,
    loadDoc,
    exportDoc,
  }
})
