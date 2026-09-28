<script setup lang="ts">
import { ref } from 'vue'
import { Download, RefreshLeft, Upload } from '@element-plus/icons-vue'

const flowName = defineModel<string>('flowName', { required: true })
const zoom = defineModel<number>('zoom', { required: true })

const emit = defineEmits<{
  (e: 'save'): void
  (e: 'validate'): void
  (e: 'fit'): void
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
</script>

<template>
  <div class="wf-toolbar">
    <el-input v-model="flowName" class="wf-toolbar-name" maxlength="30" placeholder="流程名称">
      <template #prepend>流程名称</template>
    </el-input>

    <div class="wf-toolbar-group">
      <el-button :icon="RefreshLeft" size="small" @click="zoomOut">-</el-button>
      <span class="wf-toolbar-zoom">{{ Math.round(zoom * 100) }}%</span>
      <el-button size="small" @click="zoomIn">+</el-button>
      <el-button size="small" @click="emit('fit')">适应画布</el-button>
    </div>

    <div class="wf-toolbar-group">
      <el-button size="small" @click="emit('validate')">校验</el-button>
      <el-button size="small" :icon="Upload" @click="pickFile">导入</el-button>
      <el-button size="small" :icon="Download" @click="emit('export')">导出</el-button>
      <el-button size="small" @click="emit('reset')">重置</el-button>
      <el-button size="small" type="primary" @click="emit('save')">保存草稿</el-button>
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

.wf-toolbar-name {
  width: 260px;
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
</style>
