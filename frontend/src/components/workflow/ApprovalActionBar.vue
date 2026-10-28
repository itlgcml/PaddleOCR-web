<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import ApprovalActionPanel from './ApprovalActionPanel.vue'
import { ACTION_META, ACTION_ORDER, type ActionOption } from './approval-actions'
import type { ApprovalActionKey, ApprovalActionPayload } from '@/types/workflow'
import type { NextApproverOptionVO, PendingSignTaskVO, ReturnTargetVO, SubProcessApproverOptionVO } from '@/types/approval'

const props = withDefaults(
  defineProps<{
    /** 展示的按钮；缺省为全部，顺序固定为设计稿顺序 */
    actions?: ApprovalActionKey[]
    /** 人员下拉选项（抄送人 / 新审批人 / 接收人 / 加签处理人） */
    memberOptions?: ActionOption[]
    /** 退回节点下拉选项 */
    nodeOptions?: ActionOption[]
    /** 退回目标完整选项（RETURN 时非空：目标节点候选审批人 + 多方模式，退回须指定审批人） */
    returnTargets?: ReturnTargetVO[]
    /** 人工路由选项（PASS 且下一节点为人工路由/人工并行时非空） */
    manualRouteOptions?: ActionOption[]
    /** 人工路由是否多选（人工并行 true） */
    manualRouteMultiple?: boolean
    /** 待办理加签任务（同意面板提示加签人未审并提供删除减签） */
    pendingSignTasks?: PendingSignTaskVO[]
    /** 子流程下一节点可指定审批人目标（PASS 且下一节点为子流程启动后继续时非空） */
    subProcessApproverOptions?: SubProcessApproverOptionVO[]
    /** 下一节点（普通审批节点）可指定审批人目标（PASS 且链后继为普通审批节点时非空，单元素） */
    nextApproverOptions?: NextApproverOptionVO[]
    /** 提交中：禁用按钮并锁定面板 */
    loading?: boolean
  }>(),
  { actions: undefined, memberOptions: () => [], nodeOptions: () => [], returnTargets: () => [], manualRouteOptions: () => [], manualRouteMultiple: false, pendingSignTasks: () => [], subProcessApproverOptions: () => [], nextApproverOptions: () => [], loading: false },
)

const emit = defineEmits<{ (e: 'submit', payload: ApprovalActionPayload): void }>()

/** 当前展开的操作；始终有一个在展示，不可收起 */
const activeAction = ref<ApprovalActionKey | null>(null)

// 始终默认展开后端返回按钮集的第一个（PASS 在首位时即默认「通过」表单）；
// 前端不缓存用户选择：按钮集每次变化（任务详情返回、提交成功刷新等）都重置回首个。
watch(
  () => props.actions,
  (list) => {
    activeAction.value = (list ?? ACTION_ORDER)[0] ?? null
  },
  { immediate: true },
)

const buttons = computed(() => (props.actions ?? ACTION_ORDER).map((key) => ACTION_META[key]))

/** 切换展开的操作；点当前已选中按钮保持展开不收起 */
function selectAction(key: ApprovalActionKey): void {
  if (props.loading || key === activeAction.value) {
    return
  }
  activeAction.value = key
}

const panelRef = ref<InstanceType<typeof ApprovalActionPanel>>()

/** 提交成功后重置：回到默认操作并清空表单 */
function reset(): void {
  activeAction.value = (props.actions ?? ACTION_ORDER)[0] ?? null
  panelRef.value?.resetForm()
}

defineExpose({ reset })
</script>

<template>
  <div class="approval-actions">
    <div class="approval-bar">
      <el-button
        v-for="item in buttons"
        :key="item.key"
        :plain="item.key === activeAction"
        :text="item.key !== activeAction"
        :type="item.key === activeAction ? 'success' : undefined"
        :disabled="loading"
        :class="{ 'is-selected': item.key === activeAction }"
        class="approval-bar-btn"
        @click="selectAction(item.key)"
      >
        <el-icon class="approval-bar-icon"><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
      </el-button>
    </div>

    <template v-if="activeAction">
      <ApprovalActionPanel
        ref="panelRef"
        :action="activeAction"
        :member-options="memberOptions"
        :node-options="nodeOptions"
        :return-targets="returnTargets"
        :manual-route-options="manualRouteOptions"
        :manual-route-multiple="manualRouteMultiple"
        :sub-process-approver-options="subProcessApproverOptions"
        :next-approver-options="nextApproverOptions"
        :pending-sign-tasks="pendingSignTasks"
        :loading="loading"
        @submit="emit('submit', $event)"
      />
    </template>
  </div>
</template>

<style scoped>
.approval-actions {
  width: 100%;
}

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

/* 选中按钮：浅绿底 + 绿字；悬停反色 */
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

/* 全部操作按钮（未选中）：无边框文字按钮 */
.approval-bar :deep(.el-button.is-text) {
  padding: 0 8px;
  font-weight: 500;
  color: #303133;
}

.approval-bar :deep(.el-button.is-text:hover) {
  color: #409eff;
  background-color: transparent;
}

/* 选中按钮右上角「当前选择」角标 */
.approval-bar-btn.is-selected {
  position: relative;
}

.approval-bar-btn.is-selected::after {
  content: '当前选择';
  position: absolute;
  top: -9px;
  right: -10px;
  z-index: 1;
  padding: 0 7px;
  font-size: 11px;
  font-weight: 400;
  line-height: 17px;
  color: #ffffff;
  pointer-events: none;
  background-color: #52c41a;
  border-radius: 9px;
}
</style>
