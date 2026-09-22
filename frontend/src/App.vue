<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Clock, Picture } from '@element-plus/icons-vue'

const route = useRoute()
/** 当前激活菜单：跟随路由路径变化 */
const activeMenu = computed(() => route.path)
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <div class="header-inner">
        <RouterLink to="/ocr" class="brand">
          <span class="brand-logo">
            <el-icon :size="18"><Picture /></el-icon>
          </span>
          <span class="brand-text">
            <span class="brand-name">PaddleOCR</span>
            <span class="brand-sub">在线文字识别</span>
          </span>
        </RouterLink>

        <el-menu
          class="header-menu"
          mode="horizontal"
          router
          :default-active="activeMenu"
          :ellipsis="false"
          background-color="transparent"
          text-color="rgba(255, 255, 255, 0.72)"
          active-text-color="#ffffff"
        >
          <el-menu-item index="/ocr">
            <el-icon><Picture /></el-icon>
            <span>OCR 识别</span>
          </el-menu-item>
          <el-menu-item index="/history">
            <el-icon><Clock /></el-icon>
            <span>历史记录</span>
          </el-menu-item>
        </el-menu>
      </div>
    </header>

    <main class="app-main">
      <div class="app-container">
        <RouterView />
      </div>
    </main>

    <footer class="app-footer">
      <span>PaddleOCR-web · 基于 PaddleOCR 的在线文字识别服务</span>
    </footer>
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

/* ---------- 顶部导航 ---------- */
.app-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: linear-gradient(120deg, #16294d 0%, #1d4ed8 100%);
  box-shadow: 0 2px 12px rgba(22, 41, 77, 0.28);
}

.header-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1200px;
  height: 60px;
  margin: 0 auto;
  padding: 0 20px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
}

.brand-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  color: #ffffff;
  background: linear-gradient(135deg, #79bbff, #337ecc);
  box-shadow: 0 2px 8px rgba(51, 126, 204, 0.45);
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.brand-name {
  font-size: 17px;
  font-weight: 600;
  color: #ffffff;
  letter-spacing: 0.5px;
}

.brand-sub {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
}

.header-menu {
  height: 60px;
  border-bottom: none;
}

.header-menu :deep(.el-menu-item) {
  height: 60px;
  margin: 0 4px;
  padding: 0 18px;
  font-size: 14px;
  border-bottom: 2px solid transparent;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease;
}

.header-menu :deep(.el-menu-item:hover) {
  background-color: rgba(255, 255, 255, 0.1);
}

.header-menu :deep(.el-menu-item.is-active) {
  border-bottom-color: #ffffff;
  background-color: rgba(255, 255, 255, 0.14);
}

/* ---------- 主内容区 ---------- */
.app-main {
  flex: 1;
  padding: 24px 20px 32px;
}

.app-container {
  max-width: 1200px;
  margin: 0 auto;
}

/* ---------- 页脚 ---------- */
.app-footer {
  padding: 14px 20px;
  font-size: 12px;
  color: #909399;
  text-align: center;
  background-color: #ffffff;
  border-top: 1px solid #ebeef5;
}
</style>
