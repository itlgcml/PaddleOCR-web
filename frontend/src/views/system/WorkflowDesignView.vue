<script setup lang="ts">
import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { isBranchContainer, NodeType, type BranchMoveAction, type NodeProps, type WorkflowNode } from '@/types/workflow'
import { useWorkflowStore } from '@/stores/workflow'
import { useWorkflowValidate } from '@/composables/useWorkflowValidate'
import WorkflowToolbar from '@/components/workflow/WorkflowToolbar.vue'
import WorkflowCanvas from '@/components/workflow/WorkflowCanvas.vue'
import NodePropDrawer from '@/components/workflow/NodePropDrawer.vue'

const store = useWorkflowStore()
const { doc, zoom, activeNodeId, activeNode } = storeToRefs(store)
const { errors } = useWorkflowValidate(doc)

const drawerVisible = ref(false)
const flowName = computed({
  get: () => doc.value.name,
  set: (v: string) => {
    doc.value.name = v
  },
})

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

async function onDelBranch(id: string): Promise<void> {
  try {
    await ElMessageBox.confirm('删除该分支列将连同其内部子流程一并删除，确定吗？', '删除分支', { type: 'warning' })
  } catch {
    return
  }
  if (!store.deleteBranch(id)) {
    ElMessage.warning('该分支不可删除：默认条件列，或分支数量已达下限')
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

/** 工具栏 */
function onSave(): void {
  store.saveDraft()
  ElMessage.success('已保存到本地草稿')
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
  <div class="wf-designer" @pointerdown="store.closeAddMenu()">
    <WorkflowToolbar
      v-model:flow-name="flowName"
      v-model:zoom="zoom"
      @save="onSave"
      @validate="onValidate"
      @fit="store.setZoom(1)"
      @import="onImport"
      @export="onExport"
      @reset="onReset"
    />

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

.wf-designer-main {
  flex: 1;
  min-height: 0;
}
</style>
