<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import type { CSSProperties } from 'vue'
import { ArrowLeft, DocumentCopy, RefreshRight } from '@element-plus/icons-vue'
import { useOcrStore } from '@/stores/ocr'
import { usePdfPages } from '@/composables/usePdfPages'
import type { PdfPageBox } from '@/composables/usePdfPages'
import type { OcrParsingBlock } from '@/types/ocr'

/** 高亮坐标基准（block_bbox 所在坐标系的像素尺寸） */
interface PageBasis {
  width: number
  height: number
}

interface ActiveBlockRef {
  pageIndex: number
  blockId: number
}

const router = useRouter()
const ocrStore = useOcrStore()
const { fileName, fileType, fileUrl, result } = storeToRefs(ocrStore)
const { load, renderPageTo, destroy } = usePdfPages()

// ---------- 状态 ----------
/** 图像输入：原图固有像素尺寸（即 block_bbox 坐标基准） */
const imageEl = ref<HTMLImageElement | null>(null)
const imageSize = shallowRef<PageBasis | null>(null)
/** PDF 输入：总页数与逐页基准尺寸（PDF pt） */
const numPages = ref(0)
const pdfBoxes = shallowRef<PdfPageBox[]>([])
const rendering = ref(false)
const activeBlock = ref<ActiveBlockRef | null>(null)
const canvasMap = new Map<number, HTMLCanvasElement>()

// ---------- 派生 ----------
const pages = computed(() => result.value?.layoutParsingResults ?? [])

/** 预览页数：PDF = 总页数，图像 = 1 */
const previewCount = computed(() => (fileType.value === 0 ? numPages.value : fileUrl.value ? 1 : 0))

const totalBlocks = computed(() =>
  pages.value.reduce((sum, page) => sum + (page.prunedResult?.parsing_res_list?.length ?? 0), 0),
)

// ---------- 方法 ----------
function blocksOf(pageIndex: number): OcrParsingBlock[] {
  return pages.value[pageIndex]?.prunedResult?.parsing_res_list ?? []
}

function isBlockActive(pageIndex: number, blockId: number): boolean {
  return activeBlock.value?.pageIndex === pageIndex && activeBlock.value.blockId === blockId
}

function displayContent(block: OcrParsingBlock): string {
  const content = block.block_content?.trim()
  return content || '（无文本内容）'
}

/**
 * 坐标基准（block_bbox 的像素坐标系）：
 * - PDF：上游将每页栅格化后识别，坐标按 144dpi（PDF pt × 2）换算；若上游改渲染 dpi，此处系数需同步
 * - 图像：上游直接用原图像素，坐标 = 原图固有尺寸
 */
function resolveBasis(pageIndex: number): PageBasis | null {
  if (fileType.value === 0) {
    const box = pdfBoxes.value[pageIndex]
    return box ? { width: box.baseWidth * 2, height: box.baseHeight * 2 } : null
  }
  return imageSize.value
}

/** 高亮多边形定位：包围盒样式（供滚动定位）+ SVG polygon 坐标（viewBox 以包围盒为用户坐标系） */
interface PolygonHighlight {
  boxStyle: CSSProperties
  viewBox: string
  points: string
}

/**
 * 高亮多边形：优先 block_polygon_points 四点定位（贴合倾斜/旋转区域，更精确），
 * 缺失时回退 block_bbox 矩形四点；坐标按包围盒百分比定位，由 SVG 拉伸至预览尺寸
 */
function polygonHighlight(pageIndex: number): PolygonHighlight | null {
  if (!activeBlock.value || activeBlock.value.pageIndex !== pageIndex) {
    return null
  }
  const block = blocksOf(pageIndex).find(b => b.block_id === activeBlock.value?.blockId)
  const polygon = block?.block_polygon_points
  const bbox = block?.block_bbox
  const corners =
    polygon && polygon.length >= 3
      ? polygon
      : bbox && bbox.length === 4
        ? [
            [bbox[0], bbox[1]],
            [bbox[2], bbox[1]],
            [bbox[2], bbox[3]],
            [bbox[0], bbox[3]],
          ]
        : null
  const basis = resolveBasis(pageIndex)
  if (!corners || !basis) {
    return null
  }
  const xs = corners.map(([x]) => x)
  const ys = corners.map(([, y]) => y)
  const minX = Math.min(...xs)
  const minY = Math.min(...ys)
  const width = Math.max(...xs) - minX
  const height = Math.max(...ys) - minY
  if (width <= 0 || height <= 0) {
    return null
  }
  return {
    boxStyle: {
      left: `${(minX / basis.width) * 100}%`,
      top: `${(minY / basis.height) * 100}%`,
      width: `${(width / basis.width) * 100}%`,
      height: `${(height / basis.height) * 100}%`,
    },
    viewBox: `${minX} ${minY} ${width} ${height}`,
    points: corners.map(([x, y]) => `${x},${y}`).join(' '),
  }
}

function highlightDomId(pageIndex: number): string {
  return `ocr-highlight-${pageIndex}`
}

/** 点击内容块：定位到对应页与位置并红色高亮 */
function locateBlock(pageIndex: number, blockId: number): void {
  activeBlock.value = { pageIndex, blockId }
  void nextTick(() => {
    document
      .getElementById(highlightDomId(pageIndex))
      ?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  })
}

/** 复制全文（各页 markdown 拼接） */
async function copyAllText(): Promise<void> {
  const text = pages.value
    .map(page => page.markdown?.text ?? '')
    .filter(Boolean)
    .join('\n\n')
  if (!text) {
    ElMessage.warning('暂无可复制的文本内容')
    return
  }
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制全文')
  } catch {
    ElMessage.error('复制失败，请手动选择内容复制')
  }
}

function backToHome(): void {
  void router.push({ name: 'Home' })
}

/** 原图加载完成：记录固有像素尺寸作为 block_bbox 坐标基准 */
function onImageLoad(): void {
  const el = imageEl.value
  if (el && el.naturalWidth > 0) {
    imageSize.value = { width: el.naturalWidth, height: el.naturalHeight }
  }
}

function setCanvasRef(pageNumber: number, el: unknown): void {
  if (el instanceof HTMLCanvasElement) {
    canvasMap.set(pageNumber, el)
  } else {
    canvasMap.delete(pageNumber)
  }
}

/** PDF 逐页渲染（按 PDF 原始尺寸显示，不随面板宽度缩放） */
async function renderPdfPages(): Promise<void> {
  rendering.value = true
  try {
    await nextTick()
    const boxes: PdfPageBox[] = []
    for (let pageNumber = 1; pageNumber <= numPages.value; pageNumber++) {
      const canvas = canvasMap.get(pageNumber)
      if (!canvas) {
        continue
      }
      boxes.push(await renderPageTo(canvas, pageNumber))
      pdfBoxes.value = [...boxes]
    }
  } finally {
    rendering.value = false
  }
}

// ---------- 生命周期 ----------
onMounted(async () => {
  if (!result.value || !fileUrl.value) {
    ElMessage.warning('请先上传文件进行识别')
    await router.replace({ name: 'Home' })
    return
  }
  if (fileType.value === 0) {
    const doc = await load(fileUrl.value)
    numPages.value = doc.numPages
    await renderPdfPages()
  }
})

onBeforeUnmount(() => {
  void destroy()
})
</script>

<template>
  <div class="ocr-result">
    <header class="result-header">
      <div class="file-info">
        <span class="file-name" :title="fileName">{{ fileName }}</span>
        <el-tag size="small" type="info">{{ fileType === 0 ? 'PDF' : '图像' }}</el-tag>
        <span class="file-meta">共 {{ pages.length }} 页 · {{ totalBlocks }} 个内容块</span>
      </div>
      <div class="header-actions">
        <el-button :icon="DocumentCopy" @click="copyAllText">复制全文</el-button>
        <el-button :icon="RefreshRight" @click="backToHome">重新识别</el-button>
        <el-button :icon="ArrowLeft" type="primary" plain @click="backToHome">返回首页</el-button>
      </div>
    </header>

    <div class="workspace">
      <!-- 左侧：原始文件预览（PDF 逐页 / 单图），多页上下滚动 -->
      <section v-loading="rendering" class="preview-panel" aria-label="文件预览">
        <el-empty v-if="previewCount === 0" description="暂无预览" />
        <template v-else-if="fileType === 0">
          <div
            v-for="pageNumber in numPages"
            :key="pageNumber"
            class="page-wrap"
          >
            <div class="page-image-wrap">
              <canvas :ref="el => setCanvasRef(pageNumber, el)" class="page-canvas" />
              <div class="highlight-layer">
                <div
                  v-if="polygonHighlight(pageNumber - 1)"
                  :id="highlightDomId(pageNumber - 1)"
                  :style="polygonHighlight(pageNumber - 1)?.boxStyle"
                  class="highlight-box"
                >
                  <svg
                    :viewBox="polygonHighlight(pageNumber - 1)?.viewBox"
                    preserveAspectRatio="none"
                    class="highlight-svg"
                    aria-hidden="true"
                  >
                    <polygon
                      :points="polygonHighlight(pageNumber - 1)?.points ?? ''"
                      class="highlight-polygon"
                    />
                  </svg>
                </div>
              </div>
            </div>
            <div class="page-number">{{ pageNumber }} / {{ numPages }}</div>
          </div>
        </template>
        <template v-else>
          <div class="page-wrap">
            <div class="page-image-wrap">
              <img
                ref="imageEl"
                :src="fileUrl"
                class="page-image"
                alt="识别原图预览"
                @load="onImageLoad"
              />
              <div class="highlight-layer">
                <div
                  v-if="polygonHighlight(0)"
                  :id="highlightDomId(0)"
                  :style="polygonHighlight(0)?.boxStyle"
                  class="highlight-box"
                >
                  <svg
                    :viewBox="polygonHighlight(0)?.viewBox"
                    preserveAspectRatio="none"
                    class="highlight-svg"
                    aria-hidden="true"
                  >
                    <polygon :points="polygonHighlight(0)?.points ?? ''" class="highlight-polygon" />
                  </svg>
                </div>
              </div>
            </div>
          </div>
        </template>
      </section>

      <!-- 中间：识别内容（按页分组、阅读顺序排列，点击定位） -->
      <section class="content-panel" aria-label="识别内容">
        <el-empty v-if="pages.length === 0" description="暂无识别结果" />
        <div
          v-for="(_, pageIndex) in pages"
          :key="pageIndex"
          class="page-section"
        >
          <div class="page-section-title">
            第 {{ pageIndex + 1 }} 页
            <span class="page-section-count">{{ blocksOf(pageIndex).length }} 个内容块</span>
          </div>
          <el-empty
            v-if="blocksOf(pageIndex).length === 0"
            description="本页无识别内容"
            :image-size="60"
          />
          <div
            v-for="block in blocksOf(pageIndex)"
            :key="`${pageIndex}-${block.block_id}`"
            class="block-item"
            :class="{ 'block-active': isBlockActive(pageIndex, block.block_id) }"
            @click="locateBlock(pageIndex, block.block_id)"
          >
            <div class="block-meta">
              <el-tag size="small" :type="block.block_label === 'table' ? 'warning' : 'info'">
                {{ block.block_label }}
              </el-tag>
              <span class="block-order">#{{ block.block_order ?? block.block_id }}</span>
            </div>
            <div class="block-content">{{ displayContent(block) }}</div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.ocr-result {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 100px);
}

.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 16px;
  margin-bottom: 12px;
  background-color: #ffffff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.file-info {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.file-name {
  overflow: hidden;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  flex-shrink: 0;
  font-size: 13px;
  color: #909399;
}

.header-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.workspace {
  display: flex;
  flex: 1;
  gap: 12px;
  min-height: 0;
}

.preview-panel {
  flex: 0 0 46%;
  min-width: 0;
  padding: 16px;
  overflow: auto;
  background-color: #ffffff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.page-wrap {
  position: relative;
  width: fit-content;
  margin: 0 auto;
}

.page-wrap + .page-wrap {
  margin-top: 16px;
}

/* 预览容器：尺寸贴合渲染页，供 block_bbox 百分比换算的高亮层定位 */
.page-image-wrap {
  position: relative;
  display: inline-block;
  max-width: 100%;
  line-height: 0;
}

.page-canvas,
.page-image {
  display: block;
  background-color: #f5f7fa;
  border-radius: 4px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
}

/* PDF 按原始尺寸显示（内联 width/height 固定 pt 值），禁止 max-width 压缩导致拉伸变形 */
.page-canvas {
  max-width: none;
}

/* 图像输入保留等比收敛，超大图不撑破面板 */
.page-image {
  max-width: 100%;
  height: auto;
}

.highlight-layer {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  pointer-events: none;
}

/* 红色半透明多边形高亮（点击内容块按 block_polygon_points 四点定位）：
   外层盒定位在多边形包围盒（供滚动定位），内层 SVG 按包围盒 viewBox 拉伸绘制多边形 */
.highlight-box {
  position: absolute;
}

.highlight-svg {
  display: block;
  width: 100%;
  height: 100%;
  overflow: visible;
}

.highlight-polygon {
  fill: rgba(245, 63, 63, 0.32);
  stroke: #f56c6c;
  stroke-width: 2;
  vector-effect: non-scaling-stroke;
}

.page-number {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
  text-align: center;
}

.content-panel {
  flex: 1;
  min-width: 0;
  padding: 16px;
  overflow-y: auto;
  background-color: #ffffff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.page-section + .page-section {
  margin-top: 20px;
}

.page-section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 8px;
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  border-bottom: 1px solid #ebeef5;
}

.page-section-count {
  font-size: 12px;
  font-weight: 400;
  color: #909399;
}

.block-item {
  padding: 10px 12px;
  margin-bottom: 8px;
  cursor: pointer;
  background-color: #fafbfc;
  border: 1px solid transparent;
  border-radius: 6px;
  transition: border-color 0.15s, background-color 0.15s;
}

.block-item:hover {
  border-color: #1e6fff;
}

.block-item.block-active {
  background-color: rgba(245, 63, 63, 0.06);
  border-color: #f56c6c;
}

.block-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.block-order {
  font-size: 12px;
  color: #909399;
}

/* OCR 文本按原样换行展示（表格 HTML 按纯文本呈现，禁 v-html） */
.block-content {
  font-size: 13px;
  line-height: 1.7;
  color: #303133;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
