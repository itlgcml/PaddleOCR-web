/**
 * OCR 识别接口（镜像 OcrController：POST /api/ocr/recognize）
 */
import request from './request'
import type { OcrResult } from '@/types/ocr'

/** 上传图片发起识别（multipart 字段名 file；axios 对 FormData 自动设置带 boundary 的 Content-Type） */
export function submitRecognize(file: File): Promise<OcrResult> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<OcrResult, OcrResult>('/ocr/recognize', formData)
}
