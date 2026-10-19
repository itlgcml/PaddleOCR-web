import type { Ref } from 'vue'
import { computed } from 'vue'
import { NodeType, type ApproverProps, type ConditionProps, type ConditionsProps, type WorkflowDoc, type WorkflowError, type WorkflowNode } from '@/types/workflow'

/** 先序遍历收集校验错误 */
export function useWorkflowValidate(doc: Ref<WorkflowDoc>) {
  const errors = computed<WorkflowError[]>(() => {
    const list: WorkflowError[] = []
    walk(doc.value.root, list, null)
    return list
  })

  function walk(
    node: WorkflowNode | null,
    list: WorkflowError[],
    parentRouting: 'AUTO' | 'MANUAL' | null,
  ): void {
    if (!node) {
      return
    }
    if (!node.name?.trim()) {
      list.push({ nodeId: node.id, message: '存在未命名的节点' })
    }
    const routing = node.type === NodeType.CONDITIONS || node.type === NodeType.PARALLEL
      ? ((node.props as ConditionsProps).routing ?? 'AUTO')
      : null
    if (node.type === NodeType.CONDITIONS) {
      if ((node.conditionNodes?.length ?? 0) === 0) {
        list.push({ nodeId: node.id, message: '条件分支不存在任何分支列' })
      } else if (routing === 'MANUAL') {
        if ((node.conditionNodes?.length ?? 0) < 2) {
          list.push({ nodeId: node.id, message: `人工路由节点「${node.name}」至少需要 2 个选项` })
        }
        for (const column of node.conditionNodes ?? []) {
          const label = (column.props as ConditionProps).label
          if (!label?.trim()) {
            list.push({ nodeId: column.id, message: `选项「${column.name}」未配置名称` })
          }
        }
      }
    }
    if (node.type === NodeType.PARALLEL && routing === 'MANUAL') {
      if ((node.conditionNodes?.length ?? 0) < 2) {
        list.push({ nodeId: node.id, message: `人工并行节点「${node.name}」至少需要 2 个分支` })
      }
    }
    if (node.type === NodeType.APPROVER) {
      const p = node.props as ApproverProps
      // 指定审批人：人员与角色的并集至少选择一个
      const empty = (p.memberIds?.length ?? 0) === 0 && (p.roleIds?.length ?? 0) === 0
      if (p.source === 'MEMBER' && empty) {
        list.push({ nodeId: node.id, message: `审批节点「${node.name}」未选择审批人` })
      }
    }
    if (node.type === NodeType.CONDITION) {
      const p = node.props as ConditionProps
      const ruleCount = (p.groups ?? []).reduce((total, group) => total + group.rules.length, 0)
      if (parentRouting !== 'MANUAL' && !p.isDefault && ruleCount === 0) {
        list.push({ nodeId: node.id, message: `条件「${node.name}」未配置规则` })
      }
    }
    for (const branch of node.conditionNodes ?? []) {
      walk(branch, list, routing)
    }
    walk(node.childNode, list, parentRouting)
  }

  return { errors }
}
