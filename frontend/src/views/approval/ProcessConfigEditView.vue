<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ArrowLeft, ArrowRight, Check, Promotion } from '@element-plus/icons-vue'
import { useWorkflowStore } from '@/stores/workflow'
import WorkflowDesignView from '@/views/system/WorkflowDesignView.vue'

const route = useRoute()
const router = useRouter()
const workflowStore = useWorkflowStore()

const activeStep = ref(0)
const isEdit = computed(() => !!route.query.id)

const formRef = ref<FormInstance | null>(null)
const form = reactive({
  code: '',
  name: '',
  description: '',
  initiators: ['全体人员'] as string[],
})

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
  initiators: [{ required: true, message: '请选择谁可以发起', trigger: 'change' }],
}

/** 「谁可以发起」可选范围（前端写死示例） */
const initiatorOptions = ['全体人员', '部门负责人', '指定角色', '指定人员']

onMounted(() => {
  if (isEdit.value) {
    form.code = String(route.query.code ?? '')
    form.name = String(route.query.name ?? '')
  } else {
    workflowStore.resetDoc()
  }
})

async function handleNext(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  activeStep.value = 1
}

function handlePrev(): void {
  activeStep.value = 0
}

function handleSave(): void {
  ElMessage.success(isEdit.value ? '流程已更新' : '流程已保存')
  void router.push({ name: 'ApprovalProcessConfig' })
}

function handlePublish(): void {
  ElMessage.success('流程已发布')
  void router.push({ name: 'ApprovalProcessConfig' })
}
</script>

<template>
  <div class="page-card">
    <div class="wizard-header">
      <el-steps :active="activeStep" align-center finish-status="success" class="steps">
        <el-step title="基本信息" description="填写流程编码、名称、描述与发起人" />
        <el-step title="流程设计" description="可视化编排审批流程" />
      </el-steps>
      <div class="header-actions">
        <el-button v-if="activeStep === 1" :icon="ArrowLeft" @click="handlePrev">上一步</el-button>
        <el-button v-if="activeStep === 0" type="primary" :icon="ArrowRight" @click="handleNext">下一步</el-button>
        <el-button v-if="activeStep === 1" type="primary" :icon="Check" @click="handleSave">保存</el-button>
        <el-button v-if="activeStep === 1" type="success" :icon="Promotion" @click="handlePublish">发布</el-button>
      </div>
    </div>

    <!-- 第 1 步：基本信息 -->
    <div v-if="activeStep === 0" class="step-body">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="form">
        <el-form-item label="流程编码" prop="code">
          <el-input v-model="form.code" placeholder="如 LEAVE_APPROVAL" maxlength="64" />
        </el-form-item>
        <el-form-item label="流程名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入流程名称" maxlength="64" />
        </el-form-item>
        <el-form-item label="流程描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入流程描述" maxlength="200" />
        </el-form-item>
        <el-form-item label="谁可以发起" prop="initiators">
          <el-select v-model="form.initiators" multiple placeholder="请选择谁可以发起" style="width: 100%">
            <el-option v-for="item in initiatorOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
      </el-form>
    </div>

    <!-- 第 2 步：流程设计 -->
    <div v-else class="step-body designer-body">
      <WorkflowDesignView class="designer" />
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

.form {
  max-width: 640px;
}

.designer-body {
  height: calc(100vh - 260px);
  min-height: 400px;
}

.designer {
  flex: 1;
  min-height: 0;
}
</style>
