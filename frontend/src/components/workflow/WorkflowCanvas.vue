<script setup lang="ts">
import type { WorkflowNode } from '@/types/workflow'
import { useWorkflowStore } from '@/stores/workflow'
import NodeChain from './NodeChain.vue'

defineOptions({ inheritAttrs: false })

defineProps<{
  root: WorkflowNode
  zoom: number
  activeId: string | null
}>()

const store = useWorkflowStore()
</script>

<template>
  <!-- 画布内任意位置按下即关闭「添加节点」菜单 -->
  <div class="wf-canvas" @pointerdown="store.closeAddMenu()">
    <div class="wf-canvas-scale" :style="{ transform: `scale(${zoom})` }">
      <div class="wf-canvas-flow">
        <NodeChain :node="root" :active-id="activeId" v-bind="$attrs" />
        <!-- 结束节点：静态渲染，不入数据结构 -->
        <div class="wf-end">
          <div class="wf-end-dot" />
          <span>流程结束</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.wf-canvas {
  position: relative;
  height: 100%;
  overflow: auto;
  background-color: #f5f6f7;
  background-image: radial-gradient(#e3e5e8 1px, transparent 1px);
  background-size: 16px 16px;
}

.wf-canvas-scale {
  min-width: 100%;
  padding: 40px 24px 80px;
  transform-origin: top center;
}

.wf-canvas-flow {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: fit-content;
  min-width: 100%;
  margin: 0 auto;
}

.wf-end {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: center;
  padding-top: 12px;
  font-size: 12px;
  color: #909399;
}

.wf-end-dot {
  width: 10px;
  height: 10px;
  background: #909399;
  border: 2px solid #dedfdf;
  border-radius: 50%;
}
</style>
