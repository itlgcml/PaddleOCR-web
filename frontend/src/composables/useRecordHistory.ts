/**
 * 历史记录页交互逻辑：查询/分页/搜索/删除确认
 * 数据与删空页回退由 record store 承担，本 composable 只做视图层编排
 */
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import { storeToRefs } from 'pinia'
import { shallowRef } from 'vue'
import { useRecordStore } from '@/stores/record'
import type { OcrRecord } from '@/types/ocr'

export function useRecordHistory() {
  const store = useRecordStore()
  const { records, total, pageNum, pageSize, keyword, loading } = storeToRefs(store)

  /** 删除进行中的记录 ID（供操作列按钮 loading 态） */
  const removingId = shallowRef<string | null>(null)

  /** 按 store 当前状态重新查询（页面初次加载/刷新） */
  function refresh(): Promise<void> {
    return store.loadRecords()
  }

  /** 关键词搜索（store 内重置回第一页） */
  function search(nextKeyword: string): Promise<void> {
    return store.search(nextKeyword)
  }

  function changePage(next: number): Promise<void> {
    return store.changePage(next)
  }

  function changePageSize(next: number): Promise<void> {
    return store.changePageSize(next)
  }

  /**
   * 删除记录：先 ElMessageBox 确认，确认后调 store.removeById
   * （删空当前页自动回退一页的逻辑在 store 中处理）
   * @returns 是否实际执行了删除（用户取消返回 false）
   */
  async function confirmRemove(record: OcrRecord): Promise<boolean> {
    try {
      await ElMessageBox.confirm(
        `确定删除「${record.fileName}」的识别记录吗？删除后不可恢复。`,
        '删除确认',
        { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
      )
    } catch {
      return false // 用户点击取消
    }
    removingId.value = record.id
    try {
      await store.removeById(record.id) // 失败由拦截器提示并抛出
      ElMessage.success('已删除')
      return true
    } finally {
      removingId.value = null
    }
  }

  return {
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
  }
}
