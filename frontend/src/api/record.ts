/**
 * 识别历史记录接口（镜像 RecordController：/api/records）
 */
import request from './request'
import type { PageResult } from '@/types/api'
import type { OcrRecord, RecordQuery } from '@/types/ocr'

/** 分页查询识别记录（pageNum ≥ 1，pageSize ≤ 100，keyword 按文件名模糊匹配） */
export function fetchRecords(query: RecordQuery): Promise<PageResult<OcrRecord>> {
  return request.get<PageResult<OcrRecord>, PageResult<OcrRecord>>('/records', {
    params: query,
  })
}

/** 查询记录详情 */
export function fetchRecordDetail(id: string): Promise<OcrRecord> {
  return request.get<OcrRecord, OcrRecord>(`/records/${id}`)
}

/** 删除识别记录 */
export function removeRecord(id: string): Promise<void> {
  return request.delete<void, void>(`/records/${id}`)
}
