/**
 * 文件识别提交流程：上传弹框确认 → 调接口 → 存 store → 跳结果页。
 * 组件保持纯 UI，副作用与竞态集中在此。
 */
import { readonly, ref } from 'vue'
import { useRouter } from 'vue-router'
import { submitOcrRecognize } from '@/api/ocr'
import { useOcrStore } from '@/stores/ocr'
import type { OcrFileType } from '@/types/ocr'

/** 竞态守卫：连续提交仅最后一次生效 */
let requestSeq = 0

export function useOcrRecognize() {
  const router = useRouter()
  const ocrStore = useOcrStore()
  const submitting = ref(false)

  /**
   * 提交识别：成功后写入 store 并跳转结果对照页（SPA 内路由，不开新 tab）。
   * 返回是否成功——业务失败已由 request 拦截器统一 ElMessage，调用方据此决定弹框去留。
   */
  async function submitOcrFile(file: File, fileType: OcrFileType): Promise<boolean> {
    const seq = ++requestSeq
    submitting.value = true
    try {
      const result = await submitOcrRecognize(file, fileType)
      if (seq !== requestSeq) {
        return false
      }
      ocrStore.saveRecognizedFile(file, fileType, result)
      await router.push({ name: 'OcrResult' })
      return true
    } catch {
      // 识别失败：拦截器已统一提示，弹框保持打开供重试/换文件
      return false
    } finally {
      if (seq === requestSeq) {
        submitting.value = false
      }
    }
  }

  return { submitting: readonly(submitting), submitOcrFile }
}
