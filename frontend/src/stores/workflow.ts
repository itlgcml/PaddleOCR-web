import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { deepClone } from '@/utils/clone'
import { createNodeProps, NodeType, type ConditionProps, type NodeProps, type NodeType as NodeTypeT, type WorkflowDoc, type WorkflowNode } from '@/types/workflow'

const DRAFT_KEY = 'workflow-draft'

/** 默认节点名 */
const DEFAULT_NAMES: Record<string, string> = {
  [NodeType.START]: '发起人',
  [NodeType.APPROVER]: '审批人',
  [NodeType.CC]: '抄送人',
  [NodeType.CONDITIONS]: '条件分支',
  [NodeType.CONDITION]: '条件',
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

function restoreDraft(): WorkflowDoc | null {
  try {
    const raw = localStorage.getItem(DRAFT_KEY)
    if (!raw) {
      return null
    }
    const parsed: unknown = JSON.parse(raw)
    if (typeof parsed === 'object' && parsed !== null && 'root' in parsed) {
      return parsed as WorkflowDoc
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

  /** 条件分支容器加列：自动编号命名，且插到默认列之前（默认列固定最右） */
  function addConditionBranch(containerId: string): WorkflowNode | null {
    const container = findNode(doc.value.root, containerId)
    if (!container || container.type !== NodeType.CONDITIONS) {
      return null
    }
    const branches = container.conditionNodes ?? []
    const count = branches.filter((b) => !(b.props as ConditionProps).isDefault).length
    const branch = createNode(NodeType.CONDITION, { name: `条件${count + 1}` })
    const defaultIdx = branches.findIndex((b) => (b.props as ConditionProps).isDefault)
    if (defaultIdx >= 0) {
      branches.splice(defaultIdx, 0, branch)
    } else {
      branches.push(branch)
    }
    container.conditionNodes = [...branches]
    return branch
  }

  /** 删除分支列；仅剩 1 列时返回 false（由 UI 提示） */
  function deleteConditionBranch(id: string): boolean {
    const container = findParent(doc.value.root, id)
    const branches = container?.conditionNodes
    if (!container || !branches || branches.length <= 1) {
      return false
    }
    const idx = branches.findIndex((b) => b.id === id)
    if (idx < 0 || (branches[idx].props as ConditionProps).isDefault) {
      return false
    }
    branches.splice(idx, 1)
    return true
  }

  /** 调整分支列优先级（与相邻列交换；默认列固定最右不参与） */
  function moveConditionBranch(id: string, dir: -1 | 1): boolean {
    const container = findParent(doc.value.root, id)
    const branches = container?.conditionNodes
    if (!branches) {
      return false
    }
    const idx = branches.findIndex((b) => b.id === id)
    const target = idx + dir
    if (
      idx < 0
      || target < 0
      || target >= branches.length
      || (branches[idx].props as ConditionProps).isDefault
      || (branches[target].props as ConditionProps).isDefault
    ) {
      return false
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
      doc.value = candidate
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
    addConditionBranch,
    deleteConditionBranch,
    moveConditionBranch,
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
