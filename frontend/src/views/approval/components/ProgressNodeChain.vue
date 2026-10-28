<script setup lang="ts">
import type { Component } from 'vue'
import { computed } from 'vue'
import { Avatar, Connection, Tickets, User } from '@element-plus/icons-vue'
import type { NodeProgressStatus, NodeProgressVO } from '@/types/approval'
import { NodeType, type WorkflowNode } from '@/types/workflow'

/**
 * 流程进度链递归单元（只读）：办理类节点按 nodeProgress 状态着色，
 * 分支容器渲染为多列盒子（列头状态由列内子树聚合），子链纵向递归。
 */
const props = defineProps<{
  node: WorkflowNode
  /** nodeId → 节点进度映射（外壳构建一次，逐层透传） */
  progress: Map<string, NodeProgressVO>
}>()

const emit = defineEmits<{ (e: 'open-sub', instanceId: string): void }>()

/** 节点类型图标与主题色（语义同设计器 NodeCard） */
const META: Record<string, { icon: Component; color: string }> = {
  [NodeType.START]: { icon: User, color: '#409EFF' },
  [NodeType.APPROVER]: { icon: Avatar, color: '#FF9438' },
  [NodeType.TRANSACT]: { icon: Tickets, color: '#9A6FE8' },
  [NodeType.SUB_PROCESS]: { icon: Connection, color: '#15B8A5' },
}

/** 节点类型徽标文案 */
const TYPE_LABELS: Record<string, string> = {
  [NodeType.START]: '发起人',
  [NodeType.APPROVER]: '审批',
  [NodeType.TRANSACT]: '办理',
  [NodeType.SUB_PROCESS]: '子流程',
}

const STATUS_META: Record<NodeProgressStatus, { label: string; tag: 'success' | 'primary' | 'danger' | 'info' }> = {
  PASSED: { label: '已通过', tag: 'success' },
  CURRENT: { label: '进行中', tag: 'primary' },
  REJECTED: { label: '已拒绝', tag: 'danger' },
  CANCELED: { label: '已取消', tag: 'info' },
  PENDING: { label: '未开始', tag: 'info' },
}

/** 列状态聚合优先级：进行中 > 已拒绝 > 已取消 > 已通过 > 未开始 */
const STATUS_RANK: Record<NodeProgressStatus, number> = {
  CURRENT: 4,
  REJECTED: 3,
  CANCELED: 2,
  PASSED: 1,
  PENDING: 0,
}

const isContainer = computed(() => props.node.type === NodeType.CONDITIONS || props.node.type === NodeType.PARALLEL)

const info = computed<NodeProgressVO | undefined>(() => props.progress.get(props.node.id))

const status = computed<NodeProgressStatus>(() => info.value?.status ?? 'PENDING')

const meta = computed(() => META[props.node.type] ?? META[NodeType.APPROVER])

const typeLabel = computed(() => TYPE_LABELS[props.node.type] ?? '')

const statusMeta = computed(() => STATUS_META[status.value])

const cols = computed(() => props.node.conditionNodes ?? [])

/** 副行文案：进行中=当前待办人；已通过=办理人 + 完成时间 */
const subLine = computed(() => {
  if (!info.value) return ''
  if (status.value === 'CURRENT') {
    const names = info.value.assigneeNames.join('、')
    return names ? `当前待办：${names}` : '进行中'
  }
  if (status.value === 'PASSED') {
    const names = info.value.assigneeNames.join('、')
    const time = info.value.finishTime ? ` · ${info.value.finishTime}` : ''
    return `${names || '已办理'}${time}`
  }
  return info.value.finishTime ?? ''
})

/** 子流程节点是否可跳转子实例详情（有对应子实例且已启动） */
const canOpenSub = computed(
  () => props.node.type === NodeType.SUB_PROCESS && !!info.value?.subInstanceId && status.value !== 'PENDING',
)

/** 分支列头状态：列内子树聚合（列本身无进度项） */
function colStatus(col: WorkflowNode): NodeProgressStatus {
  let best: NodeProgressStatus = 'PENDING'
  const consider = (candidate: NodeProgressStatus) => {
    if (STATUS_RANK[candidate] > STATUS_RANK[best]) {
      best = candidate
    }
  }
  const walk = (subtree: WorkflowNode | null | undefined) => {
    if (!subtree) return
    consider(props.progress.get(subtree.id)?.status ?? 'PENDING')
    for (const branch of subtree.conditionNodes ?? []) {
      walk(branch)
    }
    walk(subtree.childNode)
  }
  walk(col)
  return best
}

/** 条件列头角标：默认列=「默认」，其余按列序显示优先级；并行列无角标 */
function colBadge(col: WorkflowNode, idx: number): string {
  if (props.node.type !== NodeType.CONDITIONS) return ''
  return col.props?.isDefault ? '默认' : `优先级 ${idx + 1}`
}

function openSub(): void {
  if (info.value?.subInstanceId) {
    emit('open-sub', info.value.subInstanceId)
  }
}
</script>

<template>
  <div class="p-chain">
    <!-- 办理类节点卡片 -->
    <div v-if="!isContainer" class="p-node" :class="`is-${status}`">
      <div class="p-node-main">
        <span class="p-node-icon" :style="{ color: meta.color, backgroundColor: meta.color + '1a' }">
          <el-icon :size="15"><component :is="meta.icon" /></el-icon>
        </span>
        <span class="p-node-name">{{ node.name }}</span>
        <span class="p-node-type">{{ typeLabel }}</span>
        <el-tag :type="statusMeta.tag" size="small" round effect="light">{{ statusMeta.label }}</el-tag>
        <a v-if="canOpenSub" class="p-node-sub-link" @click.prevent="openSub">子实例详情 &gt;</a>
      </div>
      <div v-if="subLine" class="p-node-sub">{{ subLine }}</div>
    </div>

    <!-- 分支容器：多列盒子 -->
    <div v-else class="p-branch">
      <div class="p-branch-title">{{ node.type === NodeType.CONDITIONS ? '条件分支' : '并行分支' }}</div>
      <div class="p-branch-cols">
        <div v-for="(col, idx) in cols" :key="col.id" class="p-branch-col">
          <div class="p-col-head" :class="`is-${colStatus(col)}`">
            <span class="p-col-dot" />
            <span class="p-col-name">{{ col.name }}</span>
            <span v-if="colBadge(col, idx)" class="p-col-badge">{{ colBadge(col, idx) }}</span>
          </div>
          <div class="p-col-body">
            <ProgressNodeChain
              v-if="col.childNode"
              :node="col.childNode"
              :progress="progress"
              @open-sub="emit('open-sub', $event)"
            />
            <div v-else class="p-col-empty">空分支 · 直接通过</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 后继链：连线随前驱状态着色 -->
    <template v-if="node.childNode">
      <div class="p-link" :class="`is-${status}`" />
      <ProgressNodeChain :node="node.childNode" :progress="progress" @open-sub="emit('open-sub', $event)" />
    </template>
  </div>
</template>

<style scoped>
.p-chain {
  display: flex;
  flex-direction: column;
  align-items: center;
}

/* ---- 节点卡片 ---- */
.p-node {
  width: 320px;
  box-sizing: border-box;
  padding: 10px 12px;
  background: #ffffff;
  border: 1px solid var(--el-border-color);
  border-left-width: 3px;
  border-radius: 8px;
  transition: box-shadow 0.2s;
}

.p-node.is-PASSED {
  border-left-color: var(--el-color-success);
}

.p-node.is-CURRENT {
  border-color: var(--el-color-primary);
  border-left-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  box-shadow: 0 0 0 3px var(--el-color-primary-light-8);
}

.p-node.is-REJECTED {
  border-left-color: var(--el-color-danger);
}

.p-node.is-CANCELED,
.p-node.is-PENDING {
  border-left-color: var(--el-border-color);
  opacity: 0.6;
}

.p-node-main {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.p-node-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 6px;
  flex-shrink: 0;
}

.p-node-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.p-node-type {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.p-node-sub-link {
  margin-left: auto;
  font-size: 12px;
  color: var(--el-color-primary);
  cursor: pointer;
}

.p-node-sub {
  margin-top: 6px;
  padding-left: 34px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  word-break: break-all;
}

/* ---- 连线 ---- */
.p-link {
  width: 2px;
  height: 22px;
  background: var(--el-border-color);
}

.p-link.is-PASSED {
  background: var(--el-color-success);
}

.p-link.is-CURRENT {
  background: var(--el-color-primary);
}

.p-link.is-REJECTED {
  background: var(--el-color-danger);
}

/* ---- 分支容器 ---- */
.p-branch {
  width: 100%;
  border: 1px dashed #8a94e6;
  border-radius: 10px;
  background: rgba(138, 148, 230, 0.06);
  padding: 10px 12px 12px;
  box-sizing: border-box;
}

.p-branch-title {
  font-size: 12px;
  color: #6b74b8;
  margin-bottom: 8px;
}

.p-branch-cols {
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
}

.p-branch-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 180px;
}

.p-col-head {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 999px;
  background: #ffffff;
  border: 1px solid var(--el-border-color);
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.p-col-head.is-CURRENT {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.p-col-head.is-PASSED {
  border-color: var(--el-color-success);
}

.p-col-head.is-REJECTED {
  border-color: var(--el-color-danger);
}

.p-col-head.is-CANCELED,
.p-col-head.is-PENDING {
  opacity: 0.7;
}

.p-col-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-text-color-placeholder);
}

.p-col-head.is-CURRENT .p-col-dot {
  background: var(--el-color-primary);
}

.p-col-head.is-PASSED .p-col-dot {
  background: var(--el-color-success);
}

.p-col-head.is-REJECTED .p-col-dot {
  background: var(--el-color-danger);
}

.p-col-badge {
  color: var(--el-text-color-secondary);
}

.p-col-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 8px;
}

.p-col-empty {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  padding: 10px 0 14px;
}
</style>
