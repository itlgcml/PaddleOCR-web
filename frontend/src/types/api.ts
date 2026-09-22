/**
 * 后端统一契约类型（镜像 common/ApiResponse、model/PageResultVO）
 * 契约规则：成功码为 0（非 HTTP 语义码），ID 一律字符串
 */

/** 统一响应结构（镜像 ApiResponse） */
export interface ApiResponse<T> {
  /** 业务状态码：0 成功；4xxxx 客户端错误；5xxxx 系统错误；503xx OCR 服务不可用 */
  code: number
  /** 提示信息 */
  message: string
  /** 业务数据，失败时可能为 null */
  data: T | null
  /** 服务器时间戳（后端 long 毫秒，非 ISO 字符串） */
  timestamp: number
}

/** 分页结果（镜像 PageResultVO） */
export interface PageResult<T> {
  /** 当前页数据 */
  records: T[]
  /** 总条数 */
  total: number
  /** 当前页码 */
  pageNum: number
  /** 每页条数 */
  pageSize: number
}

/** 分页查询参数基类（镜像后端校验规则：pageNum ≥ 1，pageSize 1-100） */
export interface PageQuery {
  /** 页码，从 1 开始 */
  pageNum: number
  /** 每页条数（1-100） */
  pageSize: number
}
