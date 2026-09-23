<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { Delete, Document, UploadFilled } from '@element-plus/icons-vue'
import type { UploadFile, UploadUserFile } from 'element-plus'
import type { OcrFileType, OcrUploadPayload } from '@/types/ocr'

/** 允许的扩展名（小写，与后端白名单一致） */
const ALLOWED_EXTENSIONS = ['pdf', 'jpg', 'jpeg', 'png', 'bmp', 'webp']

/** 上传大小上限（与后端 multipart 限制一致） */
const MAX_FILE_SIZE_MB = 100

const props = defineProps<{ loading?: boolean }>()

const emit = defineEmits<{ (e: 'confirm', payload: OcrUploadPayload): void }>()

const visible = defineModel<boolean>({ required: true })

const fileList = ref<UploadUserFile[]>([])
const selectedFile = shallowRef<File | null>(null)
const validationError = ref('')

const fileType = computed<OcrFileType>(() =>
  selectedFile.value?.name.toLowerCase().endsWith('.pdf') ? 0 : 1,
)

const confirmDisabled = computed(() => props.loading || !selectedFile.value)

watch(visible, (open) => {
  if (open) {
    resetSelection()
  }
})

/** 选择文件：本地预校验（扩展名 + 大小），失败即清空并提示 */
function onFileChange(uploadFile: UploadFile): void {
  validationError.value = ''
  const raw = uploadFile.raw
  if (!raw) {
    return
  }
  const extension = raw.name.split('.').pop()?.toLowerCase() ?? ''
  if (!ALLOWED_EXTENSIONS.includes(extension)) {
    validationError.value = `不支持的文件类型 .${extension || '未知'}，仅支持 PDF 与图片（jpg/jpeg/png/bmp/webp）`
    fileList.value = []
    selectedFile.value = null
    return
  }
  if (raw.size > MAX_FILE_SIZE_MB * 1024 * 1024) {
    validationError.value = `文件大小超过 ${MAX_FILE_SIZE_MB}MB 上限，请重新选择`
    fileList.value = []
    selectedFile.value = null
    return
  }
  selectedFile.value = raw
}

/** 移除已选文件 */
function onFileRemove(): void {
  selectedFile.value = null
  validationError.value = ''
}

/** 超出单文件限制时替换原选择（limit=1 场景） */
function onFileExceed(): void {
  validationError.value = '一次仅支持识别一个文件，请先移除已选文件'
}

/** 确认：向上抛出文件与推导出的类型，由调用方发起识别 */
function onConfirm(): void {
  const file = selectedFile.value
  if (!file || props.loading) {
    return
  }
  emit('confirm', { file, fileType: fileType.value })
}

/** 识别请求进行中禁止关闭弹框 */
function beforeClose(done: () => void): void {
  if (!props.loading) {
    done()
  }
}

function resetSelection(): void {
  fileList.value = []
  selectedFile.value = null
  validationError.value = ''
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="上传识别文件"
    width="520px"
    align-center
    :close-on-click-modal="!loading"
    :close-on-press-escape="!loading"
    :show-close="!loading"
    :before-close="beforeClose"
  >
    <el-upload
      v-model:file-list="fileList"
      drag
      action="#"
      :auto-upload="false"
      :limit="1"
      accept=".pdf,.jpg,.jpeg,.png,.bmp,.webp"
      :on-change="onFileChange"
      :on-remove="onFileRemove"
      :on-exceed="onFileExceed"
    >
      <el-icon :size="42" class="upload-icon"><UploadFilled /></el-icon>
      <div class="upload-text">将文件拖到此处，或点击选择</div>
      <template #tip>
        <div class="upload-tip">支持 PDF / JPG / PNG / BMP / WEBP，单个文件不超过 100MB</div>
      </template>
    </el-upload>

    <el-alert
      v-if="validationError"
      :title="validationError"
      type="error"
      show-icon
      :closable="false"
      class="upload-error"
    />

    <div v-if="selectedFile" class="selected-file">
      <el-icon :size="16"><Document /></el-icon>
      <span class="selected-name" :title="selectedFile.name">{{ selectedFile.name }}</span>
      <el-tag size="small" type="info">{{ fileType === 0 ? 'PDF 文档' : '图像文件' }}</el-tag>
      <el-icon :size="16" class="selected-remove" title="移除文件" @click="onFileRemove">
        <Delete />
      </el-icon>
    </div>

    <template #footer>
      <el-button :disabled="loading" @click="visible = false">取 消</el-button>
      <el-button type="primary" :loading="loading" :disabled="confirmDisabled" @click="onConfirm">
        {{ loading ? '识别中…' : '开始识别' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.upload-icon {
  color: #1e6fff;
}

.upload-text {
  margin-top: 8px;
  font-size: 14px;
  color: #606266;
}

.upload-tip {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.upload-error {
  margin-top: 12px;
}

.selected-file {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 8px 12px;
  font-size: 13px;
  background-color: #f5f7fa;
  border-radius: 6px;
}

.selected-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #303133;
}

.selected-remove {
  color: #909399;
  cursor: pointer;
}

.selected-remove:hover {
  color: #f56c6c;
}
</style>
