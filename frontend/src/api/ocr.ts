/**
 * 文件识别接口：form-data 传输 file + fileType（0=PDF，1=图像）。
 * 注意：OCR 长推理需覆写实例默认 60s 超时（与后端 ocr.service.timeout-ms 对齐为 600s）。
 */
import request from '@/api/request'
import type { OcrFileType, OcrRecognizeResult } from '@/types/ocr'

/** OCR 识别单请求超时（毫秒），对齐后端响应超时配置 */
const OCR_RECOGNIZE_TIMEOUT_MS = 600_000

export function submitOcrRecognize(file: File, fileType: OcrFileType): Promise<OcrRecognizeResult> {
  const form = new FormData()
  form.append('file', file)
  form.append('fileType', String(fileType))
  return request.post<unknown, OcrRecognizeResult>('/ocr/recognize', form, {
    timeout: OCR_RECOGNIZE_TIMEOUT_MS,
  })
}
