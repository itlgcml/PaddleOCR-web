<script setup lang="ts">
import { View, Delete } from '@element-plus/icons-vue'
import type { OcrRecord } from '@/types/ocr'

defineProps<{
  records: OcrRecord[]
  /** 查询进行中（表格 loading 遮罩） */
  loading?: boolean
  /** 删除进行中的记录 ID（行内按钮 loading，其余行禁用防并发） */
  removingId?: string | null
}>()

const emit = defineEmits<{
  (e: 'detail', record: OcrRecord): void
  (e: 'remove', record: OcrRecord): void
}>()

/** 摘要列：多行文本压成单行，配合 show-overflow-tooltip 截断 */
function toSummary(text: string): string {
  return text.replace(/\s+/g, ' ').trim()
}
</script>

<template>
  <el-table
    v-loading="loading"
    :data="records"
    row-key="id"
    empty-text="暂无识别记录"
    stripe
  >
    <el-table-column prop="fileName" label="文件名" min-width="180" show-overflow-tooltip />
    <el-table-column label="识别摘要" min-width="240" show-overflow-tooltip>
      <template #default="{ row }">
        {{ toSummary(row.resultText) }}
      </template>
    </el-table-column>
    <el-table-column prop="costMs" label="耗时(ms)" width="100" align="right" />
    <el-table-column prop="createTime" label="识别时间" width="170" />
    <el-table-column label="操作" width="150" fixed="right">
      <template #default="{ row }">
<!-- el-table-column 插槽 row 为 EP 内置 DefaultRow（Record<PropertyKey, any>），
     无法从 data 泛型推断，emit 处局部 as 收窄为 OcrRecord -->
        <el-button link type="primary" :icon="View" @click="emit('detail', row as OcrRecord)">
          详情
        </el-button>
        <el-button
          link
          type="danger"
          :icon="Delete"
          :loading="removingId === row.id"
          :disabled="removingId !== null && removingId !== row.id"
          @click="emit('remove', row as OcrRecord)"
        >
          删除
        </el-button>
      </template>
    </el-table-column>
  </el-table>
</template>
