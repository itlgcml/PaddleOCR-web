<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { Lock, Message, User } from '@element-plus/icons-vue'
import { submitRegister } from '@/api/auth'

const router = useRouter()
const route = useRoute()

const formRef = ref<FormInstance | null>(null)
const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  email: '',
})

function validateConfirmPassword(_rule: unknown, value: string, callback: (error?: Error) => void): void {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 32, message: '用户名长度为 4~32 位', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名仅支持字母、数字、下划线', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 20, message: '密码长度为 8~20 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
  nickname: [{ max: 64, message: '昵称不能超过 64 个字符', trigger: 'blur' }],
  email: [
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
    { max: 128, message: '邮箱不能超过 128 个字符', trigger: 'blur' },
  ],
}

async function handleRegister(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  loading.value = true
  try {
    await submitRegister({
      username: form.username,
      password: form.password,
      nickname: form.nickname || undefined,
      email: form.email || undefined,
    })
    ElMessage.success('注册成功，请登录')
    await router.push({ name: 'Login', query: { username: form.username, redirect: String(route.query.redirect ?? '') } })
  } catch {
    // 业务失败已由拦截器统一提示（如 40020 用户名已存在）
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="brand">
        <div class="brand-icon">P</div>
        <h1 class="brand-title">注册 PaddleOCR 账号</h1>
        <p class="brand-subtitle">注册后默认加入「默认机构」，可由管理员后续调整</p>
      </div>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        label-position="top"
        @keyup.enter="handleRegister"
      >
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名（4~32 位字母/数字/下划线）" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码（8~20 位）"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="确认密码"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item prop="nickname">
          <el-input v-model="form.nickname" placeholder="昵称（可选，缺省同用户名）" />
        </el-form-item>
        <el-form-item prop="email">
          <el-input v-model="form.email" placeholder="邮箱（可选）" :prefix-icon="Message" />
        </el-form-item>
        <el-form-item class="submit-item">
          <el-button type="primary" class="submit-btn" :loading="loading" @click="handleRegister">
            注 册
          </el-button>
        </el-form-item>
      </el-form>
      <div class="auth-footer">
        已有账号？
        <router-link class="link" :to="{ name: 'Login' }">直接登录</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 32px 0;
  overflow-y: auto;
  background: linear-gradient(135deg, #1d2b3a 0%, #1e6fff 100%);
}

.auth-card {
  width: 400px;
  padding: 36px 36px 24px;
  background: rgba(255, 255, 255, 0.96);
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.28);
  animation: fade-in 0.4s ease both;
}

@keyframes fade-in {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.brand {
  margin-bottom: 24px;
  text-align: center;
}

.brand-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  margin-bottom: 12px;
  font-size: 26px;
  font-weight: 700;
  color: #ffffff;
  background: linear-gradient(135deg, #1e6fff, #3e8bff);
  border-radius: 14px;
  box-shadow: 0 8px 20px rgba(30, 111, 255, 0.35);
}

.brand-title {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 600;
  color: #303133;
}

.brand-subtitle {
  margin: 0;
  font-size: 13px;
  color: #909399;
}

.submit-item {
  margin-bottom: 8px;
}

.submit-btn {
  width: 100%;
  background: linear-gradient(135deg, #1e6fff, #3e8bff);
  border: none;
  transition: opacity 0.2s;
}

.submit-btn:hover {
  opacity: 0.9;
}

.auth-footer {
  font-size: 14px;
  color: #909399;
  text-align: center;
}

.link {
  color: #1e6fff;
  text-decoration: none;
}

.link:hover {
  text-decoration: underline;
}
</style>
