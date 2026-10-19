<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ACTION_META, type ActionField, type ActionOption, type ActionSubmit } from './approval-actions'
import type { AddSignDirection, ApprovalActionKey, ApprovalActionPayload } from '@/types/workflow'
import type { PendingSignTaskVO } from '@/types/approval'

const props = withDefaults(
  defineProps<{
    /** 当前打开的操作；null 表示未选择 */
    action: ApprovalActionKey | null
    /** 人员下拉选项（抄送人 / 新审批人 / 接收人 / 加签处理人） */
    memberOptions?: ActionOption[]
    /** 退回节点下拉选项 */
    nodeOptions?: ActionOption[]
    /** 人工路由选项（PASS 且下一节点为人工路由/人工并行时非空；选中值回传） */
    manualRouteOptions?: ActionOption[]
    /** 人工路由是否多选（下一节点为人工并行时 true：多选 routeTargetIds；条件路由单选 routeTargetId） */
    manualRouteMultiple?: boolean
    /** 待办理加签任务（PASS 时非空则提示加签人未同意，可删除减签） */
    pendingSignTasks?: PendingSignTaskVO[]
    /** 提交中：锁定表单与按钮 */
    loading?: boolean
  }>(),
  { memberOptions: () => [], nodeOptions: () => [], manualRouteOptions: () => [], manualRouteMultiple: false, pendingSignTasks: () => [], loading: false },
)

const emit = defineEmits<{ (e: 'submit', payload: ApprovalActionPayload): void }>()

const visible = defineModel<boolean>({ required: true })

type FormValue = string | string[]
type ActionForm = Record<string, FormValue>

const formRef = ref<FormInstance>()
const form = reactive<ActionForm>({})

/** 人工路由指定审批人（branchId → userId）：仅对所选分支中有候选审批人的分支生效，可选 */
const routeAssignees = reactive<Record<string, string>>({})

const meta = computed(() => (props.action ? ACTION_META[props.action] : null))

const rules = computed<FormRules>(() => {
  const result: FormRules = {}
  for (const field of meta.value?.fields ?? []) {
    if (!field.required) {
      continue
    }
    result[field.key] = [
      {
        // 多选下拉的值是数组，须显式声明 type，否则空数组不会被判为未填
        type: field.multiple ? 'array' : 'string',
        required: true,
        whitespace: field.type === 'textarea',
        message: `${field.type === 'select' ? '请选择' : '请输入'}${field.label}`,
        trigger: field.type === 'select' ? 'change' : 'blur',
      },
    ]
  }
  if (props.action === 'PASS' && props.manualRouteOptions.length > 0) {
    if (props.manualRouteMultiple) {
      // 人工并行：多选分支，值为数组，须显式声明 type 否则空数组不会被判为未填
      result.routeTargetIds = [{ type: 'array', required: true, message: '请选择要执行的分支', trigger: 'change' }]
    } else {
      result.routeTargetId = [{ required: true, message: '请选择下一步', trigger: 'change' }]
    }
  }
  return result
})

watch(
  () => [visible.value, props.action] as const,
  ([open]) => {
    if (open) {
      resetForm()
    }
  },
  { immediate: true },
)

/** 已选分支中带候选审批人的分支：为每个这样的分支渲染一个可选的「审批人」下拉 */
const assigneeBranches = computed(() => {
  if (props.action !== 'PASS' || props.manualRouteOptions.length === 0) {
    return []
  }
  const selected = props.manualRouteMultiple
    ? (Array.isArray(form.routeTargetIds) ? [...(form.routeTargetIds as string[])] : [])
    : typeof form.routeTargetId === 'string' && form.routeTargetId ? [form.routeTargetId] : []
  return props.manualRouteOptions.filter(
    (opt) => selected.includes(opt.id) && (opt.children?.length ?? 0) > 0,
  )
})

function setRouteAssignee(branchId: string, value: unknown): void {
  routeAssignees[branchId] = typeof value === 'string' ? value : ''
}

/** 加签方向文案（与任务标签口径一致） */
function signTypeLabel(signType: string): string {
  return signType === 'BEFORE_SIGN' ? '向前加签' : '向后加签'
}

/** 删除未办理的加签任务（减签）：直接上抛载荷，父组件提交后刷新详情，弹窗保持打开 */
function onReduceSign(taskId: string): void {
  if (props.loading) {
    return
  }
  emit('submit', { action: 'REDUCE_SIGN', targetTaskId: taskId, comment: '' })
}

/** 每个操作的下拉数据源：退回节点取 nodeOptions，其余取人员 */
function optionsOf(field: ActionField): ActionOption[] {
  return field.key === 'targetNodeId' ? props.nodeOptions : props.memberOptions
}

/** 文本域绑定桥：表单值可能是数组，此处统一按字符串读写 */
function textValue(key: string): string {
  return typeof form[key] === 'string' ? (form[key] as string) : ''
}

function onSelect(key: string, value: unknown): void {
  // Element Plus 的 el-select 值类型宽泛，字段定义已限定为 string / string[]
  form[key] = value as FormValue
}

async function onSubmit(submit: ActionSubmit): Promise<void> {
  const current = meta.value
  if (!current || props.loading) {
    return
  }
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  emit('submit', buildPayload(current.key, submit.direction))
}

/** 按操作类型组装带判别字段的载荷 */
function buildPayload(action: ApprovalActionKey, direction?: AddSignDirection): ApprovalActionPayload {
  const text = (key: string): string => textValue(key).trim()
  const list = (key: string): string[] => (Array.isArray(form[key]) ? [...(form[key] as string[])] : [])
  switch (action) {
    case 'COPY':
      return { action: 'COPY', receiverIds: list('receiverIds'), comment: text('comment') }
    case 'TRANSFER':
      return { action: 'TRANSFER', receiverId: text('receiverId'), comment: text('comment') }
    case 'DELEGATE':
      return { action: 'DELEGATE', receiverId: text('receiverId'), comment: text('comment') }
    case 'ADD_SIGN':
      return {
        action: 'ADD_SIGN',
        receiverId: text('receiverId'),
        comment: text('comment'),
        direction: direction ?? 'AFTER',
      }
    case 'RETURN':
      return { action: 'RETURN', targetNodeId: text('targetNodeId'), comment: text('comment') }
    case 'PASS': {
      const payload: {
        action: 'PASS'
        comment: string
        routeTargetId?: string
        routeTargetIds?: string[]
        routeAssignees?: Record<string, string>
      } = {
        action: 'PASS',
        comment: text('comment'),
      }
      if (props.manualRouteMultiple) {
        // 人工并行：多选分支 id 集合
        const ids = list('routeTargetIds')
        payload.routeTargetIds = ids.length > 0 ? ids : undefined
      } else {
        payload.routeTargetId = text('routeTargetId') || undefined
      }
      // 所选分支指定审批人（仅收集当前所选分支的非空值，未选分支的残留不提交）
      const assignees: Record<string, string> = {}
      for (const branch of assigneeBranches.value) {
        const userId = (routeAssignees[branch.id] ?? '').trim()
        if (userId) {
          assignees[branch.id] = userId
        }
      }
      payload.routeAssignees = Object.keys(assignees).length > 0 ? assignees : undefined
      return payload
    }
    default:
      return { action, comment: text('comment') }
  }
}

/** 每次打开重置表单，避免上一次输入残留 */
function resetForm(): void {
  for (const key of Object.keys(form)) {
    delete form[key]
  }
  for (const key of Object.keys(routeAssignees)) {
    delete routeAssignees[key]
  }
  for (const field of meta.value?.fields ?? []) {
    form[field.key] = field.type === 'select' && field.multiple ? [] : ''
  }
  form.routeTargetId = ''
  form.routeTargetIds = []
  formRef.value?.clearValidate()
}

/** 提交中禁止关闭弹框 */
function beforeClose(done: () => void): void {
  if (!props.loading) {
    done()
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="520px"
    align-center
    :show-close="false"
    :close-on-click-modal="!loading"
    :close-on-press-escape="!loading"
    :before-close="beforeClose"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      class="approval-form"
      @submit.prevent
    >
      <div v-if="meta?.key === 'PASS' && pendingSignTasks.length > 0" class="pending-sign-tip">
        <div class="pending-sign-title">以下加签人还没有同意，可删除其加签任务（减签）后再继续</div>
        <div v-for="sign in pendingSignTasks" :key="sign.taskId" class="pending-sign-row">
          <span class="pending-sign-name">{{ sign.assigneeName }}</span>
          <el-tag size="small" effect="plain">{{ signTypeLabel(sign.signType) }}</el-tag>
          <el-button type="danger" text size="small" :disabled="loading" @click="onReduceSign(sign.taskId)">
            删除
          </el-button>
        </div>
      </div>

      <el-form-item
        v-if="meta?.key === 'PASS' && manualRouteOptions.length > 0"
        :label="manualRouteMultiple ? '执行分支' : '下一步'"
        :prop="manualRouteMultiple ? 'routeTargetIds' : 'routeTargetId'"
      >
        <el-select
          :model-value="manualRouteMultiple ? form.routeTargetIds : form.routeTargetId"
          :multiple="manualRouteMultiple"
          :placeholder="manualRouteMultiple ? '请选择要执行的分支（可多选）' : '请选择下一步'"
          :disabled="loading"
          class="approval-control"
          @update:model-value="(value: unknown) => onSelect(manualRouteMultiple ? 'routeTargetIds' : 'routeTargetId', value)"
        >
          <el-option v-for="opt in manualRouteOptions" :key="opt.id" :label="opt.label" :value="opt.id" />
        </el-select>
      </el-form-item>

      <el-form-item
        v-for="branch in assigneeBranches"
        :key="branch.id"
        :label="`${branch.label}审批人`"
      >
        <el-select
          :model-value="routeAssignees[branch.id] ?? ''"
          placeholder="默认按节点配置审批（可选）"
          clearable
          filterable
          :disabled="loading"
          class="approval-control"
          @update:model-value="(value: unknown) => setRouteAssignee(branch.id, value)"
        >
          <el-option v-for="opt in branch.children" :key="opt.id" :label="opt.label" :value="opt.id" />
        </el-select>
      </el-form-item>

      <el-form-item v-for="field in meta?.fields ?? []" :key="field.key" :label="field.label" :prop="field.key">
        <div class="approval-field">
          <div v-if="field.tip" class="approval-field-tip">{{ field.tip }}</div>
          <el-select
            v-if="field.type === 'select'"
            :model-value="form[field.key]"
            :multiple="field.multiple"
            :placeholder="field.placeholder"
            :disabled="loading"
            filterable
            class="approval-control"
            @update:model-value="(value: unknown) => onSelect(field.key, value)"
          >
            <el-option v-for="option in optionsOf(field)" :key="option.id" :label="option.label" :value="option.id" />
          </el-select>
          <el-input
            v-else
            :model-value="textValue(field.key)"
            type="textarea"
            :rows="4"
            :placeholder="field.placeholder"
            :disabled="loading"
            class="approval-control"
            @update:model-value="(value: string) => onSelect(field.key, value)"
          />
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button :disabled="loading" @click="visible = false">取消</el-button>
      <el-button
        v-for="submit in meta?.submits ?? []"
        :key="submit.label"
        type="primary"
        :loading="loading"
        @click="onSubmit(submit)"
      >
        {{ submit.label }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.approval-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.approval-form :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

.approval-form :deep(.el-form-item__label) {
  padding-bottom: 8px;
  font-size: 14px;
  line-height: 22px;
  color: #303133;
}

.approval-field {
  width: 100%;
}

.pending-sign-tip {
  margin-bottom: 20px;
  padding: 10px 12px;
  background-color: #fdf6ec;
  border: 1px solid #faecd8;
  border-radius: 6px;
}

.pending-sign-title {
  margin-bottom: 6px;
  font-size: 13px;
  line-height: 20px;
  color: #b88230;
}

.pending-sign-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.pending-sign-name {
  font-size: 14px;
  line-height: 22px;
  color: #303133;
}

.approval-field-tip {
  margin-bottom: 8px;
  font-size: 14px;
  line-height: 22px;
  color: #909399;
}

.approval-control {
  width: 100%;
}

.approval-control :deep(.el-textarea__inner) {
  font-size: 14px;
  line-height: 22px;
}

:deep(.el-dialog__footer) {
  padding-top: 8px;
}
</style>
