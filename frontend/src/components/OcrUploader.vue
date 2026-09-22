<script setup lang="ts">
import type { UploadRequestOptions } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'

const props = defineProps<{
  /** 允许的扩展名白名单（小写、不含点） */
  accept: string[]
  /** 单文件大小上限（MB） */
  maxSizeMb: number
  /** 识别进行中（禁用重复提交） */
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'upload', file: File): void
  (e: 'error', message: string): void
}>()

/** el-upload 自定义上传：只做客户端校验，通过后上抛文件，由父组件调 composable
 *  async 返回 Promise 以满足 UploadRequestHandler 的返回类型约束 */
async function handleUpload(options: UploadRequestOptions): Promise<void> {
  const file = options.file
  const ext = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (!props.accept.includes(ext)) {
    emit('error', `仅支持 ${props.accept.join(' / ')} 格式`)
    return
  }
  if (file.size > props.maxSizeMb * 1024 * 1024) {
    emit('error', `图片不能超过 ${props.maxSizeMb}MB`)
    return
  }
  emit('upload', file)
}
</script>

<template>
  <el-upload
    drag
    class="uploader"
    :show-file-list="false"
    :http-request="handleUpload"
    :accept="accept.map((ext) => `.${ext}`).join(',')"
    :disabled="loading"
  >
    <el-icon :size="48" class="uploader-icon"><Upload /></el-icon>
    <div class="uploader-text">拖拽图片到此处，或 <em>点击上传</em></div>
    <template #tip>
      <div class="uploader-hint">支持 {{ accept.join(' / ') }}，不超过 {{ maxSizeMb }}MB</div>
    </template>
  </el-upload>
</template>

<style scoped>
.uploader {
  width: 100%;
}

.uploader-icon {
  color: var(--el-color-primary);
}

.uploader-text {
  margin-top: 8px;
  color: var(--el-text-color-regular);
}

.uploader-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
}
</style>
