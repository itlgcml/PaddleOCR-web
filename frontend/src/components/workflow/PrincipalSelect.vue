<script setup lang="ts">
import { computed, onMounted } from 'vue'
import {
  principalOption,
  usePrincipalOptions,
  warmPrincipalLabels,
  type PrincipalOption,
  type PrincipalType,
} from '@/composables/usePrincipalOptions'

const props = defineProps<{
  /** USER=指定人员（/api/users/page）；ROLE=指定角色（/api/roles/page） */
  type: PrincipalType
  disabled?: boolean
  placeholder?: string
}>()

const model = defineModel<string[]>({ required: true })

const emit = defineEmits<{
  (e: 'change', value: string[]): void
}>()

const { options, loading, load } = usePrincipalOptions(props.type)

/**
 * 实际渲染项：当前搜索结果 + 已选但不在结果里的项。
 * 多次搜索多次选择时，前几轮选中的项靠缓存补回，标签才不会退化成 id。
 */
const mergedOptions = computed<PrincipalOption[]>(() => {
  const list: PrincipalOption[] = []
  const seen = new Set<string>()
  for (const item of options.value) {
    if (seen.has(item.id)) {
      continue
    }
    seen.add(item.id)
    list.push(item)
  }
  // 已选但不在当前结果里的项排在后段，仅用于让标签正常显示名称
  for (const id of model.value) {
    if (seen.has(id)) {
      continue
    }
    seen.add(id)
    list.push(principalOption(id) ?? { id, label: id, sub: '' })
  }
  return list
})

/** 输入即搜索：只查一个关键词，后端前缀匹配逻辑不变 */
function onSearch(keyword: string): void {
  void load(keyword.trim())
}

/** 展开下拉且尚无结果时先拉首页 */
function onVisibleChange(visible: boolean): void {
  if (visible && options.value.length === 0) {
    void load('')
  }
}

/** 选中项变化时向上透传（如发起人范围需要复检） */
function onChange(value: string[]): void {
  emit('change', value)
}

onMounted(warmPrincipalLabels)
</script>

<template>
  <el-select
    v-model="model"
    multiple
    filterable
    remote
    reserve-keyword
    clearable
    style="width: 100%"
    :remote-method="onSearch"
    :loading="loading"
    :disabled="disabled"
    :placeholder="placeholder"
    @change="onChange"
    @visible-change="onVisibleChange"
  >
    <el-option v-for="opt in mergedOptions" :key="opt.id" :label="opt.label" :value="opt.id">
      <span>{{ opt.label }}</span>
      <span v-if="opt.sub" class="principal-sub">{{ opt.sub }}</span>
    </el-option>
  </el-select>
</template>

<style scoped>
/* 选项右侧的次要信息（用户名 / 角色编码），与名称区分开 */
.principal-sub {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}
</style>
