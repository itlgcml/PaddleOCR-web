/**
 * OCR 识别逻辑：提交识别、结果状态、预览图 objectURL 生命周期
 * 组件保持纯 UI，全部副作用集中于此
 */
import { onScopeDispose, readonly, shallowRef } from 'vue'
import { submitRecognize } from '@/api/ocr'
import type { OcrResult } from '@/types/ocr'

export function useOcrRecognize() {
  /** 识别进行中 */
  const loading = shallowRef(false)
  /** 最近一次识别结果（整体替换，不深层修改） */
  const result = shallowRef<OcrResult | null>(null)
  /** 预览图地址（objectURL，由本 composable 独家管理） */
  const previewUrl = shallowRef('')
  /** 预览图文件名 */
  const previewName = shallowRef('')

  let objectUrl = ''
  /** 竞态守卫：连续上传多张图时，仅最后一次请求的结果生效 */
  let requestSeq = 0

  function revokeObjectUrl(): void {
    if (objectUrl) {
      URL.revokeObjectURL(objectUrl)
      objectUrl = ''
    }
  }

  /** 更新预览图（先释放旧 objectURL 再创建新的） */
  function setPreview(file: File): void {
    revokeObjectUrl()
    objectUrl = URL.createObjectURL(file)
    previewUrl.value = objectUrl
    previewName.value = file.name
  }

  /** 发起识别：更新预览 → 调接口 → 落结果 */
  async function recognize(file: File): Promise<void> {
    const seq = ++requestSeq
    setPreview(file)
    loading.value = true
    try {
      const res = await submitRecognize(file)
      if (seq !== requestSeq) {
        return
      }
      result.value = res
    } catch {
      // 拦截器已统一 ElMessage 提示；清空旧结果避免与当前预览图文错配
      if (seq === requestSeq) {
        result.value = null
      }
    } finally {
      if (seq === requestSeq) {
        loading.value = false
      }
    }
  }

  /** 重置：清空结果与预览，并使在途请求的结果作废 */
  function reset(): void {
    requestSeq++
    revokeObjectUrl()
    previewUrl.value = ''
    previewName.value = ''
    result.value = null
    loading.value = false
  }

  // 组件作用域销毁时释放 objectURL，防止内存泄漏
  onScopeDispose(revokeObjectUrl)

  return {
    loading: readonly(loading),
    result: readonly(result),
    previewUrl: readonly(previewUrl),
    previewName: readonly(previewName),
    recognize,
    reset,
  }
}
