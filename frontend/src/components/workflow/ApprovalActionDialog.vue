<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ACTION_META, type ActionField, type ActionOption, type ActionSubmit } from './approval-actions'
import type { AddSignDirection, ApprovalActionKey, ApprovalActionPayload } from '@/types/workflow'

const props = withDefaults(
  defineProps<{
    /** 当前打开的操作；null 表示未选择 */
    action: ApprovalActionKey | null
    /** 人员下拉选项（抄送人 / 新审批人 / 接收人 / 加签处理人） */
    memberOptions?: ActionOption[]
    /** 退回节点下拉选项 */
    nodeOptions?: ActionOption[]
    /** 提交中：锁定表单与按钮 */
    loading?: boolean
  }>(),
  { memberOptions: () => [], nodeOptions: () => [], loading: false },
)

const emit = defineEmits<{ (e: 'submit', payload: ApprovalActionPayload): void }>()

const visible = defineModel<boolean>({ required: true })

type FormValue = string | string[]
type ActionForm = Record<string, FormValue>

const formRef = ref<FormInstance>()
const form = reactive<ActionForm>({})

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
    default:
      return { action, comment: text('comment') }
  }
}

/** 每次打开重置表单，避免上一次输入残留 */
function resetForm(): void {
  for (const key of Object.keys(form)) {
    delete form[key]
  }
  for (const field of meta.value?.fields ?? []) {
    form[field.key] = field.type === 'select' && field.multiple ? [] : ''
  }
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
      <el-form-item v-for="field in meta?.fields ?? []" :key="field.key" :label="field.label" :prop="field.key">
        <div class="approval-field">
          <div v-if="field.tip" class="approval-field-tip">{{ field.tip }}</div>
          <el-select
            v-if="field.type === 'select'"
            :model-value="form[field.key]"
            :multiple="field.multiple"
            :placeholder="field.placeholder"
            :disabled="loading"
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
