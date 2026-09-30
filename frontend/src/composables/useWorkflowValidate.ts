import type { Ref } from 'vue'
import { computed } from 'vue'
import { NodeType, type ApproverProps, type ConditionProps, type WorkflowDoc, type WorkflowError, type WorkflowNode } from '@/types/workflow'

/** 先序遍历收集校验错误 */
export function useWorkflowValidate(doc: Ref<WorkflowDoc>) {
  const errors = computed<WorkflowError[]>(() => {
    const list: WorkflowError[] = []
    walk(doc.value.root, list)
    return list
  })

  function walk(node: WorkflowNode | null, list: WorkflowError[]): void {
    if (!node) {
      return
    }
    if (!node.name?.trim()) {
      list.push({ nodeId: node.id, message: '存在未命名的节点' })
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
      if (!p.isDefault && ruleCount === 0) {
        list.push({ nodeId: node.id, message: `条件「${node.name}」未配置规则` })
      }
    }
    if (node.type === NodeType.CONDITIONS && (node.conditionNodes?.length ?? 0) === 0) {
      list.push({ nodeId: node.id, message: '条件分支不存在任何分支列' })
    }
    for (const branch of node.conditionNodes ?? []) {
      walk(branch, list)
    }
    walk(node.childNode, list)
  }

  return { errors }
}
