<script setup lang="ts">
import { computed, reactive, ref, shallowRef, watch } from 'vue'
import RuntimeUserSelect from '@/views/approval/components/RuntimeUserSelect.vue'
import { fetchNextApprovers, fetchStartableProcesses, submitInstanceStart } from '@/api/approval-runtime'
import { submitLawFirmSelectCreate } from '@/api/lawfirmselect'
import type { LawFirmSelectSaveDTO } from '@/types/lawfirmselect'
import type { NextApproverVO, NodeApproverPreviewVO, StartableProcessVO } from '@/types/approval'

/**
 * 新增单据"提交审批"抽屉：按表单值预览下一阶段审批人（conditions 真实求值条件分支），
 * 确认后创建单据并发起流程（V1 bizType=LAW_FIRM_SELECT）。
 * 与列表页 StartApprovalDialog（已有 bizId 的单据发起）互补，覆盖"边填边提交"场景。
 */
const props = defineProps<{
  /** 业务单据类型（V1 仅 LAW_FIRM_SELECT） */
  bizType: string
  /** 新建单据表单值快照（创建单据入参与条件分支求值共用） */
  formData: LawFirmSelectSaveDTO
  /** 单据标题（抽屉回显） */
  bizTitle?: string
}>()

const emit = defineEmits<{ (e: 'submitted', instanceId: string): void }>()

const visible = defineModel<boolean>({ default: false })

const form = reactive({
  processId: '',
  ccUserIds: [] as string[],
  comment: '',
})

const loading = ref(false)
const submitting = ref(false)
const processes = shallowRef<StartableProcessVO[]>([])
const preview = shallowRef<NextApproverVO | null>(null)
const previewLoading = ref(false)
/** 发起后第一审批阶段节点（作为预览起点选项快照，切换起点不丢失） */
const firstNodes = shallowRef<NodeApproverPreviewVO[]>([])
/** 预览起点：'' = 发起后第一审批阶段；其余 = 以该 nodeId 为起点的下一阶段 */
const fromNodeId = ref('')
/** 或签节点选中的审批人（下拉单选，键 nodeId，值 userId） */
const orPickedMap = reactive<Record<string, string>>({})
/** 会签节点选中的审批人（下拉多选，键 nodeId，值 userId 列表；切换起点保留各自选择） */
const andPickedMap = reactive<Record<string, string[]>>({})

const selectedProcess = computed(
  () => processes.value.find((item) => item.id === form.processId) ?? null,
)

/** 条件分支求值入参：键与 form_snapshot 字段一致（title/amount/reason，值转字符串） */
const conditions = computed<Record<string, string>>(() => ({
  title: props.formData.title,
  amount: String(props.formData.amount),
  reason: props.formData.reason,
}))

/** 抽屉打开时加载可发起流程；唯一流程默认选中并预览审批人 */
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
  firstNodes.value = []
  fromNodeId.value = ''
  clearChecked()
}

/** 清空全部选择（流程切换 / 抽屉重置时） */
function clearChecked(): void {
  for (const key of Object.keys(orPickedMap)) {
    delete orPickedMap[key]
  }
  for (const key of Object.keys(andPickedMap)) {
    delete andPickedMap[key]
  }
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

/** 按表单值求值条件分支，预览下一阶段审批人；fromNodeId 为空查发起后首阶段，否则查该节点的下一阶段 */
async function loadPreview(): Promise<void> {
  if (!form.processId) {
    preview.value = null
    firstNodes.value = []
    return
  }
  previewLoading.value = true
  try {
    const result = await fetchNextApprovers({
      processId: form.processId,
      nodeId: fromNodeId.value || undefined,
      conditions: conditions.value,
    })
    preview.value = result
    if (!fromNodeId.value) {
      firstNodes.value = result.nodes
    }
    initChecked(result.nodes)
  } catch {
    preview.value = null
  } finally {
    previewLoading.value = false
  }
}

/** 新节点选择初始化（已有选择不覆盖）：或签单选存 string，会签多选存 string[] */
function initChecked(nodes: NodeApproverPreviewVO[]): void {
  for (const node of nodes) {
    if (node.multiMode === 'AND') {
      andPickedMap[node.nodeId] ??= []
    } else {
      orPickedMap[node.nodeId] ??= ''
    }
  }
}

function onProcessChange(): void {
  fromNodeId.value = ''
  clearChecked()
  void loadPreview()
}

/** 切换预览起点节点，带 nodeId 重查该节点的下一阶段审批人 */
function onFromNodeChange(): void {
  void loadPreview()
}

/** 确认提交：先创建单据拿 bizId，再发起审批流程 */
async function onSubmit(): Promise<void> {
  const process = selectedProcess.value
  if (!process || submitting.value) {
    return
  }
  submitting.value = true
  try {
    const bizId = await submitLawFirmSelectCreate(props.formData)
    const instanceId = await submitInstanceStart({
      processCode: process.code,
      bizType: props.bizType,
      bizId,
      ccUserIds: form.ccUserIds.length > 0 ? form.ccUserIds : undefined,
      comment: form.comment.trim() || undefined,
    })
    ElMessage.success('已创建单据并提交审批')
    visible.value = false
    emit('submitted', instanceId)
  } catch {
    // 失败提示由拦截器统一处理（create 成功而 start 失败时单据已存在，可回列表重新发起）
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-drawer
    v-model="visible"
    title="提交审批"
    size="480px"
    :close-on-click-modal="!submitting"
    :close-on-press-escape="!submitting"
  >
    <div v-if="props.bizTitle" class="drawer-biz-title">{{ props.bizTitle }}</div>

    <el-form v-loading="loading" :model="form" label-position="top" @submit.prevent>
      <el-form-item label="审批流程" required>
        <el-select
          v-model="form.processId"
          placeholder="请选择审批流程"
          :disabled="submitting"
          class="drawer-control"
          @change="onProcessChange"
        >
          <el-option
            v-for="item in processes"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
        <div v-if="selectedProcess?.description" class="drawer-field-tip">
          {{ selectedProcess.description }}
        </div>
      </el-form-item>

      <el-form-item label="抄送人（可选）">
        <RuntimeUserSelect
          v-model="form.ccUserIds"
          class="drawer-control"
          :disabled="submitting"
          placeholder="搜索并选择抄送人"
        />
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

    <div v-if="form.processId" v-loading="previewLoading" class="drawer-preview">
      <template v-if="preview">
        <template v-if="preview.ended">
          <div class="drawer-preview-title">
            {{ fromNodeId ? '该节点之后流程将直接结束（无审批节点）' : '提交后流程将直接结束（无审批节点）' }}
          </div>
        </template>
        <template v-else>
          <div class="drawer-preview-from">
            <span class="drawer-preview-from-label">预览起点</span>
            <el-radio-group v-model="fromNodeId" size="small" :disabled="submitting" @change="onFromNodeChange">
              <el-radio-button value="">发起后</el-radio-button>
              <el-radio-button v-for="node in firstNodes" :key="node.nodeId" :value="node.nodeId">
                {{ node.nodeName }}
              </el-radio-button>
            </el-radio-group>
          </div>
          <div class="drawer-preview-title">
            {{ fromNodeId ? '该节点的下一阶段审批人（可搜索选择）' : '提交后第一审批阶段审批人（可搜索选择）' }}
          </div>
          <div v-for="node in preview.nodes" :key="node.nodeId" class="drawer-preview-node">
            <div class="drawer-preview-node-head">
              <span class="drawer-preview-name">{{ node.nodeName }}</span>
              <span class="drawer-preview-mode">{{ node.multiMode === 'AND' ? '会签' : '或签' }}</span>
            </div>
            <el-select
              v-if="node.approvers.length > 0 && node.multiMode === 'AND'"
              v-model="andPickedMap[node.nodeId]"
              multiple
              filterable
              clearable
              collapse-tags
              collapse-tags-tooltip
              placeholder="搜索并选择审批人（多选）"
              :disabled="submitting"
              class="drawer-preview-select"
            >
              <el-option v-for="user in node.approvers" :key="user.userId" :label="user.name" :value="user.userId" />
            </el-select>
            <el-select
              v-else-if="node.approvers.length > 0"
              v-model="orPickedMap[node.nodeId]"
              filterable
              clearable
              placeholder="搜索并选择审批人"
              :disabled="submitting"
              class="drawer-preview-select"
            >
              <el-option v-for="user in node.approvers" :key="user.userId" :label="user.name" :value="user.userId" />
            </el-select>
            <div v-else class="drawer-preview-empty">（空审批人将自动处理）</div>
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
        确认提交
      </el-button>
    </template>
  </el-drawer>
</template>

<style scoped>
.drawer-biz-title {
  margin-bottom: 12px;
  padding: 8px 12px;
  font-size: 14px;
  line-height: 22px;
  color: #303133;
  background-color: #f7f8fa;
  border-radius: 4px;
}

.drawer-control {
  width: 100%;
}

.drawer-field-tip {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  line-height: 20px;
  color: #909399;
}

.drawer-preview {
  padding: 10px 12px;
  background-color: #f7f9fc;
  border-radius: 4px;
}

.drawer-preview-title {
  font-size: 13px;
  line-height: 22px;
  color: #606266;
}

.drawer-preview-from {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.drawer-preview-from-label {
  flex-shrink: 0;
  font-size: 12px;
  color: #909399;
}

.drawer-preview-node {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed #e4e7ed;
}

.drawer-preview-node-head {
  display: flex;
  gap: 8px;
  align-items: baseline;
  font-size: 13px;
  line-height: 22px;
}

.drawer-preview-name {
  font-weight: 600;
  color: #303133;
}

.drawer-preview-mode {
  flex-shrink: 0;
  padding: 0 6px;
  font-size: 12px;
  line-height: 18px;
  color: #409eff;
  background-color: #ecf5ff;
  border-radius: 9px;
}

.drawer-preview-select {
  width: 100%;
  margin-top: 2px;
}

.drawer-preview-empty {
  margin-top: 2px;
  font-size: 12px;
  line-height: 20px;
  color: #909399;
}
</style>
