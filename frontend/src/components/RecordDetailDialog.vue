<script setup lang="ts">
import type { OcrRecord } from '@/types/ocr'

/** 弹窗显隐双向绑定（父持有状态） */
const visible = defineModel<boolean>('visible', { default: false })

defineProps<{
  /** 当前查看的记录（null 时内容区不渲染） */
  record: OcrRecord | null
}>()
</script>

<template>
  <el-dialog v-model="visible" title="识别详情" width="640px" append-to-body>
    <template v-if="record">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="文件名" :span="2">
          {{ record.fileName }}
        </el-descriptions-item>
        <el-descriptions-item label="耗时">{{ record.costMs }} ms</el-descriptions-item>
        <el-descriptions-item label="识别时间">{{ record.createTime }}</el-descriptions-item>
      </el-descriptions>
      <!-- pre 保留 resultText 换行 -->
      <pre class="detail-text">{{ record.resultText }}</pre>
    </template>
  </el-dialog>
</template>

<style scoped>
.detail-text {
  margin: 16px 0 0;
  padding: 12px;
  max-height: 320px;
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
