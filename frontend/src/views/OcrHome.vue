<script setup lang="ts">
import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { Clock, DocumentChecked, Picture } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useOcrRecognize } from '@/composables/useOcrRecognize'
import OcrUploadDialog from '@/components/OcrUploadDialog.vue'
import type { OcrUploadPayload } from '@/types/ocr'

const auth = useAuthStore()
const { user } = storeToRefs(auth)

const { submitting, submitOcrFile } = useOcrRecognize()

const uploadVisible = ref(false)

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})
const displayName = computed(() => user.value?.nickname || user.value?.username || '用户')

type FeatureAction = 'recognize'

const features: Array<{
  icon: typeof Picture
  title: string
  desc: string
  action?: FeatureAction
}> = [
  {
    icon: Picture,
    title: '文件识别',
    desc: '上传图片或 PDF 文件，基于 PaddleOCR-VL1.6 引擎提取内容。',
    action: 'recognize',
  },
  { icon: DocumentChecked, title: '识别结果管理', desc: '结构化保存识别结果，支持复制与导出，即将上线。' },
  { icon: Clock, title: '历史记录追溯', desc: '完整保留每次识别的时间、文件与结果，即将上线。' },
]

function onFeatureCardClick(action?: FeatureAction): void {
  if (action === 'recognize') {
    uploadVisible.value = true
  }
}

/** 弹框确认：发起识别；失败保持弹框打开供重试/换文件 */
async function onUploadConfirm(payload: OcrUploadPayload): Promise<void> {
  const success = await submitOcrFile(payload.file, payload.fileType)
  if (success) {
    uploadVisible.value = false
  }
}
</script>

<template>
  <div class="home">
    <div class="hero">
      <div class="hero-title">{{ greeting }}，{{ displayName }}</div>
      <div class="hero-sub">欢迎使用 PaddleOCR 在线识别平台，上传图片或 PDF 文件即可快速提取内容。</div>
    </div>
    <div class="features">
      <div
        v-for="feature in features"
        :key="feature.title"
        class="feature-card"
        :class="{ actionable: !!feature.action }"
        :tabindex="feature.action ? 0 : undefined"
        @click="onFeatureCardClick(feature.action)"
        @keydown.enter="onFeatureCardClick(feature.action)"
      >
        <div class="feature-icon">
          <el-icon :size="22"><component :is="feature.icon" /></el-icon>
        </div>
        <div class="feature-title">{{ feature.title }}</div>
        <div class="feature-desc">{{ feature.desc }}</div>
        <div v-if="feature.action" class="feature-entry">点击上传 →</div>
      </div>
    </div>
    <OcrUploadDialog v-model="uploadVisible" :loading="submitting" @confirm="onUploadConfirm" />
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

.feature-card.actionable {
  cursor: pointer;
}

.feature-card.actionable:focus-visible {
  outline: 2px solid #1e6fff;
  outline-offset: 2px;
}

.feature-entry {
  margin-top: 10px;
  font-size: 13px;
  font-weight: 500;
  color: #1e6fff;
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
