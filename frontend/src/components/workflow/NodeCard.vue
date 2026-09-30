<script setup lang="ts">
import { computed } from 'vue'
import { Avatar, Close, CopyDocument, EditPen, Share, User } from '@element-plus/icons-vue'
import type { Component } from 'vue'
import { NodeType, type ConditionLogic, type ConditionProps, type WorkflowNode } from '@/types/workflow'
import { memberLabel } from './mock'

const props = defineProps<{ node: WorkflowNode; active?: boolean }>()

const emit = defineEmits<{
  (e: 'open', node: WorkflowNode): void
  (e: 'copy', id: string): void
  (e: 'remove', id: string): void
}>()

const META: Record<string, { icon: Component; color: string }> = {
  [NodeType.START]: { icon: User, color: '#409EFF' },
  [NodeType.APPROVER]: { icon: Avatar, color: '#FF9438' },
  [NodeType.CONDITION]: { icon: Share, color: '#8A94E6' },
  [NodeType.CONDITIONS]: { icon: Share, color: '#8A94E6' },
  [NodeType.TRANSACT]: { icon: EditPen, color: '#6C7CFF' },
}

const meta = computed(() => META[props.node.type] ?? META[NodeType.APPROVER])

const description = computed<string>(() => {
  const p = props.node.props
  switch (props.node.type) {
    case NodeType.START: {
      const start = p as { rangeType: string; memberIds?: string[]; roleIds?: string[] }
      if (start.rangeType === 'ALL') {
        return '全体'
      }
      const names = [...(start.memberIds ?? []), ...(start.roleIds ?? [])].map(memberLabel)
      return names.length > 0 ? names.join('、') : '未指定'
    }
    case NodeType.APPROVER: {
      const appr = p as { source: string; memberIds?: string[]; roleIds?: string[]; leaderLevel: number }
      if (appr.source === 'LEADER') {
        return appr.leaderLevel > 1 ? `连续 ${appr.leaderLevel} 级主管` : '直属主管'
      }
      if (appr.source === 'INITIATOR') {
        return '发起人本人'
      }
      if (appr.source === 'SELF_SELECT') {
        return '发起人自选'
      }
      const names = [...(appr.memberIds ?? []), ...(appr.roleIds ?? [])].map(memberLabel)
      return names.length > 0 ? names.join('、') : '请设置审批人'
    }
    case NodeType.CONDITION: {
      const cond = p as ConditionProps
      if (cond.isDefault) {
        return '其余条件均不满足时进入'
      }
      return summarizeCondition(cond) || '请设置条件'
    }
    default:
      return ''
  }
})

/** 条件摘要：组内按各条连接符串联，组间按组连接符串联，多规则组加括号 */
function summarizeCondition(cond: ConditionProps): string {
  const groups = cond.groups.filter((group) => group.rules.length > 0)
  return groups
    .map((group, groupIdx) => {
      const parts: string[] = []
      group.rules.forEach((rule, ruleIdx) => {
        if (ruleIdx > 0) {
          parts.push(logicLabel(rule.link))
        }
        parts.push(`${rule.fieldName} ${operatorLabel(rule.operator)} ${rule.value}`)
      })
      const text = parts.join(' ')
      const wrapped = group.rules.length > 1 ? `（${text}）` : text
      return groupIdx > 0 ? `${logicLabel(group.link)} ${wrapped}` : wrapped
    })
    .join(' ')
}

function logicLabel(logic: ConditionLogic): string {
  return logic === 'OR' ? '或' : '且'
}

function operatorLabel(op: string): string {
  const map: Record<string, string> = {
    EQ: '=', NEQ: '≠', GT: '>', GTE: '≥', LT: '<', LTE: '≤',
    CONTAIN: '包含', NOT_CONTAIN: '不包含',
  }
  return map[op] ?? op
}
</script>

<template>
  <div class="wf-card" :class="{ 'is-active': active }" @click="emit('open', node)">
    <div class="wf-card-head">
      <span class="wf-card-icon" :style="{ color: meta.color, backgroundColor: meta.color + '1a' }">
        <el-icon :size="16"><component :is="meta.icon" /></el-icon>
      </span>
      <span class="wf-card-title">{{ node.name }}</span>
      <span v-if="node.type !== NodeType.START" class="wf-card-actions">
        <el-icon title="复制" @click.stop="emit('copy', node.id)"><CopyDocument /></el-icon>
        <el-icon title="删除" @click.stop="emit('remove', node.id)"><Close /></el-icon>
      </span>
    </div>
    <div class="wf-card-desc">{{ description }}</div>
  </div>
</template>

<style scoped>
/* 实体层：z-index 1，压在连线层（0）之上 */
.wf-card {
  position: relative;
  z-index: 1;
  width: 220px;
  padding: 12px;
  cursor: pointer;
  background: #ffffff;
  border: 1px solid #e3e5e8;
  border-radius: 4px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  transition: box-shadow 0.2s, border-color 0.2s;
  box-sizing: border-box;
}

.wf-card:hover {
  border-color: #409eff;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.12);
}

.wf-card.is-active {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}

.wf-card-head {
  display: flex;
  gap: 8px;
  align-items: center;
}

.wf-card-icon {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
}

.wf-card-title {
  flex: 1;
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wf-card-actions {
  display: none;
  gap: 6px;
  color: #909399;
}

.wf-card:hover .wf-card-actions {
  display: inline-flex;
}

.wf-card-actions .el-icon:hover {
  color: #409eff;
}

.wf-card-desc {
  margin-top: 6px;
  overflow: hidden;
  font-size: 12px;
  color: #909399;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
