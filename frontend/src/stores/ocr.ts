/**
 * 文件识别状态：跨页共享（首页上传 → 结果对照页），Pinia setup store。
 * 结果大对象用 shallowRef 整体替换；原始文件以 objectURL 持有供结果页预览（替换/清空时释放）。
 */
import { ref, shallowRef } from 'vue'
import { defineStore } from 'pinia'
import type { OcrFileType, OcrRecognizeResult } from '@/types/ocr'

export const useOcrStore = defineStore('ocr', () => {
  const fileName = ref('')
  const fileType = ref<OcrFileType>(0)
  /** 原始文件预览地址（blob:，随识别结果同生命周期） */
  const fileUrl = ref('')
  const result = shallowRef<OcrRecognizeResult | null>(null)

  /** 释放当前 objectURL（重新识别/清空时调用，防内存泄漏） */
  function revokeFileUrl(): void {
    if (fileUrl.value) {
      URL.revokeObjectURL(fileUrl.value)
    }
  }

  /** 识别成功：保存文件信息（objectURL 供结果页预览原始文件）与识别结果 */
  function saveRecognizedFile(file: File, type: OcrFileType, res: OcrRecognizeResult): void {
    revokeFileUrl()
    fileName.value = file.name
    fileType.value = type
    fileUrl.value = URL.createObjectURL(file)
    result.value = res
  }

  /** 清空识别态（结果页无数据兜底时调用） */
  function clearRecognizedFile(): void {
    revokeFileUrl()
    fileName.value = ''
    fileType.value = 0
    fileUrl.value = ''
    result.value = null
  }

  return { fileName, fileType, fileUrl, result, saveRecognizedFile, clearRecognizedFile }
})
