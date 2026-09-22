<script setup lang="ts">
import { Search } from '@element-plus/icons-vue'

/** 关键词筛选：keyword 双向绑定（父接 store），搜索动作上抛 */
const keyword = defineModel<string>({ default: '' })

defineProps<{
  /** 查询进行中（禁用按钮防重复提交） */
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'search'): void
}>()

/** 重置：清空关键词并立即搜索 */
function handleReset(): void {
  keyword.value = ''
  emit('search')
}
</script>

<template>
  <div class="filter">
    <el-input
      v-model="keyword"
      class="filter-input"
      placeholder="按文件名搜索"
      clearable
      maxlength="255"
      :prefix-icon="Search"
      @keyup.enter="emit('search')"
      @clear="emit('search')"
    />
    <el-button type="primary" :loading="loading" @click="emit('search')">查询</el-button>
    <el-button :disabled="loading" @click="handleReset">重置</el-button>
  </div>
</template>

<style scoped>
.filter {
  display: flex;
  align-items: center;
  gap: 12px;
}

.filter-input {
  width: 280px;
}
</style>
