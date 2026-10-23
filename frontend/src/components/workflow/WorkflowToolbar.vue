<script setup lang="ts">
import { ref } from 'vue'
import { Download, FullScreen, RefreshLeft, Upload } from '@element-plus/icons-vue'

const zoom = defineModel<number>('zoom', { required: true })
const fullscreen = defineModel<boolean>('fullscreen', { default: false })

defineProps<{
  /** 视图面包屑（主流程名 → 逐级子流程名）；长度 ≤ 1 时不展示 */
  crumbs?: string[]
}>()

const emit = defineEmits<{
  (e: 'validate'): void
  (e: 'import', text: string): void
  (e: 'export'): void
  (e: 'reset'): void
  /** 跳转到指定视图层：0=主流程 */
  (e: 'navigate', level: number): void
}>()

const fileInput = ref<HTMLInputElement | null>(null)

function pickFile(): void {
  fileInput.value?.click()
}

async function onFileChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) {
    emit('import', await file.text())
  }
  input.value = ''
}

function zoomIn(): void {
  zoom.value = Math.min(2, zoom.value + 0.1)
}

function zoomOut(): void {
  zoom.value = Math.max(0.5, zoom.value - 0.1)
}

function resetZoom(): void {
  zoom.value = 1
}
</script>

<template>
  <div class="wf-toolbar">
    <!-- 视图面包屑：主流程 / 子流程名 / …（钻入子流程后出现，点击回到对应层级） -->
    <div v-if="crumbs && crumbs.length > 1" class="wf-toolbar-group wf-toolbar-crumbs">
      <!-- 层级链顺序固定且可能同名，key 用「层序 + 名称」组合保证唯一 -->
      <template v-for="(crumb, index) in crumbs" :key="`${index}-${crumb}`">
        <button
          class="wf-toolbar-crumb"
          :class="{ 'is-current': index === crumbs.length - 1 }"
          type="button"
          :disabled="index === crumbs.length - 1"
          @click="emit('navigate', index)"
        >
          {{ crumb }}
        </button>
        <span v-if="index < crumbs.length - 1" class="wf-toolbar-crumb-sep">/</span>
      </template>
    </div>
    <span v-if="crumbs && crumbs.length > 1" class="wf-toolbar-sep" />

    <div class="wf-toolbar-group">
      <div class="wf-toolbar-zoom-group">
        <el-button size="small" @click="zoomOut">-</el-button>
        <span class="wf-toolbar-zoom">{{ Math.round(zoom * 100) }}%</span>
        <el-button size="small" @click="zoomIn">+</el-button>
        <el-button :icon="RefreshLeft" size="small" title="重置缩放" @click="resetZoom" />
      </div>
      <span class="wf-toolbar-sep" />
      <el-button size="small" :icon="FullScreen" @click="fullscreen = !fullscreen">
        {{ fullscreen ? '退出全屏' : '全屏' }}
      </el-button>
    </div>

    <div class="wf-toolbar-group">
      <el-button size="small" @click="emit('validate')">校验</el-button>
      <el-button size="small" :icon="Upload" @click="pickFile">导入</el-button>
      <el-button size="small" :icon="Download" @click="emit('export')">导出</el-button>
      <el-button size="small" @click="emit('reset')">重置</el-button>
    </div>

    <!-- 工具栏右侧扩展位：由调用方传入（流程配置向导传入「版本视图」切换器；空插槽时不占位） -->
    <div v-if="$slots.version" class="wf-toolbar-group wf-toolbar-version">
      <slot name="version" />
    </div>

    <input ref="fileInput" type="file" accept=".json,application/json" hidden @change="onFileChange" />
  </div>
</template>

<style scoped>
.wf-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  padding: 10px 16px;
  background: #ffffff;
  border-bottom: 1px solid #e4e7ed;
}

.wf-toolbar-group {
  display: flex;
  gap: 8px;
  align-items: center;
}

.wf-toolbar-zoom {
  min-width: 42px;
  font-size: 12px;
  color: #606266;
  text-align: center;
}

.wf-toolbar-zoom-group {
  display: flex;
  gap: 4px;
  align-items: center;
}

.wf-toolbar-sep {
  width: 1px;
  height: 16px;
  background: #dcdfe6;
}

.wf-toolbar-crumbs {
  max-width: 40%;
}

.wf-toolbar-crumb {
  max-width: 140px;
  padding: 0;
  overflow: hidden;
  font-size: 13px;
  color: #409eff;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  background: none;
  border: none;
}

.wf-toolbar-crumb.is-current,
.wf-toolbar-crumb:disabled {
  font-weight: 600;
  color: #606266;
  cursor: default;
}

.wf-toolbar-crumb-sep {
  color: #c0c4cc;
}

.wf-toolbar-version {
  margin-left: auto;
  margin-right: -16px;
}
</style>
