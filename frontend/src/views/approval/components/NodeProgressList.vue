<script setup lang="ts">
import type { NodeProgressVO } from '@/types/approval'

/**
 * 节点进度列表：按后端先序遍历的扁平 nodes 渲染垂直进度（画布状态着色的轻量替代）。
 * CURRENT=进行中（蓝）PASSED=已通过（绿）REJECTED=已拒绝（红）PENDING/SKIPPED=未到达或跳过（灰）
 */
const props = defineProps<{ nodes: NodeProgressVO[] }>()

const STATUS_META: Record<string, { label: string; cls: string }> = {
  CURRENT: { label: '进行中', cls: 'is-current' },
  PENDING: { label: '未到达', cls: 'is-pending' },
  PASSED: { label: '已通过', cls: 'is-passed' },
  REJECTED: { label: '已拒绝', cls: 'is-rejected' },
  SKIPPED: { label: '已跳过', cls: 'is-pending' },
}

const NODE_TYPE_LABELS: Record<string, string> = {
  START: '发起人',
  APPROVER: '审批',
  CONDITIONS: '条件分支',
  CONDITION: '条件',
  PARALLEL: '并行',
  PARALLEL_BRANCH: '并行分支',
}

function metaOf(status: string): { label: string; cls: string } {
  return STATUS_META[status] ?? STATUS_META.PENDING
}

function typeLabelOf(nodeType: string): string {
  return NODE_TYPE_LABELS[nodeType] ?? nodeType
}
</script>

<template>
  <div class="node-progress">
    <div v-for="(node, index) in props.nodes" :key="node.nodeId" class="progress-item">
      <div class="progress-rail">
        <span class="progress-dot" :class="metaOf(node.status).cls" />
        <span v-if="index < props.nodes.length - 1" class="progress-line" :class="metaOf(node.status).cls" />
      </div>
      <div class="progress-body">
        <div class="progress-head">
          <span class="progress-name">{{ node.nodeName }}</span>
          <span class="progress-type">{{ typeLabelOf(node.nodeType) }}</span>
          <span class="progress-status" :class="metaOf(node.status).cls">{{ metaOf(node.status).label }}</span>
          <span v-if="node.finishTime" class="progress-time">{{ node.finishTime }}</span>
        </div>
        <div v-if="node.assigneeNames.length > 0" class="progress-assignees">
          {{ node.assigneeNames.join('、') }}
        </div>
      </div>
    </div>
    <el-empty v-if="props.nodes.length === 0" description="暂无节点进度" :image-size="72" />
  </div>
</template>

<style scoped>
.node-progress {
  display: flex;
  flex-direction: column;
  padding: 4px 4px 0;
}

.progress-item {
  display: flex;
}

.progress-rail {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 20px;
}

.progress-dot {
  z-index: 1;
  box-sizing: border-box;
  width: 12px;
  height: 12px;
  margin-top: 5px;
  background: #ffffff;
  border: 3px solid #c0c4cc;
  border-radius: 50%;
}

.progress-dot.is-current {
  border-color: #409eff;
  box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.15);
}

.progress-dot.is-passed {
  background: #52c41a;
  border-color: #52c41a;
}

.progress-dot.is-rejected {
  background: #f5222d;
  border-color: #f5222d;
}

.progress-line {
  flex: 1 1 auto;
  width: 2px;
  min-height: 18px;
  margin: 2px 0;
  background: #dcdfe6;
}

.progress-line.is-passed {
  background: #b7eb8f;
}

.progress-body {
  flex: 1 1 auto;
  padding: 0 0 18px 8px;
}

.progress-head {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 14px;
  line-height: 22px;
}

.progress-name {
  font-weight: 600;
  color: #303133;
}

.progress-type {
  padding: 0 8px;
  font-size: 12px;
  line-height: 20px;
  color: #909399;
  background-color: #f4f4f5;
  border-radius: 10px;
}

.progress-status {
  font-size: 13px;
}

.progress-status.is-current {
  color: #409eff;
}

.progress-status.is-passed {
  color: #52c41a;
}

.progress-status.is-rejected {
  color: #f5222d;
}

.progress-status.is-pending {
  color: #909399;
}

.progress-time {
  margin-left: auto;
  font-size: 12px;
  color: #909399;
}

.progress-assignees {
  margin-top: 2px;
  font-size: 13px;
  line-height: 20px;
  color: #606266;
}
</style>
