/**
 * 识别历史记录 store（setup store）
 * 状态：分页 + 文件名关键词；所有 state 均通过 return 暴露（DevTools/持久化友好）
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchRecords, removeRecord } from '@/api/record'
import type { OcrRecord } from '@/types/ocr'

export const useRecordStore = defineStore('record', () => {
  /** 当前页数据 */
  const records = ref<OcrRecord[]>([])
  /** 总条数 */
  const total = ref(0)
  /** 当前页码（从 1 开始，镜像后端校验） */
  const pageNum = ref(1)
  /** 每页条数（后端上限 100） */
  const pageSize = ref(10)
  /** 文件名关键词（后端 @Size(max = 255)） */
  const keyword = ref('')
  /** 表格加载状态 */
  const loading = ref(false)

  /** 竞态守卫：快速搜索/翻页时仅最后一次请求的结果生效 */
  let requestSeq = 0

  /** 按当前分页与关键词查询；keyword 为空串时不下发该参数 */
  async function loadRecords(): Promise<void> {
    const seq = ++requestSeq
    loading.value = true
    try {
      const page = await fetchRecords({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        keyword: keyword.value.trim() || undefined,
      })
      if (seq !== requestSeq) {
        return
      }
      records.value = page.records
      total.value = page.total
    } finally {
      if (seq === requestSeq) {
        loading.value = false
      }
    }
  }

  /** 关键词搜索：trim 后重置到第一页再查询 */
  function search(nextKeyword: string): Promise<void> {
    keyword.value = nextKeyword.trim()
    pageNum.value = 1
    return loadRecords()
  }

  /** 翻页 */
  function changePage(next: number): Promise<void> {
    pageNum.value = next
    return loadRecords()
  }

  /** 切换每页条数：回到第一页 */
  function changePageSize(next: number): Promise<void> {
    pageSize.value = next
    pageNum.value = 1
    return loadRecords()
  }

  /** 删除记录；删空当前页且非首页时自动回退一页 */
  async function removeById(id: string): Promise<void> {
    await removeRecord(id)
    if (records.value.length === 1 && pageNum.value > 1) {
      pageNum.value -= 1
    }
    await loadRecords()
  }

  return {
    records,
    total,
    pageNum,
    pageSize,
    keyword,
    loading,
    loadRecords,
    search,
    changePage,
    changePageSize,
    removeById,
  }
})
