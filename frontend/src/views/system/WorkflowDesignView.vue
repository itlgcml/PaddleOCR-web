<script setup lang="ts">
import { ref } from 'vue'
import { storeToRefs } from 'pinia'
import { isBranchContainer, NodeType, type BranchDeleteAction, type BranchMoveAction, type NodeProps, type WorkflowNode } from '@/types/workflow'
import { useWorkflowStore } from '@/stores/workflow'
import { useWorkflowValidate } from '@/composables/useWorkflowValidate'
import WorkflowToolbar from '@/components/workflow/WorkflowToolbar.vue'
import WorkflowCanvas from '@/components/workflow/WorkflowCanvas.vue'
import NodePropDrawer from '@/components/workflow/NodePropDrawer.vue'

const store = useWorkflowStore()
const { doc, zoom, activeNodeId, activeNode } = storeToRefs(store)
const { errors } = useWorkflowValidate(doc)

const drawerVisible = ref(false)
const fullscreen = ref(false)

/** 画布统一事件入口（经 $attrs 逐层透传） */
function onOpen(node: WorkflowNode): void {
  store.setActive(node.id)
  drawerVisible.value = true
}

function onAdd(parentId: string, type: NodeType): void {
  const node = store.insertNode(parentId, type)
  if (!node) {
    ElMessage.error('添加失败')
    return
  }
  // 分支容器（条件分支 / 并行节点）：容器无属性表单，直接呈现多列，不弹任何面板
  if (isBranchContainer(type)) {
    return
  }
  onOpen(node)
}

async function onCopy(id: string): Promise<void> {
  if (!store.copyNode(id)) {
    ElMessage.error('复制失败')
  }
}

async function onRemove(id: string): Promise<void> {
  try {
    await ElMessageBox.confirm('删除该节点将连同其内部子流程一并删除，确定吗？', '删除节点', { type: 'warning' })
  } catch {
    return
  }
  if (activeNodeId.value === id) {
    drawerVisible.value = false
  }
  store.deleteNode(id)
}

async function onAddBranch(containerId: string): Promise<void> {
  const branch = store.addBranch(containerId)
  if (!branch) {
    ElMessage.error('添加分支失败')
    return
  }
  onOpen(branch)
}

/** 分支删除确认文案：按删除动作区分（只删列 / 删整个条件分支节点 / 删整个并行节点） */
const BRANCH_DELETE_CONFIRM: Record<Exclude<BranchDeleteAction, 'NONE'>, { title: string; message: string }> = {
  COLUMN: {
    title: '删除分支',
    message: '删除该分支列将连同其内部子流程一并删除，确定吗？',
  },
  BRANCH: {
    title: '删除条件分支节点',
    message: '该条件分支仅剩「1 个条件 + 1 个其他条件」，删除会连同整个条件分支节点及其内部子流程一并移除，确定吗？',
  },
  PARALLEL: {
    title: '删除并行节点',
    message: '该并行节点仅剩 2 个并行节点，删除会连同整个并行节点及其内部子流程一并移除，确定吗？',
  },
}

async function onDelBranch(id: string): Promise<void> {
  const action = store.resolveBranchDeleteAction(id)
  if (action === 'NONE') {
    ElMessage.warning('其他条件列不可删除')
    return
  }
  const { title, message } = BRANCH_DELETE_CONFIRM[action]
  try {
    await ElMessageBox.confirm(message, title, { type: 'warning' })
  } catch {
    return
  }
  if (!store.deleteBranch(id)) {
    ElMessage.error('删除失败')
    return
  }
  // 删的是整个节点（条件分支 / 并行节点）时，同步收起属性抽屉
  if (action !== 'COLUMN') {
    drawerVisible.value = false
  }
}

function onMoveBranch(id: string, action: BranchMoveAction): void {
  store.moveBranch(id, action)
}

function onDrawerSave(id: string, nodeProps: unknown, name: string): void {
  store.updateNode(id, nodeProps as NodeProps, name)
  store.setActive(null)
}

function onDrawerClose(): void {
  store.setActive(null)
}

function onValidate(): void {
  if (errors.value.length === 0) {
    ElMessage.success('校验通过')
    return
  }
  ElMessage.error(`校验发现 ${errors.value.length} 个问题：${errors.value[0].message}`)
  store.setActive(errors.value[0].nodeId)
}

function onImport(text: string): void {
  if (store.loadDoc(text)) {
    drawerVisible.value = false
    ElMessage.success('导入成功')
  } else {
    ElMessage.error('导入失败：JSON 结构不合法')
  }
}

function onExport(): void {
  const blob = new Blob([store.exportDoc()], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `${doc.value.name || 'workflow'}.json`
  anchor.click()
  URL.revokeObjectURL(url)
}

async function onReset(): Promise<void> {
  try {
    await ElMessageBox.confirm('将清空当前流程并恢复为初始模板，确定吗？', '重置流程', { type: 'warning' })
  } catch {
    return
  }
  drawerVisible.value = false
  store.resetDoc()
}
</script>

<template>
  <!-- 页面任意位置按下即关闭「添加节点」菜单（菜单内部已阻止冒泡） -->
  <div class="wf-designer" :class="{ 'is-fullscreen': fullscreen }" @pointerdown="store.closeAddMenu()">
    <WorkflowToolbar
      v-model:zoom="zoom"
      v-model:fullscreen="fullscreen"
      @validate="onValidate"
      @import="onImport"
      @export="onExport"
      @reset="onReset"
    >
      <!-- 工具栏右侧扩展位透传：由使用方（如流程配置向导）注入版本视图切换器，未注入时该插槽为空 -->
      <template #version>
        <slot name="version" />
      </template>
    </WorkflowToolbar>

    <div class="wf-designer-main">
      <WorkflowCanvas
        :root="doc.root"
        :zoom="zoom"
        :active-id="activeNodeId"
        @open="onOpen"
        @add="onAdd"
        @copy="onCopy"
        @remove="onRemove"
        @add-branch="onAddBranch"
        @del-branch="onDelBranch"
        @move-branch="onMoveBranch"
      />
    </div>

    <NodePropDrawer v-model="drawerVisible" :node="activeNode" @save="onDrawerSave" @close="onDrawerClose" />
  </div>
</template>

<style scoped>
.wf-designer {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: #ffffff;
  border-radius: 6px;
}

.wf-designer.is-fullscreen {
  position: fixed;
  inset: 0;
  /* 低于 Element Plus 弹窗默认层级（2000），避免遮住重置/删除等确认框 */
  z-index: 1000;
  border-radius: 0;
}

.wf-designer-main {
  flex: 1;
  min-height: 0;
}
</style>
