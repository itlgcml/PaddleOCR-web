<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { submitLawFirmSelectCreate } from '@/api/lawfirmselect'

const router = useRouter()

const formRef = ref<FormInstance | null>(null)
const form = reactive({
  title: '',
  amount: undefined as number | undefined,
  reason: '',
})

const rules: FormRules = {
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { max: 128, message: '标题不能超过 128 个字符', trigger: 'blur' },
  ],
  amount: [{ required: true, message: '请输入选聘金额', trigger: ['blur', 'change'] }],
  reason: [
    { required: true, message: '请输入选聘原因', trigger: 'blur' },
    { max: 512, message: '选聘原因不能超过 512 个字符', trigger: 'blur' },
  ],
}

const submitting = ref(false)

/** 返回列表页（不走浏览器历史，避免表单页残留在栈中） */
function goBack(): void {
  void router.push({ name: 'LawFirmSelect' })
}

async function submitCreate(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  const amount = form.amount
  if (amount == null) {
    return
  }
  submitting.value = true
  try {
    await submitLawFirmSelectCreate({
      title: form.title,
      amount,
      reason: form.reason,
    })
    ElMessage.success('律所选聘记录创建成功')
    goBack()
  } catch {
    // 业务失败已由拦截器统一提示
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div v-loading="submitting" class="page-card">
    <div class="page-header">
      <el-button :icon="ArrowLeft" @click="goBack">返回</el-button>
      <span class="page-title">新增律所选聘</span>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="88px" class="form">
      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" placeholder="请输入标题" maxlength="128" />
      </el-form-item>
      <el-form-item label="选聘金额" prop="amount">
        <el-input-number
          v-model="form.amount"
          class="amount-input"
          :min="0.01"
          :max="9999999999.99"
          :precision="2"
          :step="1000"
          controls-position="right"
          placeholder="请输入选聘金额（元）"
        />
      </el-form-item>
      <el-form-item label="选聘原因" prop="reason">
        <el-input
          v-model="form.reason"
          type="textarea"
          :rows="4"
          placeholder="请输入选聘原因"
          maxlength="512"
          show-word-limit
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="goBack">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">保存</el-button>
      </el-form-item>
    </el-form>
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

.page-header {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 24px;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.form {
  max-width: 560px;
}

.amount-input {
  width: 100%;
}
</style>