<script setup lang="ts">
import type { DeepReadonly } from 'vue'
import type { TextBox } from '@/types/ocr'

defineProps<{
  /** 预览图地址（objectURL） */
  src: string
  /** 预览图文件名（alt 兜底文案） */
  fileName?: string
  /** 检测框列表（归一化坐标 0-1；来自 composable 的 DeepReadonly 状态） */
  textBoxes: readonly DeepReadonly<TextBox>[]
  /** 识别进行中（叠加 loading 遮罩） */
  loading?: boolean
}>()

/** 归一化坐标 → SVG points 字符串（viewBox 0-1 直接映射，保留 4 位小数） */
function toPoints(box: DeepReadonly<TextBox>): string {
  return box.points.map((p) => `${p.x.toFixed(4)},${p.y.toFixed(4)}`).join(' ')
}

/** 置信度 0-1 → 百分比文案 */
function toPercent(confidence: number): string {
  return `${(confidence * 100).toFixed(1)}%`
}
</script>

<template>
  <div class="viewer">
    <el-empty v-if="!src" description="上传图片后在此预览" />
    <div v-else v-loading="loading" class="viewer-canvas">
      <img class="viewer-image" :src="src" :alt="fileName ?? '预览图'" />
      <!-- viewBox 0-1 + preserveAspectRatio=none：归一化坐标按容器百分比铺开，随图片缩放自适应 -->
      <svg
        v-if="textBoxes.length > 0"
        class="viewer-overlay"
        viewBox="0 0 1 1"
        preserveAspectRatio="none"
        aria-hidden="true"
      >
        <polygon
          v-for="(box, index) in textBoxes"
          :key="index"
          class="viewer-box"
          :points="toPoints(box)"
        >
          <title>{{ box.text }}（置信度 {{ toPercent(box.confidence) }}）</title>
        </polygon>
      </svg>
    </div>
  </div>
</template>

<style scoped>
.viewer-canvas {
  position: relative;
  display: inline-block;
  max-width: 100%;
  line-height: 0;
}

.viewer-image {
  display: block;
  max-width: 100%;
}

.viewer-overlay {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.viewer-box {
  fill: rgba(64, 158, 255, 0.12);
  stroke: var(--el-color-primary);
  stroke-width: 1.5;
  /* 线宽不随 viewBox 拉伸缩放，恒为屏幕像素 */
  vector-effect: non-scaling-stroke;
  pointer-events: visiblePainted;
  cursor: help;
}
</style>
