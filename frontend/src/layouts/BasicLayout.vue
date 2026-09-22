<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { CaretBottom, HomeFilled, Key, OfficeBuilding, Setting, SwitchButton, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const ROLE_LABELS: Record<string, string> = { ADMIN: '管理员', USER: '普通用户' }

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { user, isAdmin } = storeToRefs(auth)

const pageTitle = computed(() => route.meta.title || '首页')
const displayName = computed(() => user.value?.nickname || user.value?.username || '用户')
const avatarChar = computed(() => displayName.value.charAt(0).toUpperCase())
const roleLabels = computed(() => {
  const roles = user.value?.roles ?? []
  if (roles.length === 0) {
    return ['未分配角色']
  }
  return roles.map((code) => ROLE_LABELS[code] ?? code)
})

const loggingOut = ref(false)

async function handleCommand(command: string | number | object): Promise<void> {
  if (command !== 'logout') {
    return
  }
  loggingOut.value = true
  try {
    try {
      await auth.logout()
    } catch {
      // 服务端登出失败不阻断本地退出（本地态已在 store 中清空）
    }
    ElMessage.success('已退出登录')
    await router.push({ name: 'Login' })
  } finally {
    loggingOut.value = false
  }
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">
        <div class="logo-icon">P</div>
        <span class="logo-text">PaddleOCR</span>
      </div>
      <el-menu
        class="menu"
        router
        :default-active="route.path"
        background-color="#1d2b3a"
        text-color="rgba(255, 255, 255, 0.65)"
        active-text-color="#ffffff"
      >
        <el-menu-item index="/">
          <el-icon><HomeFilled /></el-icon>
          <span>首页</span>
        </el-menu-item>
        <el-sub-menu v-if="isAdmin" index="/system">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统设置</span>
          </template>
          <el-menu-item index="/system/orgs">
            <el-icon><OfficeBuilding /></el-icon>
            <span>机构管理</span>
          </el-menu-item>
          <el-menu-item index="/system/roles">
            <el-icon><Key /></el-icon>
            <span>角色管理</span>
          </el-menu-item>
          <el-menu-item index="/system/users">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container class="body">
      <el-header class="header">
        <div class="page-title">{{ pageTitle }}</div>
        <el-dropdown trigger="click" @command="handleCommand">
          <div class="user-chip">
            <div class="avatar">{{ avatarChar }}</div>
            <span class="user-name">{{ displayName }}</span>
            <el-icon class="caret"><CaretBottom /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <div class="dropdown-profile">
                <div class="dropdown-username">{{ user?.username ?? '-' }}</div>
                <div class="dropdown-org">{{ user?.org?.orgName ?? '未分配机构' }}</div>
                <div class="dropdown-roles">
                  <el-tag v-for="label in roleLabels" :key="label" size="small" type="info">{{ label }}</el-tag>
                </div>
              </div>
              <el-dropdown-item divided :icon="SwitchButton" command="logout" :disabled="loggingOut">
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
}

.aside {
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  background-color: #1d2b3a;
}

.logo {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 20px 20px 16px;
}

.logo-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  font-size: 18px;
  font-weight: 700;
  color: #ffffff;
  background: linear-gradient(135deg, #1e6fff, #3e8bff);
  border-radius: 10px;
  box-shadow: 0 6px 14px rgba(30, 111, 255, 0.4);
}

.logo-text {
  font-size: 17px;
  font-weight: 600;
  color: #ffffff;
  letter-spacing: 0.5px;
}

.menu {
  flex: 1;
  border-right: none;
}

.menu :deep(.el-menu-item.is-active) {
  background-color: #1e6fff;
}

.menu :deep(.el-menu-item:hover),
.menu :deep(.el-sub-menu__title:hover) {
  background-color: rgba(255, 255, 255, 0.06);
}

.menu :deep(.el-sub-menu .el-menu) {
  background-color: #16222e;
}

.body {
  min-width: 0;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  padding: 0 24px;
  background-color: #ffffff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.user-chip {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 4px 8px;
  cursor: pointer;
  border-radius: 8px;
  transition: background-color 0.2s;
}

.user-chip:hover {
  background-color: #f5f7fa;
}

.avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  font-size: 14px;
  font-weight: 600;
  color: #ffffff;
  background: linear-gradient(135deg, #1e6fff, #3e8bff);
  border-radius: 50%;
}

.user-name {
  max-width: 140px;
  overflow: hidden;
  font-size: 14px;
  color: #303133;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.caret {
  color: #909399;
}

.dropdown-profile {
  min-width: 200px;
  padding: 10px 16px;
}

.dropdown-username {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.dropdown-org {
  margin: 2px 0 8px;
  font-size: 12px;
  color: #909399;
}

.dropdown-roles {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.main {
  padding: 20px;
  overflow-y: auto;
  background-color: #f5f7fa;
}
</style>
