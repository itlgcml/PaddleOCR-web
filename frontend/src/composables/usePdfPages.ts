/**
 * PDF 逐页渲染（pdfjs-dist）：结果页左侧原始文件预览专用。
 * - worker 以 ?url 静态资源方式引入（由 Vite 打包输出，避免 CDN）
 * - renderPageTo 按目标 CSS 像素宽等比渲染，内部乘 devicePixelRatio 提升清晰度
 * - PdfPageBox.baseWidth/baseHeight 为 PDF pt 尺寸（scale=1 视口），供 block_bbox 坐标换算
 */
import * as pdfjsLib from 'pdfjs-dist'
import type { PDFDocumentProxy } from 'pdfjs-dist'
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'

pdfjsLib.GlobalWorkerOptions.workerSrc = workerUrl

/** 单页渲染元信息（baseWidth/baseHeight 即 PDF pt 基准尺寸） */
export interface PdfPageBox {
  pageNumber: number
  baseWidth: number
  baseHeight: number
}

export function usePdfPages() {
  let doc: PDFDocumentProxy | null = null

  /** 加载文档（重复调用会先释放旧文档） */
  async function load(url: string): Promise<PDFDocumentProxy> {
    destroy()
    doc = await pdfjsLib.getDocument({ url }).promise
    return doc
  }

  /** 渲染指定页到 canvas（renderWidth 为目标 CSS 像素宽），返回页面基准尺寸 */
  async function renderPageTo(
    canvas: HTMLCanvasElement,
    pageNumber: number,
    renderWidth: number,
  ): Promise<PdfPageBox> {
    if (!doc) {
      throw new Error('PDF 文档尚未加载')
    }
    const page = await doc.getPage(pageNumber)
    const baseViewport = page.getViewport({ scale: 1 })
    const viewport = page.getViewport({ scale: renderWidth / baseViewport.width })
    const dpr = window.devicePixelRatio || 1
    canvas.width = Math.floor(viewport.width * dpr)
    canvas.height = Math.floor(viewport.height * dpr)
    // width 固定、height auto：配合 CSS max-width 压缩时仍按属性宽高比等比缩放
    canvas.style.width = `${Math.floor(viewport.width)}px`
    canvas.style.height = 'auto'
    const ctx = canvas.getContext('2d')
    if (!ctx) {
      throw new Error('canvas 2d 上下文不可用')
    }
    await page.render({ canvas, canvasContext: ctx, viewport }).promise
    return { pageNumber, baseWidth: baseViewport.width, baseHeight: baseViewport.height }
  }

  /** 释放文档资源（组件卸载时调用） */
  function destroy(): void {
    if (doc) {
      void doc.destroy()
      doc = null
    }
  }

  return { load, renderPageTo, destroy }
}
