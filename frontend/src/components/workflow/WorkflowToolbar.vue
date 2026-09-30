<script setup lang="ts">
import { ref } from 'vue'
import { Download, FullScreen, RefreshLeft, Upload } from '@element-plus/icons-vue'

const zoom = defineModel<number>('zoom', { required: true })
const fullscreen = defineModel<boolean>('fullscreen', { default: false })

const emit = defineEmits<{
  (e: 'validate'): void
  (e: 'import', text: string): void
  (e: 'export'): void
  (e: 'reset'): void
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

.wf-toolbar-version {
  margin-left: auto;
  margin-right: -16px;
}
</style>
