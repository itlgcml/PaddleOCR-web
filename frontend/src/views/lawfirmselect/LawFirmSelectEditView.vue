<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { fetchLawFirmSelectDetail, submitLawFirmSelectUpdate } from '@/api/lawfirmselect'

const route = useRoute()
const router = useRouter()

/** 编辑目标 id（雪花 ID 字符串，由列表页 query 传入） */
const recordId = String(route.query.id ?? '')

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

const detailLoading = ref(true)
const submitting = ref(false)

/** 返回列表页（不走浏览器历史，避免表单页残留在栈中） */
function goBack(): void {
  void router.push({ name: 'LawFirmSelect' })
}

/** 编辑态：按 id 加载详情回填表单 */
async function loadDetail(): Promise<void> {
  if (!recordId) {
    ElMessage.error('缺少记录 id，无法编辑')
    goBack()
    return
  }
  try {
    const detail = await fetchLawFirmSelectDetail(recordId)
    form.title = detail.title
    form.amount = detail.amount
    form.reason = detail.reason
  } catch {
    // 业务失败已由拦截器统一提示（记录不存在 40050），失败回到列表
    goBack()
    return
  } finally {
    detailLoading.value = false
  }
}

onMounted(() => {
  void loadDetail()
})

async function submitUpdate(): Promise<void> {
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
    await submitLawFirmSelectUpdate(recordId, {
      title: form.title,
      amount,
      reason: form.reason,
    })
    ElMessage.success('律所选聘记录已更新')
    goBack()
  } catch {
    // 业务失败已由拦截器统一提示（记录不存在 40050）
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div v-loading="detailLoading || submitting" class="page-card">
    <div class="page-header">
      <el-button :icon="ArrowLeft" @click="goBack">返回</el-button>
      <span class="page-title">编辑律所选聘</span>
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
        <el-button type="primary" :loading="submitting" @click="submitUpdate">保存</el-button>
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
