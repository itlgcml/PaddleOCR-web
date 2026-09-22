/**
 * 统一响应结构，与后端 `com.paddleocr.web.common.ApiResponse` 逐字段镜像。
 * 注意：`timestamp` 为后端 long 毫秒（非 ISO 字符串）。
 */
export interface ApiResponse<T> {
  /** 0 成功；4xxxx 客户端错误；5xxxx 系统错误；503xx OCR 服务错误 */
  code: number
  message: string
  data: T | null
  timestamp: number
}

/** 分页查询入参（页码从 1 开始） */
export interface PageQuery {
  pageNum?: number
  pageSize?: number
}

/** 分页响应（与后端 PageResultVO 镜像） */
export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}
