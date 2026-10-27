<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import { ElMessageBox } from 'element-plus'
import ApprovalActionBar from '@/components/workflow/ApprovalActionBar.vue'
import { fetchPrincipalUsers } from '@/api/approval-runtime'
import type { PrincipalOption } from '@/composables/usePrincipalOptions'
import type { ApprovalActionKey, ApprovalActionPayload } from '@/types/workflow'
import type { ApproverButtonVO, ReturnTargetVO, TaskActionPayload } from '@/api/approval-runtime'
import type { ManualRouteOptionVO, PendingSignTaskVO, SubProcessApproverOptionVO } from '@/types/approval'

/**
 * 运行态操作栏：节点按钮快照 → ApprovalActionKey 列表（COMMENT/COPY 恒可用，与后端口径一致）；
 * ADD_SIGN_BEFORE/AFTER 合并为 ADD_SIGN（任一启用即可发起，方向在面板中选择）。
 * 撤销为实例级动作（仅发起人 + RUNNING），由父组件通过 emit('cancel') 处理。
 */
const props = defineProps<{
  buttons: ApproverButtonVO[]
  returnTargets: ReturnTargetVO[]
  manualRouteOptions: ManualRouteOptionVO[]
  manualRouteMultiple: boolean
  subProcessApproverOptions: SubProcessApproverOptionVO[]
  pendingSignTasks: PendingSignTaskVO[]
  canCancel: boolean
  loading: boolean
}>()

const emit = defineEmits<{
  (e: 'submit', payload: TaskActionPayload): void
  (e: 'cancel', comment: string): void
}>()

/** 运行态按钮 key → ApprovalActionKey */
const BUTTON_KEY_MAP: Record<string, ApprovalActionKey> = {
  PASS: 'PASS',
  REJECT: 'REJECT',
  TRANSFER: 'TRANSFER',
  DELEGATE: 'DELEGATE',
  ADD_SIGN_BEFORE: 'ADD_SIGN',
  ADD_SIGN_AFTER: 'ADD_SIGN',
  RETURN: 'RETURN',
}

/** 按钮渲染顺序 */
const ACTION_ORDER: ApprovalActionKey[] = [
  'PASS',
  'REJECT',
  'RETURN',
  'TRANSFER',
  'DELEGATE',
  'ADD_SIGN',
  'COMMENT',
  'COPY',
]

/** 节点启用按钮映射（COMMENT/COPY 恒可用）；按固定顺序输出 */
const actions = computed<ApprovalActionKey[]>(() => {
  const enabled = new Set<ApprovalActionKey>()
  for (const button of props.buttons) {
    if (button.enabled) {
      const mapped = BUTTON_KEY_MAP[button.key]
      if (mapped) {
        enabled.add(mapped)
      }
    }
  }
  enabled.add('COMMENT')
  enabled.add('COPY')
  return ACTION_ORDER.filter((key) => enabled.has(key))
})

/** 转办 / 委派 / 加签接收人选项：走运行态用户接口（登录即可，ADMIN 域 /users/page 普通审批人不可用） */
const memberOptions = shallowRef<PrincipalOption[]>([])

onMounted(async () => {
  try {
    const users = await fetchPrincipalUsers('', 50)
    memberOptions.value = users.map((user) => ({ id: user.userId, label: user.name, sub: '' }))
  } catch {
    // 失败提示由拦截器统一处理，动作弹窗内选项为空
  }
})

/** 退回目标选项（START=退回发起人重新提交） */
const nodeOptions = computed<PrincipalOption[]>(() =>
  props.returnTargets.map((target) => ({ id: target.nodeId, label: target.nodeName, sub: '' })),
)

/** 人工路由选项（当前节点通过后需人工选择下一步时非空）；children=分支列内首个审批节点候选审批人 */
const routeOptions = computed<(PrincipalOption & { children?: PrincipalOption[] })[]>(() =>
  props.manualRouteOptions.map((opt) => {
    const children = opt.assigneeOptions.map((user) => ({ id: user.userId, label: user.name, sub: '' }))
    return children.length > 0
      ? { id: opt.branchId, label: opt.label, sub: '', children }
      : { id: opt.branchId, label: opt.label, sub: '' }
  }),
)

const barRef = ref<InstanceType<typeof ApprovalActionBar>>()

defineExpose({
  /** 提交成功后重置操作面板（回默认操作并清空表单） */
  reset: () => barRef.value?.reset(),
})

function onSubmit(payload: ApprovalActionPayload): void {
  if (payload.action === 'CANCEL') {
    return
  }
  emit('submit', payload as TaskActionPayload)
}

/** 撤销实例：输入理由后回调父组件 */
async function onCancel(): Promise<void> {
  try {
    const { value } = await ElMessageBox.prompt('撤销后流程终止且不可恢复，请输入撤销理由', '撤销流程', {
      confirmButtonText: '确认撤销',
      cancelButtonText: '取消',
      inputPlaceholder: '请输入撤销理由（必填）',
      inputValidator: (input: string) => (input && input.trim().length > 0 ? true : '撤销理由不能为空'),
      type: 'warning',
    })
    emit('cancel', value.trim())
  } catch {
    // 用户取消
  }
}
</script>

<template>
  <div class="approval-status-bar">
    <ApprovalActionBar
      ref="barRef"
      :actions="actions"
      :member-options="memberOptions"
      :node-options="nodeOptions"
      :return-targets="props.returnTargets"
      :manual-route-options="routeOptions"
      :manual-route-multiple="props.manualRouteMultiple"
      :sub-process-approver-options="props.subProcessApproverOptions"
      :pending-sign-tasks="props.pendingSignTasks"
      :loading="props.loading"
      @submit="onSubmit"
    />
    <el-button
      v-if="props.canCancel"
      type="danger"
      plain
      :loading="props.loading"
      class="cancel-btn"
      @click="onCancel"
    >
      撤销流程
    </el-button>
  </div>
</template>

<style scoped>
.approval-status-bar {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 12px 16px;
  background-color: #ffffff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
}

.cancel-btn {
  flex-shrink: 0;
}
</style>
