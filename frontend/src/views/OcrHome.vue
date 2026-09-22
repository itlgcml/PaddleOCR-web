<script setup lang="ts">
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { Clock, DocumentChecked, Picture } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const { user } = storeToRefs(auth)

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})
const displayName = computed(() => user.value?.nickname || user.value?.username || '用户')

const features = [
  { icon: Picture, title: '图片文字识别', desc: '上传图片，基于 PaddleOCR 引擎提取文本内容，即将上线。' },
  { icon: DocumentChecked, title: '识别结果管理', desc: '结构化保存识别结果，支持复制与导出，即将上线。' },
  { icon: Clock, title: '历史记录追溯', desc: '完整保留每次识别的时间、文件与结果，即将上线。' },
]
</script>

<template>
  <div class="home">
    <div class="hero">
      <div class="hero-title">{{ greeting }}，{{ displayName }}</div>
      <div class="hero-sub">欢迎使用 PaddleOCR 在线识别平台，文字识别功能正在接入中，敬请期待。</div>
    </div>
    <div class="features">
      <div v-for="feature in features" :key="feature.title" class="feature-card">
        <div class="feature-icon">
          <el-icon :size="22"><component :is="feature.icon" /></el-icon>
        </div>
        <div class="feature-title">{{ feature.title }}</div>
        <div class="feature-desc">{{ feature.desc }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.hero {
  padding: 36px 40px;
  color: #ffffff;
  background: linear-gradient(120deg, #1d2b3a 0%, #1e6fff 100%);
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(30, 111, 255, 0.25);
}

.hero-title {
  font-size: 24px;
  font-weight: 600;
}

.hero-sub {
  margin-top: 8px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.85);
}

.features {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-top: 16px;
}

.feature-card {
  padding: 24px;
  background-color: #ffffff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  transition: transform 0.2s, box-shadow 0.2s;
}

.feature-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(30, 111, 255, 0.12);
}

.feature-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  margin-bottom: 14px;
  color: #1e6fff;
  background-color: rgba(30, 111, 255, 0.1);
  border-radius: 10px;
}

.feature-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.feature-desc {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.6;
  color: #909399;
}
</style>
