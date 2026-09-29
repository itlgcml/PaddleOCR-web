import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { deepClone } from '@/utils/clone'
import { createNodeProps, isBranchContainer, NodeType, type ConditionProps, type NodeProps, type NodeType as NodeTypeT, type WorkflowDoc, type WorkflowNode } from '@/types/workflow'

const DRAFT_KEY = 'workflow-draft'

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

/** 取容器内尚未占用的「条件n」编号（从 1 起，跳过已被占用的编号） */
function nextConditionIndex(branches: WorkflowNode[]): number {
  const used = new Set<number>()
  for (const branch of branches) {
    const matched = /^条件(\d+)$/.exec(branch.name)
    if (matched) {
      used.add(Number(matched[1]))
    }
  }
  let index = 1
  while (used.has(index)) {
    index += 1
  }
  return index
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

/**
 * 旧草稿兼容：剔除已被移除类型的节点，并把它的后继接驳到父级，避免主链断裂。
 * 先清理子树再判定自身，这样自身被剔除时可以直接把已清理的后继顶上来。
 */
function sanitizeNode(node: WorkflowNode | null): WorkflowNode | null {
  if (!node) {
    return null
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
  }
  return SUPPORTED_NODE_TYPES.has(node.type) ? node : node.childNode
}

/** 根节点兜底：整棵树都被剔除时退回一个全新的发起人节点 */
function sanitizeDoc(doc: WorkflowDoc): WorkflowDoc {
  doc.root = sanitizeNode(doc.root) ?? createNode(NodeType.START)
  return doc
}

function restoreDraft(): WorkflowDoc | null {
  try {
    const raw = localStorage.getItem(DRAFT_KEY)
    if (!raw) {
      return null
    }
    const parsed: unknown = JSON.parse(raw)
    if (typeof parsed === 'object' && parsed !== null && 'root' in parsed) {
      return sanitizeDoc(parsed as WorkflowDoc)
    }
    return null
  } catch {
    return null
  }
}

export const useWorkflowStore = defineStore('workflow', () => {
  const doc = ref<WorkflowDoc>(restoreDraft() ?? createDefaultDoc())
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
      // 钉钉式初始形态：条件1 + 默认条件两列，且每列预置一个审批节点
      const first = createNode(NodeType.CONDITION, { name: '条件1' })
      const defaultBranch = createNode(NodeType.CONDITION, { name: '默认条件' })
      ;(defaultBranch.props as ConditionProps).isDefault = true
      first.childNode = createNode(NodeType.APPROVER)
      defaultBranch.childNode = createNode(NodeType.APPROVER)
      node.conditionNodes = [first, defaultBranch]
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

  /** 分支容器加列：自动编号命名；条件分支插到默认列之前，并行节点追加到末尾 */
  function addBranch(containerId: string): WorkflowNode | null {
    const container = findNode(doc.value.root, containerId)
    if (!container || !isBranchContainer(container.type)) {
      return null
    }
    const branches = container.conditionNodes ?? []
    const parallel = container.type === NodeType.PARALLEL
    const count = branches.filter((b) => !(b.props as ConditionProps).isDefault).length
    const branch = createNode(
      parallel ? NodeType.PARALLEL_BRANCH : NodeType.CONDITION,
      { name: `${parallel ? '并行' : '条件'}${count + 1}` },
    )
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
    return branch
  }

  /** 删除分支列：条件分支至少留 1 列、并行节点至少留 2 列，低于下限返回 false（由 UI 提示） */
  function deleteBranch(id: string): boolean {
    const container = findParent(doc.value.root, id)
    const branches = container?.conditionNodes
    if (!container || !branches) {
      return false
    }
    const idx = branches.findIndex((b) => b.id === id)
    if (idx < 0 || (branches[idx].props as ConditionProps).isDefault) {
      return false
    }
    const minCount = container.type === NodeType.PARALLEL ? 2 : 1
    if (branches.length <= minCount) {
      return false
    }
    branches.splice(idx, 1)
    return true
  }

  /** 调整分支列顺序：与相邻列交换；右侧紧邻默认列时，两列互换位置并转让默认身份 */
  function moveBranch(id: string, dir: -1 | 1): boolean {
    const container = findParent(doc.value.root, id)
    const branches = container?.conditionNodes
    if (!branches) {
      return false
    }
    const idx = branches.findIndex((b) => b.id === id)
    const target = idx + dir
    // 默认列自身不参与排序
    if (
      idx < 0
      || target < 0
      || target >= branches.length
      || (branches[idx].props as ConditionProps).isDefault
    ) {
      return false
    }
    const neighbor = branches[target]
    if ((neighbor.props as ConditionProps).isDefault) {
      // 默认列固定最右：整列互换位置，默认身份随「落到末尾」的那一列转移
      const moving = branches[idx]
      branches[target] = moving
      branches[idx] = neighbor
      ;(moving.props as ConditionProps).isDefault = true
      moving.name = '默认条件'
      // 原默认列降级为普通条件列，取容器内未占用的编号重建名称（其规则需重新配置）
      ;(neighbor.props as ConditionProps).isDefault = false
      neighbor.name = `条件${nextConditionIndex(branches.filter((b) => b.id !== neighbor.id))}`
      return true
    }
    ;[branches[idx], branches[target]] = [branches[target], branches[idx]]
    return true
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

  function saveDraft(): void {
    try {
      localStorage.setItem(DRAFT_KEY, exportDoc())
    } catch {
      // 存储满等异常静默忽略，不影响主流程
    }
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
    moveBranch,
    setActive,
    setZoom,
    openAddMenu,
    closeAddMenu,
    resetDoc,
    loadDoc,
    exportDoc,
    saveDraft,
  }
})
