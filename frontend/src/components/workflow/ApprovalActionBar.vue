<script setup lang="ts">
import { computed, ref } from 'vue'
import ApprovalActionDialog from './ApprovalActionDialog.vue'
import { ACTION_META, ACTION_ORDER, type ActionOption } from './approval-actions'
import type { ApprovalActionKey, ApprovalActionPayload } from '@/types/workflow'

const props = withDefaults(
  defineProps<{
    /** 展示的按钮；缺省为全部，顺序固定为设计稿顺序 */
    actions?: ApprovalActionKey[]
    /** 人员下拉选项（抄送人 / 新审批人 / 接收人 / 加签处理人） */
    memberOptions?: ActionOption[]
    /** 退回节点下拉选项 */
    nodeOptions?: ActionOption[]
    /** 人工路由选项（PASS 且下一节点为人工路由/人工并行时非空） */
    manualRouteOptions?: ActionOption[]
    /** 人工路由是否多选（人工并行 true） */
    manualRouteMultiple?: boolean
    /** 提交中：禁用按钮并锁定弹窗 */
    loading?: boolean
  }>(),
  { actions: undefined, memberOptions: () => [], nodeOptions: () => [], manualRouteOptions: () => [], manualRouteMultiple: false, loading: false },
)

const emit = defineEmits<{ (e: 'submit', payload: ApprovalActionPayload): void }>()

const visible = defineModel<boolean>('visible', { default: false })

const activeAction = ref<ApprovalActionKey | null>(null)

const buttons = computed(() => (props.actions ?? ACTION_ORDER).map((key) => ACTION_META[key]))

function openDialog(key: ApprovalActionKey): void {
  if (props.loading) {
    return
  }
  activeAction.value = key
  visible.value = true
}

/** 关闭弹窗（父组件提交成功后调用） */
function close(): void {
  visible.value = false
  activeAction.value = null
}

defineExpose({ close })
</script>

<template>
  <div class="approval-bar">
    <el-button
      v-for="item in buttons"
      :key="item.key"
      :plain="item.skin === 'plain'"
      :text="item.skin === 'text'"
      :type="item.type"
      :disabled="loading"
      class="approval-bar-btn"
      @click="openDialog(item.key)"
    >
      <el-icon class="approval-bar-icon"><component :is="item.icon" /></el-icon>
      <span>{{ item.label }}</span>
    </el-button>
  </div>

  <ApprovalActionDialog
    v-model="visible"
    :action="activeAction"
    :member-options="memberOptions"
    :node-options="nodeOptions"
    :manual-route-options="manualRouteOptions"
    :manual-route-multiple="manualRouteMultiple"
    :loading="loading"
    @submit="emit('submit', $event)"
  />
</template>

<style scoped>
.approval-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
  padding: 14px 16px;
  background-color: #ffffff;
  border-radius: 8px;
}

.approval-bar-btn {
  height: 36px;
  padding: 0 16px;
  font-size: 15px;
  border-radius: 6px;
}

.approval-bar-icon {
  margin-right: 6px;
  font-size: 16px;
}

/* 通过：浅绿底 + 绿字；悬停反色 */
.approval-bar :deep(.el-button--success.is-plain) {
  color: #52c41a;
  background-color: #f6ffed;
  border-color: #b7eb8f;
}

.approval-bar :deep(.el-button--success.is-plain:hover) {
  color: #ffffff;
  background-color: #52c41a;
  border-color: #52c41a;
}

/* 拒绝：浅红底 + 红字 */
.approval-bar :deep(.el-button--danger.is-plain) {
  color: #f5222d;
  background-color: #fff1f0;
  border-color: #ffccc7;
}

.approval-bar :deep(.el-button--danger.is-plain:hover) {
  color: #ffffff;
  background-color: #f5222d;
  border-color: #f5222d;
}

/* 评论：浅蓝底 + 蓝字 */
.approval-bar :deep(.el-button--primary.is-plain) {
  color: #409eff;
  background-color: #ecf5ff;
  border-color: #b3d8ff;
}

.approval-bar :deep(.el-button--primary.is-plain:hover) {
  color: #ffffff;
  background-color: #409eff;
  border-color: #409eff;
}

/* 抄送/转办/委派/加签/退回/取消：无边框文字按钮 */
.approval-bar :deep(.el-button.is-text) {
  padding: 0 8px;
  font-weight: 500;
  color: #303133;
}

.approval-bar :deep(.el-button.is-text:hover) {
  color: #409eff;
  background-color: transparent;
}
</style>
