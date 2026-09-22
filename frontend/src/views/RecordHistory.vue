<script setup lang="ts">
import { onMounted, shallowRef } from 'vue'
import RecordFilter from '@/components/RecordFilter.vue'
import RecordTable from '@/components/RecordTable.vue'
import RecordDetailDialog from '@/components/RecordDetailDialog.vue'
import { useRecordHistory } from '@/composables/useRecordHistory'
import type { OcrRecord } from '@/types/ocr'

const {
  records,
  total,
  pageNum,
  pageSize,
  keyword,
  loading,
  removingId,
  refresh,
  search,
  changePage,
  changePageSize,
  confirmRemove,
} = useRecordHistory()

/** 分页大小可选项（后端 pageSize 上限 100，此处远低于上限） */
const PAGE_SIZES = [5, 10, 20]

/** 详情弹窗：显隐与当前记录由视图持有 */
const detailVisible = shallowRef(false)
const currentRecord = shallowRef<OcrRecord | null>(null)

onMounted(() => {
  void refresh()
})

function handleSearch(): void {
  void search(keyword.value)
}

function handleDetail(record: OcrRecord): void {
  currentRecord.value = record
  detailVisible.value = true
}

function handleRemove(record: OcrRecord): void {
  // 确认框 + 删除 + 删空页回退刷新均在 composable/store 中处理
  void confirmRemove(record)
}

function handleChangePage(page: number): void {
  void changePage(page)
}

function handleChangeSize(size: number): void {
  void changePageSize(size)
}
</script>

<template>
  <div class="history">
    <el-card shadow="never">
      <RecordFilter v-model="keyword" :loading="loading" @search="handleSearch" />
      <RecordTable
        class="history-table"
        :records="records"
        :loading="loading"
        :removing-id="removingId"
        @detail="handleDetail"
        @remove="handleRemove"
      />
      <!-- 单向绑定分页状态：状态源唯一在 store，翻页/改页大小走 action -->
      <el-pagination
        class="history-pagination"
        :current-page="pageNum"
        :page-size="pageSize"
        :page-sizes="PAGE_SIZES"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="handleChangePage"
        @size-change="handleChangeSize"
      />
    </el-card>

    <RecordDetailDialog v-model:visible="detailVisible" :record="currentRecord" />
  </div>
</template>

<style scoped>
.history-table {
  margin-top: 16px;
}

.history-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
