import type { Ref } from 'vue'
import { computed } from 'vue'
import { MAX_SUB_PROCESS_DEPTH, NodeType, type ApproverProps, type ConditionProps, type ConditionsProps, type SubProcessProps, type SubProcessTrailItem, type WorkflowDoc, type WorkflowError, type WorkflowNode } from '@/types/workflow'

/**
 * 先序遍历收集校验错误（含子流程内嵌 subDoc 递归）。
 * 子流程内的错误项携带「子视图路径」path（外层到内层的子流程节点链），前端据此逐层钻入定位。
 */
export function useWorkflowValidate(doc: Ref<WorkflowDoc>) {
  const errors = computed<WorkflowError[]>(() => {
    const list: WorkflowError[] = []
    walk(doc.value.root, list, null, [], false)
    return list
  })

  function walk(
    node: WorkflowNode | null,
    list: WorkflowError[],
    parentRouting: 'AUTO' | 'MANUAL' | null,
    path: SubProcessTrailItem[],
    inBranchColumn: boolean,
  ): void {
    if (!node) {
      return
    }
    if (!node.name?.trim()) {
      list.push({ nodeId: node.id, message: '存在未命名的节点', path })
    }
    const routing = node.type === NodeType.CONDITIONS || node.type === NodeType.PARALLEL
      ? ((node.props as ConditionsProps).routing ?? 'AUTO')
      : null
    if (node.type === NodeType.CONDITIONS) {
      if ((node.conditionNodes?.length ?? 0) === 0) {
        list.push({ nodeId: node.id, message: '条件分支不存在任何分支列', path })
      } else if (routing === 'MANUAL') {
        if ((node.conditionNodes?.length ?? 0) < 2) {
          list.push({ nodeId: node.id, message: `人工路由节点「${node.name}」至少需要 2 个选项`, path })
        }
        for (const column of node.conditionNodes ?? []) {
          const label = (column.props as ConditionProps).label
          if (!label?.trim()) {
            list.push({ nodeId: column.id, message: `选项「${column.name}」未配置名称`, path })
          }
        }
      }
    }
    if (node.type === NodeType.PARALLEL && routing === 'MANUAL') {
      if ((node.conditionNodes?.length ?? 0) < 2) {
        list.push({ nodeId: node.id, message: `人工并行节点「${node.name}」至少需要 2 个分支`, path })
      }
    }
    if (node.type === NodeType.APPROVER) {
      const p = node.props as ApproverProps
      // 指定审批人：人员与角色的并集至少选择一个
      const empty = (p.memberIds?.length ?? 0) === 0 && (p.roleIds?.length ?? 0) === 0
      if (p.source === 'MEMBER' && empty) {
        list.push({ nodeId: node.id, message: `审批节点「${node.name}」未选择审批人`, path })
      }
    }
    if (node.type === NodeType.CONDITION) {
      const p = node.props as ConditionProps
      const ruleCount = (p.groups ?? []).reduce((total, group) => total + group.rules.length, 0)
      if (parentRouting !== 'MANUAL' && !p.isDefault && ruleCount === 0) {
        list.push({ nodeId: node.id, message: `条件「${node.name}」未配置规则`, path })
      }
    }
    if (node.type === NodeType.SUB_PROCESS) {
      validateSubProcess(node, list, path, inBranchColumn)
    }
    for (const branch of node.conditionNodes ?? []) {
      walk(branch, list, routing, path, true)
    }
    walk(node.childNode, list, parentRouting, path, inBranchColumn)
  }

  /**
   * 子流程节点校验：分支列内禁止摆放（运行态不做跨实例汇合判定）；
   * subDoc 缺失或根节点非发起人则不可发布；嵌套层数超过上限时拦截并停止继续下钻。
   */
  function validateSubProcess(
    node: WorkflowNode,
    list: WorkflowError[],
    path: SubProcessTrailItem[],
    inBranchColumn: boolean,
  ): void {
    if (inBranchColumn) {
      list.push({ nodeId: node.id, message: `子流程「${node.name}」不能放在分支列内`, path })
    }
    const subDoc = (node.props as SubProcessProps).subDoc
    if (!subDoc || subDoc.root?.type !== NodeType.START) {
      list.push({ nodeId: node.id, message: `子流程「${node.name}」的设计缺失或根节点不是发起人`, path })
      return
    }
    const depth = path.length + 1
    if (depth > MAX_SUB_PROCESS_DEPTH) {
      list.push({
        nodeId: node.id,
        message: `子流程「${node.name}」嵌套层数超过上限（最多 ${MAX_SUB_PROCESS_DEPTH} 层）`,
        path,
      })
      return
    }
    // 下钻子设计：子 doc 内为主干链起点（脱离父级的分支列上下文与路由上下文）
    walk(subDoc.root, list, null, [...path, { nodeId: node.id, name: node.name }], false)
  }

  return { errors }
}
