<script setup lang="ts">
import { computed } from 'vue'
import type { NodeProgressVO } from '@/types/approval'
import type { WorkflowDoc } from '@/types/workflow'
import ProgressNodeChain from './ProgressNodeChain.vue'

/**
 * 审批流程树（实例详情「流程图」页签）：按版本快照 designJson 渲染只读流程树，
 * 节点状态取实例详情下发的 nodeProgress——已通过/进行中/未开始分开着色。
 */
const props = defineProps<{
  /** 版本快照 WorkflowDoc JSON 字符串 */
  designJson: string | null
  /** 节点进度列表（实例详情下发） */
  progress: NodeProgressVO[]
}>()

const emit = defineEmits<{ (e: 'open-sub', instanceId: string): void }>()

const doc = computed<WorkflowDoc | null>(() => {
  if (!props.designJson) return null
  try {
    const parsed: unknown = JSON.parse(props.designJson)
    // JSON.parse 结果无法静态推导，按 WorkflowDoc 契约断言（后端版本快照）
    if (typeof parsed !== 'object' || parsed === null || !('root' in parsed)) return null
    return parsed as WorkflowDoc
  } catch {
    return null
  }
})

const progressMap = computed(() => {
  const map = new Map<string, NodeProgressVO>()
  for (const item of props.progress) {
    map.set(item.nodeId, item)
  }
  return map
})
</script>

<template>
  <div class="flow-progress-tree">
    <div class="fp-legend">
      <span class="fp-legend-item"><i class="fp-dot is-PASSED" />已通过</span>
      <span class="fp-legend-item"><i class="fp-dot is-CURRENT" />进行中</span>
      <span class="fp-legend-item"><i class="fp-dot is-REJECTED" />已拒绝</span>
      <span class="fp-legend-item"><i class="fp-dot is-CANCELED" />已取消</span>
      <span class="fp-legend-item"><i class="fp-dot is-PENDING" />未开始</span>
    </div>
    <div v-if="doc && doc.root" class="fp-canvas">
      <ProgressNodeChain :node="doc.root" :progress="progressMap" @open-sub="emit('open-sub', $event)" />
    </div>
    <el-empty v-else description="暂无流程设计快照" :image-size="72" />
  </div>
</template>

<style scoped>
.flow-progress-tree {
  padding: 16px 8px 24px;
}

.fp-legend {
  display: flex;
  justify-content: center;
  gap: 20px;
  flex-wrap: wrap;
  margin-bottom: 18px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.fp-legend-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.fp-dot {
  width: 10px;
  height: 10px;
  border-radius: 3px;
}

.fp-dot.is-PASSED {
  background: var(--el-color-success);
}

.fp-dot.is-CURRENT {
  background: var(--el-color-primary);
}

.fp-dot.is-REJECTED {
  background: var(--el-color-danger);
}

.fp-dot.is-CANCELED {
  background: var(--el-text-color-placeholder);
}

.fp-dot.is-PENDING {
  background: #ffffff;
  border: 1px solid var(--el-border-color);
  box-sizing: border-box;
}

.fp-canvas {
  overflow-x: auto;
}
</style>
