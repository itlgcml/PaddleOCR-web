<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const formRef = ref<FormInstance | null>(null)
const loading = ref(false)
const form = reactive({
  // 注册成功跳转时通过 query.username 带入，预填用户名
  username: typeof route.query.username === 'string' ? route.query.username : '',
  password: '',
})

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
}

async function handleLogin(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  loading.value = true
  try {
    await auth.login({ username: form.username, password: form.password })
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect !== '' ? route.query.redirect : '/'
    await router.push(redirect)
  } catch {
    // 业务失败已由拦截器统一提示
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
        <h1 class="brand-title">PaddleOCR 在线识别</h1>
        <p class="brand-subtitle">登录你的账号，开始文字识别之旅</p>
      </div>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" autocomplete="username" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <el-form-item class="submit-item">
          <el-button type="primary" class="submit-btn" :loading="loading" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
      <div class="auth-footer">
        还没有账号？
        <router-link class="link" :to="{ name: 'Register' }">立即注册</router-link>
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
  background: linear-gradient(135deg, #1d2b3a 0%, #1e6fff 100%);
}

.auth-card {
  width: 400px;
  padding: 40px 36px 28px;
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
  margin-bottom: 28px;
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
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}

.brand-subtitle {
  margin: 0;
  font-size: 14px;
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
