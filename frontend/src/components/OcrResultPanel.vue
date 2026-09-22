<script setup lang="ts">
import { computed, type DeepReadonly } from 'vue'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import { CopyDocument, Download } from '@element-plus/icons-vue'
import { copyText } from '@/utils/clipboard'
import { downloadTextFile } from '@/utils/file'
import type { OcrResult } from '@/types/ocr'

const props = defineProps<{
  /** 识别结果（null 时显示空态；来自 composable 的 readonly 状态） */
  result: DeepReadonly<OcrResult> | null
  /** 上传文件名（用于导出命名，缺省回退时间戳） */
  fileName?: string
}>()

const stats = computed(() => {
  if (!props.result) {
    return null
  }
  return {
    boxes: props.result.textBoxes.length,
    cost: props.result.costMs,
  }
})

/** 导出文件名：原文件名去扩展名 + 后缀，无文件名时用时间戳 */
const exportName = computed(() => {
  const base = props.fileName?.replace(/\.[^.]+$/, '') || `OCR-${Date.now()}`
  return `${base}-识别结果.txt`
})

async function handleCopy(): Promise<void> {
  if (!props.result) {
    return
  }
  if (await copyText(props.result.fullText)) {
    ElMessage.success('已复制到剪贴板')
  } else {
    ElMessage.error('复制失败，请手动选择文本复制')
  }
}

function handleExport(): void {
  if (!props.result) {
    return
  }
  downloadTextFile(exportName.value, props.result.fullText)
}
</script>

<template>
  <div class="result-panel">
    <el-empty v-if="!result" description="识别结果将显示在这里" />
    <template v-else>
      <div class="result-toolbar">
        <span class="result-stats">{{ stats?.boxes }} 个文本框 · 耗时 {{ stats?.cost }} ms</span>
        <span class="result-actions">
          <el-button type="primary" size="small" :icon="CopyDocument" @click="handleCopy">
            复制全文
          </el-button>
          <el-button size="small" :icon="Download" @click="handleExport">导出 .txt</el-button>
        </span>
      </div>
      <!-- pre 保留 fullText 的 \n 换行 -->
      <pre class="result-text">{{ result.fullText }}</pre>
    </template>
  </div>
</template>

<style scoped>
.result-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}

.result-stats {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.result-text {
  margin: 12px 0 0;
  padding: 12px;
  max-height: 420px;
  overflow: auto;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-all;
  background-color: var(--el-fill-color-light);
  border-radius: 6px;
}
</style>
