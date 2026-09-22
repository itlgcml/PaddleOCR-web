<script setup lang="ts">
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import OcrUploader from '@/components/OcrUploader.vue'
import OcrImageViewer from '@/components/OcrImageViewer.vue'
import OcrResultPanel from '@/components/OcrResultPanel.vue'
import { useOcrRecognize } from '@/composables/useOcrRecognize'

/** 后端契约：支持格式与大小上限（OcrController 校验规则，前端先行拦截） */
const ACCEPT = ['jpg', 'jpeg', 'png', 'bmp', 'webp']
const MAX_SIZE_MB = 10

const { loading, result, previewUrl, previewName, recognize, reset } = useOcrRecognize()

function handleUpload(file: File): void {
  void recognize(file)
}

function handleUploadError(message: string): void {
  ElMessage.warning(message)
}
</script>

<template>
  <div class="ocr-home">
    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="ocr-card">
          <template #header>
            <div class="ocr-card-header">
              <span>上传图片</span>
              <el-button v-if="previewUrl" link type="danger" @click="reset">清空</el-button>
            </div>
          </template>
          <OcrUploader
            :accept="ACCEPT"
            :max-size-mb="MAX_SIZE_MB"
            :loading="loading"
            @upload="handleUpload"
            @error="handleUploadError"
          />
          <OcrImageViewer
            class="ocr-viewer"
            :src="previewUrl"
            :file-name="previewName"
            :text-boxes="result?.textBoxes ?? []"
            :loading="loading"
          />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="ocr-card">
          <template #header>
            <span>识别结果</span>
          </template>
          <OcrResultPanel :result="result" :file-name="previewName" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.ocr-card {
  min-height: 320px;
}

.ocr-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ocr-viewer {
  margin-top: 16px;
}
</style>
