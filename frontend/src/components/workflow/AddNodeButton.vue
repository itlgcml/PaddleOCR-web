<script setup lang="ts">
import { computed } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { isBranchColumn, NodeType } from '@/types/workflow'
import { useWorkflowStore } from '@/stores/workflow'

const props = defineProps<{ parentId: string; parentType: NodeType }>()

const emit = defineEmits<{ (e: 'add', parentId: string, type: NodeType): void }>()

const store = useWorkflowStore()

/** 菜单展开态收敛在 store：同一时刻只有一个菜单，点击画布外部由画布统一清空 */
const visible = computed(() => store.addMenuId === props.parentId)

const MENU: { type: NodeType; label: string; color: string }[] = [
  { type: NodeType.APPROVER, label: '审批人', color: '#FF9438' },
  { type: NodeType.CONDITIONS, label: '条件分支', color: '#8A94E6' },
  { type: NodeType.PARALLEL, label: '并行节点', color: '#7C6BFF' },
]

/** 分支列之下必须先接普通节点，故不提供分支类选项；分支容器汇合后可继续接分支容器 */
const menuItems = computed(() => (
  isBranchColumn(props.parentType)
    ? MENU.filter((item) => item.type === NodeType.APPROVER)
    : MENU
))

function toggle(): void {
  if (visible.value) {
    store.closeAddMenu()
    return
  }
  store.openAddMenu(props.parentId)
}

/** 幂等：mousedown 与 click 双路径触发时只执行一次 */
function pick(type: NodeType): void {
  if (!visible.value) {
    return
  }
  store.closeAddMenu()
  emit('add', props.parentId, type)
}
</script>

<template>
  <div class="wf-add">
    <span class="wf-add-line" />
    <button class="wf-add-btn" type="button" title="添加节点" @pointerdown.stop @click.stop="toggle">
      <el-icon :size="14"><Plus /></el-icon>
    </button>
    <template v-if="visible">
      <!-- 不加遮罩层：外部点击由画布/页面的 pointerdown 统一关闭；
           遮罩会抢先拦截 pointerdown 并使随后的 click 穿透到下层按钮，导致菜单被重新 toggle 打开 -->
      <!-- 菜单内部阻止 pointerdown 冒泡，避免点击项时先被画布关闭 -->
      <div class="wf-add-menu" @pointerdown.stop @click.stop>
        <button
          v-for="item in menuItems"
          :key="item.type"
          class="wf-add-item"
          type="button"
          @mousedown.prevent="pick(item.type)"
          @click="pick(item.type)"
        >
          <span class="wf-add-dot" :style="{ backgroundColor: item.color }" />
          <span>{{ item.label }}</span>
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.wf-add {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: 10px 0;
}

/* 连线层：z-index 0，置于所有实体（卡片/按钮/菜单）之下 */
.wf-add-line {
  position: absolute;
  z-index: 0;
  top: 0;
  bottom: 0;
  left: 50%;
  width: 2px;
  margin-left: -1px;
  background: #dedfdf;
}

.wf-add-btn {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: #909399;
  cursor: pointer;
  background: #ffffff;
  border: 1px solid #dedfdf;
  border-radius: 50%;
  transition: all 0.2s;
}

.wf-add-btn:hover {
  color: #409eff;
  border-color: #409eff;
  transform: scale(1.1);
}

.wf-add-menu {
  position: absolute;
  top: 50%;
  left: calc(50% + 20px);
  z-index: 20;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 150px;
  padding: 6px;
  background: #ffffff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  box-shadow: 0 2px 12px rgb(0 0 0 / 12%);
  transform: translateY(-50%);
}

.wf-add-item {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 8px 10px;
  font-size: 13px;
  color: #303133;
  cursor: pointer;
  background: none;
  border: none;
  border-radius: 4px;
}

.wf-add-item:hover {
  background: #f5f7fa;
}

.wf-add-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
</style>
