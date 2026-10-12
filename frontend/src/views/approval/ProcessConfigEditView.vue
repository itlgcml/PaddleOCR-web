<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ArrowLeft, ArrowRight, Check, Promotion } from '@element-plus/icons-vue'
import { useWorkflowStore } from '@/stores/workflow'
import { warmPrincipalLabels } from '@/composables/usePrincipalOptions'
import PrincipalSelect from '@/components/workflow/PrincipalSelect.vue'
import {
  fetchProcessDetail,
  fetchProcessDesign,
  fetchProcessVersions,
  submitProcessCreate,
  submitProcessDesign,
  submitProcessPublish,
  submitProcessUpdate,
} from '@/api/approval'
import type { ProcessStatus, ProcessVersionVO } from '@/types/approval'
import WorkflowDesignView from '@/views/system/WorkflowDesignView.vue'

const route = useRoute()
const router = useRouter()
const workflowStore = useWorkflowStore()

const activeStep = ref(0)
const isEdit = computed(() => !!route.query.id)

/** 当前流程 id（新增态首次保存后写入） */
const processId = ref<string | null>(isEdit.value ? String(route.query.id) : null)

/** 后端当前流程状态：非 DRAFT 态不可改基本信息（仍可编辑草稿并发布新版本） */
const processStatus = ref<ProcessStatus | null>(null)
const basicInfoLocked = computed(() => isEdit.value && processStatus.value !== null && processStatus.value !== 'DRAFT')

const detailLoading = ref(isEdit.value)
const saving = ref(false)
const publishing = ref(false)

/** 已发布版本列表（按发布时间倒序，编辑态按真实流程 id 加载）；新增/从未发布流程为空数组 */
const publishedVersions = ref<ProcessVersionVO[]>([])
/** 画布当前查看目标：'draft'=草稿（默认）；'v1'/'v2'=历史版本发布快照。用非空哨兵值避免 el-select 对空串的回显歧义 */
const activeVersion = ref('draft')
/** 当前已发布版本号（来自详情 currentVersion），用于在下拉中给最大版本号标「当前」 */
const currentVersion = ref<string | null>(null)
const switching = ref(false)

const formRef = ref<FormInstance | null>(null)
const form = reactive({
  code: '',
  name: '',
  description: '',
  /** 前端发起路径 */
  startPath: '/approval/leave/create',
  /** 前端详情路径 */
  detailPath: '/approval/leave/detail',
  /** 谁可以发起：全体 / 指定 */
  initiatorType: 'ALL' as 'ALL' | 'SPECIFIC',
  /** 指定人员（initiatorType 为 SPECIFIC 时生效） */
  initiatorMemberIds: [] as string[],
  /** 指定角色（initiatorType 为 SPECIFIC 时生效） */
  initiatorRoleIds: [] as string[],
})

/** 路径校验：允许留空，填写时需以 / 开头 */
function pathValidator(_rule: unknown, value: string, callback: (error?: Error) => void): void {
  if (value && !value.startsWith('/')) {
    callback(new Error('路径需以 / 开头'))
    return
  }
  callback()
}

/** 指定范围校验：选「指定」时「人员 + 角色」的并集至少选择一个 */
function initiatorRangeValidator(_rule: unknown, _value: unknown, callback: (error?: Error) => void): void {
  const empty = form.initiatorMemberIds.length === 0 && form.initiatorRoleIds.length === 0
  if (form.initiatorType === 'SPECIFIC' && empty) {
    callback(new Error('请至少选择一名人员或一个角色'))
    return
  }
  callback()
}

/** 角色变化后复检指定范围（错误提示统一挂在「指定人员」项上） */
function revalidateInitiatorRange(): void {
  void formRef.value?.validateField('initiatorMemberIds').catch(() => undefined)
}

const rules: FormRules = {
  code: [
    { required: true, message: '请输入流程编码', trigger: 'blur' },
    { pattern: /^[A-Z][A-Z0-9_]*$/, message: '需大写字母开头，仅含大写字母/数字/下划线', trigger: 'blur' },
    { max: 64, message: '流程编码不能超过 64 位', trigger: 'blur' },
  ],
  name: [
    { required: true, message: '请输入流程名称', trigger: 'blur' },
    { max: 64, message: '流程名称不能超过 64 个字符', trigger: 'blur' },
  ],
  description: [{ max: 200, message: '流程描述不能超过 200 个字符', trigger: 'blur' }],
  startPath: [
    { required: true, message: '请输入前端发起路径', trigger: 'blur' },
    { max: 200, message: '前端发起路径不能超过 200 个字符', trigger: 'blur' },
    { validator: pathValidator, trigger: 'blur' },
  ],
  detailPath: [
    { required: true, message: '请输入前端详情路径', trigger: 'blur' },
    { max: 200, message: '前端详情路径不能超过 200 个字符', trigger: 'blur' },
    { validator: pathValidator, trigger: 'blur' },
  ],
  initiatorType: [{ required: true, message: '请选择谁可以发起', trigger: 'change' }],
  initiatorMemberIds: [{ validator: initiatorRangeValidator, trigger: 'change' }],
}

/** 编辑态：加载详情与版本列表；下拉与画布同步定位——已发布流程默认展示「当前版本」快照，从未发布（第一次）默认展示草稿 */
async function loadEditData(): Promise<void> {
  try {
    const [detail, versionList] = await Promise.all([
      fetchProcessDetail(processId.value as string),
      fetchProcessVersions(processId.value as string).catch(() => []),
    ])
    processStatus.value = detail.status
    currentVersion.value = detail.currentVersion
    publishedVersions.value = versionList
    form.code = detail.code
    form.name = detail.name
    form.description = detail.description ?? ''
    form.startPath = detail.startPath
    form.detailPath = detail.detailPath
    form.initiatorType = detail.initiatorType
    form.initiatorMemberIds = [...detail.initiatorMembers]
    form.initiatorRoleIds = [...detail.initiatorRoles]

    // 下拉默认选中当前版本（无版本时选中「草稿」），画布加载同一目标的设计 JSON
    activeVersion.value = detail.currentVersion ?? 'draft'
    const design = await fetchProcessDesign(processId.value as string, detail.currentVersion ?? undefined)
    if (design.designJson) {
      workflowStore.loadDoc(design.designJson)
    } else {
      workflowStore.resetDoc()
    }
  } catch {
    // 详情/设计加载失败（拦截器已提示），退回列表避免停留在残缺向导
    void router.replace({ name: 'ApprovalProcessConfig' })
    return
  } finally {
    detailLoading.value = false
  }
}

/** 版本切换选项：草稿恒为第一项（未发布过时仅此一项，将来发布为 v1）；已发布版本倒序排列，与 currentVersion 相同的最大版本号标「当前版本」 */
const versionOptions = computed(() => [
  { label: '草稿', value: 'draft', current: false },
  ...publishedVersions.value.map((item) => ({
    label: item.version,
    value: item.version,
    current: item.version === currentVersion.value,
  })),
])

/** 切换版本视图：'draft'=草稿；历史版本=加载对应发布快照。失败时 activeVersion 保持原值（拦截器已提示） */
async function switchVersion(version: string): Promise<void> {
  if (processId.value === null || version === activeVersion.value || switching.value) {
    return
  }
  switching.value = true
  try {
    const design = await fetchProcessDesign(processId.value, version === 'draft' ? undefined : version)
    if (design.designJson) {
      workflowStore.loadDoc(design.designJson)
    } else {
      workflowStore.resetDoc()
    }
    activeVersion.value = version
  } catch {
    // radio 的 model-value 手动控制，仅成功时更新 activeVersion，失败停留在原视图
  } finally {
    switching.value = false
  }
}

function onVersionChange(value: string | number | boolean | undefined): void {
  void switchVersion(String(value ?? ''))
}

onMounted(() => {
  // 预热人员/角色名称缓存：画布与只读态按 id 回显名称（后端无批量查名接口）
  warmPrincipalLabels()
  if (isEdit.value) {
    void loadEditData()
  } else {
    workflowStore.resetDoc()
  }
})

async function handleNext(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  // 谁可以发起在基本信息里配置，同步给画布上的发起人节点展示
  workflowStore.updateStartInitiators({
    rangeType: form.initiatorType,
    memberIds: [...form.initiatorMemberIds],
    roleIds: [...form.initiatorRoleIds],
  })
  activeStep.value = 1
}

function handlePrev(): void {
  activeStep.value = 0
}

/** 基本信息 payload（不含 code） */
function buildUpdatePayload() {
  return {
    name: form.name,
    startPath: form.startPath,
    detailPath: form.detailPath,
    description: form.description,
    initiatorType: form.initiatorType,
    initiatorMembers: form.initiatorType === 'SPECIFIC' ? [...form.initiatorMemberIds] : [],
    initiatorRoles: form.initiatorType === 'SPECIFIC' ? [...form.initiatorRoleIds] : [],
  }
}

/** 确保流程存在且基本信息最新：新增则创建，DRAFT 态则更新，其余态跳过更新。不产生任何页面跳转 */
async function ensureProcess(): Promise<string> {
  if (processId.value !== null) {
    if (!basicInfoLocked.value) {
      await submitProcessUpdate(processId.value, buildUpdatePayload())
    }
    return processId.value
  }
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return Promise.reject(new Error('基本信息校验未通过'))
  }
  const newId = await submitProcessCreate({ code: form.code, ...buildUpdatePayload() })
  processId.value = newId
  return newId
}

/** 保存：静默保存基本信息（新增/更新）+ 设计草稿，不产生版本；不切步骤、不跳转，停留在当前画布 */
async function handleSave(): Promise<void> {
  saving.value = true
  try {
    const id = await ensureProcess()
    await submitProcessDesign(id, { designJson: workflowStore.exportDoc() })
    ElMessage.success('流程已保存')
  } catch {
    // 失败提示由拦截器统一处理；保存不产生任何页面动作
  } finally {
    saving.value = false
  }
}

/** 发布：先存设计草稿再发布，产生新版本快照；新增态基本信息不全时跳回第 1 步补全 */
async function handlePublish(): Promise<void> {
  publishing.value = true
  try {
    const id = await ensureProcess()
    await submitProcessDesign(id, { designJson: workflowStore.exportDoc() })
    const version = await submitProcessPublish(id)
    ElMessage.success(`流程已发布，当前版本 ${version}`)
    void router.push({ name: 'ApprovalProcessConfig' })
  } catch (error) {
    if (error instanceof Error && error.message === '基本信息校验未通过') {
      activeStep.value = 0
    }
  } finally {
    publishing.value = false
  }
}
</script>

<template>
  <div v-loading="detailLoading" class="page-card">
    <div class="wizard-header">
      <el-steps :active="activeStep" align-center finish-status="success" class="steps">
        <el-step title="基本信息" description="填写流程编码、名称、描述、路径与发起人" />
        <el-step title="流程设计" description="可视化编排审批流程" />
      </el-steps>
      <div class="header-actions">
        <el-button v-if="activeStep === 1" :icon="ArrowLeft" @click="handlePrev">上一步</el-button>
        <el-button v-if="activeStep === 0" type="primary" :icon="ArrowRight" @click="handleNext">下一步</el-button>
        <el-button v-if="activeStep === 1" type="primary" :icon="Check" :loading="saving" @click="handleSave">保存</el-button>
        <el-button v-if="activeStep === 1" type="success" :icon="Promotion" :loading="publishing" @click="handlePublish">发布</el-button>
      </div>
    </div>

    <!-- 第 1 步：基本信息（v-show 而非 v-if：第 2 步点保存/发布时仍需通过 formRef 校验表单，v-if 卸载会导致 ref 置空、校验被误判失败而不发请求） -->
    <div v-show="activeStep === 0" v-loading="detailLoading" class="step-body">
      <el-alert
        v-if="basicInfoLocked"
        title="该流程已发布或停用，基本信息不可修改；可继续编辑流程设计并发布新版本"
        type="info"
        show-icon
        :closable="false"
        class="lock-tip"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" class="form" :disabled="detailLoading">
        <el-form-item label="流程编码" prop="code">
          <el-input v-model="form.code" placeholder="如 LEAVE_APPROVAL" maxlength="64" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="流程名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入流程名称" maxlength="64" :disabled="basicInfoLocked" />
        </el-form-item>
        <el-form-item label="前端发起路径" prop="startPath">
          <el-input v-model="form.startPath" placeholder="如 /approval/leave/create" maxlength="200" :disabled="basicInfoLocked" />
        </el-form-item>
        <el-form-item label="前端详情路径" prop="detailPath">
          <el-input v-model="form.detailPath" placeholder="如 /approval/leave/detail" maxlength="200" :disabled="basicInfoLocked" />
        </el-form-item>
        <el-form-item label="谁可以发起" prop="initiatorType">
          <el-radio-group v-model="form.initiatorType" :disabled="basicInfoLocked">
            <el-radio value="ALL">全体</el-radio>
            <el-radio value="SPECIFIC">指定</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="form.initiatorType === 'SPECIFIC'">
          <el-form-item label="指定人员" prop="initiatorMemberIds">
            <PrincipalSelect v-model="form.initiatorMemberIds" type="USER" placeholder="请选择人员" :disabled="basicInfoLocked" />
          </el-form-item>
          <el-form-item label="指定角色">
            <PrincipalSelect v-model="form.initiatorRoleIds" type="ROLE" placeholder="请选择角色" :disabled="basicInfoLocked" @change="revalidateInitiatorRange" />
          </el-form-item>
        </template>
        <el-form-item label="流程描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入流程描述" maxlength="200" :disabled="basicInfoLocked" />
        </el-form-item>
      </el-form>
    </div>

    <!-- 第 2 步：流程设计（版本视图注入设计器工具栏右侧扩展位；新增/从未发布流程仅一项「v1」，编辑态按真实流程 id 拉取的版本列表渲染） -->
    <div v-show="activeStep === 1" class="step-body designer-body">
      <WorkflowDesignView class="designer">
        <template #version>
          <span class="version-label">版本视图</span>
          <el-select :model-value="activeVersion" size="small" style="width: 110px" :disabled="switching" @update:model-value="onVersionChange">
            <el-option v-for="opt in versionOptions" :key="opt.value" :label="opt.label" :value="opt.value">
              {{ opt.label }}
              <span v-if="opt.current" class="version-current">当前版本</span>
            </el-option>
          </el-select>
        </template>
      </WorkflowDesignView>
    </div>
  </div>
</template>

<style scoped>
.page-card {
  min-height: 100%;
  padding: 20px;
  background-color: #ffffff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.wizard-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.steps {
  flex: 1;
}

.header-actions {
  display: flex;
  gap: 12px;
  flex-shrink: 0;
}

.step-body {
  display: flex;
  flex-direction: column;
}

.lock-tip {
  max-width: 640px;
  margin-bottom: 16px;
}

.form {
  max-width: 640px;
}

/* 标签文案（如「前端发起路径」）单行显示，不换行 */
.form :deep(.el-form-item__label) {
  white-space: nowrap;
}

.designer-body {
  height: calc(100vh - 260px);
  min-height: 400px;
}

/* 版本视图（注入设计器工具栏右侧扩展位）：与工具栏原「版本号」标签同款字重 */
.version-label {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

/* 下拉中最大版本号（当前已发布版本）右侧的彩色小字标记 */
.version-current {
  margin-left: 8px;
  font-size: 12px;
  color: #67c23a;
}

.designer {
  flex: 1;
  min-height: 0;
}
</style>
