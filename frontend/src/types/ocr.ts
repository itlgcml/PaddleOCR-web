/**
 * OCR 业务类型（镜像 model/OcrResultVO、TextBoxVO、PointVO、OcrRecordVO）
 * ID 一律 string：后端 Long 经 ToStringSerializer 序列化
 */
import type { PageQuery } from './api'

/** 检测框顶点（归一化坐标 0-1，相对原图宽高比例） */
export interface Point {
  x: number
  y: number
}

/** OCR 检测框（镜像 TextBoxVO） */
export interface TextBox {
  /** 识别文本 */
  text: string
  /** 置信度 0-1 */
  confidence: number
  /** 框顶点（归一化坐标） */
  points: Point[]
}

/** OCR 识别结果（镜像 OcrResultVO） */
export interface OcrResult {
  /** 识别全文（多行以 \n 分隔） */
  fullText: string
  /** 检测框列表 */
  textBoxes: TextBox[]
  /** 识别耗时（毫秒） */
  costMs: number
  /** 识别记录 ID（雪花 ID 字符串，禁止 number） */
  recordId: string
}

/** 历史识别记录（镜像 OcrRecordVO） */
export interface OcrRecord {
  /** 记录 ID（雪花 ID 字符串） */
  id: string
  /** 上传文件名 */
  fileName: string
  /** 识别文本全文 */
  resultText: string
  /** 识别耗时（毫秒） */
  costMs: number
  /** 识别时间（yyyy-MM-dd HH:mm:ss，后端 JacksonConfig 全局格式） */
  createTime: string
}

/** 历史记录查询参数 */
export interface RecordQuery extends PageQuery {
  /** 文件名关键词（可选，最长 255 字符） */
  keyword?: string
}
