<script setup lang="ts">
import { computed, reactive, ref, shallowRef, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import RuntimeUserSelect from './RuntimeUserSelect.vue'
import {
  fetchNextApprovers,
  fetchStartableProcesses,
  submitInstanceStart,
} from '@/api/approval-runtime'
import type { NextApproverVO, StartableProcessVO } from '@/types/approval'

/**
 * 发起审批弹窗：可发起流程下拉（唯一流程默认选中）→ 抄送人（USER 多选）→ 备注；
 * 选定流程后调 fetchNextApprovers（不传 nodeId）只读预览"提交后将由以下人员审批"。
 */
const props = defineProps<{
  /** 业务单据类型（V1 仅 LAW_FIRM_SELECT） */
  bizType: string
  /** 业务单据 ID */
  bizId: string
  /** 单据标题（弹窗回显） */
  bizTitle?: string
}>()

const emit = defineEmits<{ (e: 'started', instanceId: string): void }>()

const visible = defineModel<boolean>({ default: false })

const formRef = ref<FormInstance>()
const form = reactive({
  processId: '',
  ccUserIds: [] as string[],
  comment: '',
})

const rules: FormRules = {
  processId: [{ required: true, message: '请选择流程', trigger: 'change' }],
}

const loading = ref(false)
const submitting = ref(false)
const processes = shallowRef<StartableProcessVO[]>([])
const preview = shallowRef<NextApproverVO | null>(null)
const previewLoading = ref(false)

const selectedProcess = computed(
  () => processes.value.find((item) => item.id === form.processId) ?? null,
)

/** 弹窗打开时加载可发起流程；唯一流程默认选中 */
watch(visible, (open) => {
  if (!open) {
    return
  }
  resetForm()
  void loadProcesses()
})

function resetForm(): void {
  form.processId = ''
  form.ccUserIds = []
  form.comment = ''
  preview.value = null
  formRef.value?.clearValidate()
}

async function loadProcesses(): Promise<void> {
  loading.value = true
  try {
    const list = await fetchStartableProcesses()
    processes.value = list
    if (list.length === 1) {
      form.processId = list[0].id
      await loadPreview()
    }
  } catch {
    // 失败提示由拦截器统一处理
  } finally {
    loading.value = false
  }
}

/** 选定流程后预览首个审批节点审批人（不传 nodeId，按默认列模拟） */
async function loadPreview(): Promise<void> {
  if (!form.processId) {
    preview.value = null
    return
  }
  previewLoading.value = true
  try {
    preview.value = await fetchNextApprovers({ processId: form.processId })
  } catch {
    preview.value = null
  } finally {
    previewLoading.value = false
  }
}

function onProcessChange(): void {
  void loadPreview()
}

async function onSubmit(): Promise<void> {
  const process = selectedProcess.value
  if (!process || submitting.value) {
    return
  }
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    const instanceId = await submitInstanceStart({
      processCode: process.code,
      bizType: props.bizType,
      bizId: props.bizId,
      ccUserIds: form.ccUserIds.length > 0 ? form.ccUserIds : undefined,
      comment: form.comment.trim() || undefined,
    })
    ElMessage.success('已提交审批')
    visible.value = false
    emit('started', instanceId)
  } catch {
    // 失败提示由拦截器统一处理
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="提交审批"
    width="560px"
    align-center
    :close-on-click-modal="!submitting"
    :close-on-press-escape="!submitting"
  >
    <div v-if="props.bizTitle" class="start-biz-title">{{ props.bizTitle }}</div>

    <el-form
      ref="formRef"
      v-loading="loading"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent
    >
      <el-form-item label="审批流程" prop="processId">
        <el-select
          v-model="form.processId"
          placeholder="请选择审批流程"
          :disabled="submitting"
          class="start-control"
          @change="onProcessChange"
        >
          <el-option
            v-for="item in processes"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
        <div v-if="selectedProcess?.description" class="start-field-tip">
          {{ selectedProcess.description }}
        </div>
      </el-form-item>

      <el-form-item label="抄送人（可选）">
        <RuntimeUserSelect v-model="form.ccUserIds" :disabled="submitting" placeholder="搜索并选择抄送人" />
      </el-form-item>

      <el-form-item label="备注（可选）">
        <el-input
          v-model="form.comment"
          type="textarea"
          :rows="3"
          maxlength="512"
          show-word-limit
          placeholder="请输入发起备注"
          :disabled="submitting"
        />
      </el-form-item>
    </el-form>

    <div v-if="form.processId" v-loading="previewLoading" class="start-preview">
      <template v-if="preview">
        <template v-if="preview.ended">
          <div class="start-preview-title">提交后流程将直接结束（无审批节点）</div>
        </template>
        <template v-else>
          <div class="start-preview-title">提交后将由以下人员审批</div>
          <div v-for="node in preview.nodes" :key="node.nodeId" class="start-preview-node">
            <span class="start-preview-name">{{ node.nodeName }}</span>
            <span class="start-preview-mode">{{ node.multiMode === 'AND' ? '会签' : '或签' }}</span>
            <span class="start-preview-approvers">
              {{ node.approvers.map((item) => item.name).join('、') || '（空审批人将自动处理）' }}
            </span>
          </div>
        </template>
      </template>
    </div>

    <template #footer>
      <el-button :disabled="submitting" @click="visible = false">取消</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="loading || processes.length === 0"
        @click="onSubmit"
      >
        提交
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.start-biz-title {
  margin-bottom: 12px;
  padding: 8px 12px;
  font-size: 14px;
  line-height: 22px;
  color: #303133;
  background-color: #f7f8fa;
  border-radius: 4px;
}

.start-control {
  width: 100%;
}

.start-field-tip {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  line-height: 20px;
  color: #909399;
}

.start-preview {
  padding: 10px 12px;
  background-color: #f7f9fc;
  border-radius: 4px;
}

.start-preview-title {
  font-size: 13px;
  line-height: 22px;
  color: #606266;
}

.start-preview-node {
  display: flex;
  gap: 8px;
  align-items: baseline;
  margin-top: 4px;
  font-size: 13px;
  line-height: 22px;
}

.start-preview-name {
  font-weight: 600;
  color: #303133;
}

.start-preview-mode {
  flex-shrink: 0;
  padding: 0 6px;
  font-size: 12px;
  line-height: 18px;
  color: #409eff;
  background-color: #ecf5ff;
  border-radius: 9px;
}

.start-preview-approvers {
  color: #606266;
  word-break: break-all;
}
</style>
