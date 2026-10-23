<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ACTION_META, type ActionField, type ActionOption, type ActionSubmit } from './approval-actions'
import type { AddSignDirection, ApprovalActionKey, ApprovalActionPayload } from '@/types/workflow'
import type { PendingSignTaskVO, SubProcessApproverOptionVO } from '@/types/approval'

const props = withDefaults(
  defineProps<{
    /** 当前展示的操作（面板始终渲染，由父级切换） */
    action: ApprovalActionKey
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
    /** 子流程下一节点可指定审批人目标（PASS 且下一节点为子流程启动后继续时非空，分开展示主/子流程审批人） */
    subProcessApproverOptions?: SubProcessApproverOptionVO[]
    /** 提交中：锁定表单与按钮 */
    loading?: boolean
  }>(),
  { memberOptions: () => [], nodeOptions: () => [], manualRouteOptions: () => [], manualRouteMultiple: false, pendingSignTasks: () => [], subProcessApproverOptions: () => [], loading: false },
)

const emit = defineEmits<{ (e: 'submit', payload: ApprovalActionPayload): void }>()

type FormValue = string | string[]
type ActionForm = Record<string, FormValue>

const formRef = ref<FormInstance>()
const form = reactive<ActionForm>({})

/** 人工路由指定审批人（branchId → userId）：仅对所选分支中有候选审批人的分支生效，可选 */
const routeAssignees = reactive<Record<string, string>>({})

/** 子流程目标节点指定审批人（nodeId → userId）：仅对候选审批人非空的目标节点生效，可选 */
const subProcessAssignees = reactive<Record<string, string>>({})

const meta = computed(() => ACTION_META[props.action])

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

/** 切换操作时重置表单，避免上一次输入残留 */
watch(
  () => props.action,
  () => resetForm(),
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

function setSubProcessAssignee(nodeId: string, value: unknown): void {
  subProcessAssignees[nodeId] = typeof value === 'string' ? value : ''
}

/** 子流程名（PASS 面板标注用） */
const subProcessName = computed(() =>
  props.subProcessApproverOptions.find((opt) => opt.scopeType === 'SUB_PROCESS')?.scopeName ?? '',
)

/** 主/子流程目标节点展示标签：标「主流程「名」· 节点名」/「子流程「名」· 节点名」 */
function subProcessNodeLabel(opt: SubProcessApproverOptionVO): string {
  const scope = opt.scopeType === 'SUB_PROCESS' ? '子流程' : '主流程'
  const name = opt.scopeName ? `「${opt.scopeName}」` : ''
  return `${scope}${name} · ${opt.nodeName}`
}

/** 加签方向文案（与任务标签口径一致） */
function signTypeLabel(signType: string): string {
  return signType === 'BEFORE_SIGN' ? '向前加签' : '向后加签'
}

/** 删除未办理的加签任务（减签）：直接上抛载荷，父组件提交后刷新详情，面板保持展开 */
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
        subProcessAssignees?: Record<string, string>
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
      // 子流程目标节点指定审批人（仅收集非空值）
      const subAssignees: Record<string, string> = {}
      for (const opt of props.subProcessApproverOptions) {
        const userId = (subProcessAssignees[opt.nodeId] ?? '').trim()
        if (userId) {
          subAssignees[opt.nodeId] = userId
        }
      }
      payload.subProcessAssignees = Object.keys(subAssignees).length > 0 ? subAssignees : undefined
      return payload
    }
    default:
      return { action, comment: text('comment') }
  }
}

/** 重置表单（切换操作 / 提交成功后由父级调用） */
function resetForm(): void {
  for (const key of Object.keys(form)) {
    delete form[key]
  }
  for (const key of Object.keys(routeAssignees)) {
    delete routeAssignees[key]
  }
  for (const key of Object.keys(subProcessAssignees)) {
    delete subProcessAssignees[key]
  }
  for (const field of meta.value?.fields ?? []) {
    form[field.key] = field.type === 'select' && field.multiple ? [] : ''
  }
  form.routeTargetId = ''
  form.routeTargetIds = []
  formRef.value?.clearValidate()
}

defineExpose({ resetForm })
</script>

<template>
  <div class="approval-panel">
    <div class="approval-panel-header">
      <span class="approval-panel-title">{{ meta.label }}</span>
    </div>
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="left"
      label-width="96px"
      class="approval-form"
      @submit.prevent
    >
      <div v-if="meta.key === 'PASS' && pendingSignTasks.length > 0" class="pending-sign-tip">
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
        v-if="meta.key === 'PASS' && manualRouteOptions.length > 0"
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

      <div v-if="meta.key === 'PASS' && subProcessApproverOptions.length > 0" class="sub-process-assignees">
        <div class="sub-process-assignees-title">
          下一节点为子流程「{{ subProcessName }}」启动后继续，请设置主流程审批与子流程审批
        </div>
        <div v-for="opt in subProcessApproverOptions" :key="opt.nodeId" class="sub-process-assignee">
          <div class="sub-process-assignee-label">{{ subProcessNodeLabel(opt) }}</div>
          <el-select
            v-if="opt.assigneeOptions.length > 0"
            :model-value="subProcessAssignees[opt.nodeId] ?? ''"
            placeholder="默认按节点配置审批（可选）"
            clearable
            filterable
            :disabled="loading"
            class="approval-control"
            @update:model-value="(value: unknown) => setSubProcessAssignee(opt.nodeId, value)"
          >
            <el-option v-for="user in opt.assigneeOptions" :key="user.userId" :label="user.name" :value="user.userId" />
          </el-select>
          <div v-else class="sub-process-assignee-empty">（空审批人将自动处理）</div>
        </div>
      </div>

      <el-form-item v-for="field in meta.fields" :key="field.key" :label="field.label" :prop="field.key">
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

    <div class="approval-panel-footer">
      <el-button
        v-for="submit in meta.submits"
        :key="submit.label"
        type="primary"
        :loading="loading"
        @click="onSubmit(submit)"
      >
        {{ submit.label }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.approval-panel {
  padding: 14px 16px 16px;
  margin-top: 12px;
  background-color: #fafbfc;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
}

.approval-panel-header {
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
}

.approval-panel-title {
  font-size: 15px;
  font-weight: 600;
  line-height: 22px;
  color: #303133;
}

.approval-panel-footer {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  padding-top: 4px;
}

.approval-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.approval-form :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

.approval-field {
  width: 100%;
}

.pending-sign-tip {
  margin-bottom: 16px;
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

.sub-process-assignees {
  margin-bottom: 16px;
  padding: 10px 12px;
  background-color: #ffffff;
  border-radius: 6px;
}

.sub-process-assignees-title {
  margin-bottom: 8px;
  font-size: 13px;
  line-height: 22px;
  color: #606266;
}

.sub-process-assignee {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed #e4e7ed;
}

.sub-process-assignee-label {
  font-size: 13px;
  font-weight: 600;
  line-height: 22px;
  color: #303133;
}

.sub-process-assignee-empty {
  margin-top: 2px;
  font-size: 12px;
  line-height: 20px;
  color: #909399;
}

.approval-control :deep(.el-textarea__inner) {
  font-size: 14px;
  line-height: 22px;
}
</style>
