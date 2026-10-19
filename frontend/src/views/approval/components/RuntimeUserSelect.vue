<script setup lang="ts">
import { ref, shallowRef, watch } from 'vue'
import { useDebounceFn } from '@vueuse/core'
import { fetchPrincipalUsers } from '@/api/approval-runtime'
import type { UserBriefVO } from '@/types/approval'

/**
 * 运行态用户选择（远程搜索，登录即可的数据源）：
 * 用于发起时选抄送人等场景；选中项本地缓存合并进选项，避免翻页后回显丢失。
 */
const props = defineProps<{
  placeholder?: string
  disabled?: boolean
}>()

const model = defineModel<string[]>({ default: () => [] })

const options = shallowRef<UserBriefVO[]>([])
const loading = ref(false)

/** 已选中但可能不在当前搜索结果中的用户（userId → UserBriefVO） */
const selectedCache = shallowRef(new Map<string, UserBriefVO>())

/** 请求序号：丢弃迟到响应 */
let lastSeq = 0

async function search(keyword: string): Promise<void> {
  const seq = lastSeq + 1
  lastSeq = seq
  loading.value = true
  try {
    const users = await fetchPrincipalUsers(keyword, 50)
    if (seq !== lastSeq) {
      return
    }
    options.value = users
  } catch {
    // 失败提示由拦截器统一处理
  } finally {
    if (seq === lastSeq) {
      loading.value = false
    }
  }
}

const debouncedSearch = useDebounceFn(search, 300)

/** 首次聚焦且尚无选项时预拉一页，避免打开下拉空白（模板中 ref 已自动解包，不能写 options.value） */
function onFocus(): void {
  if (options.value.length === 0) {
    void search('')
  }
}

/** 合并缓存与搜索结果（缓存优先级低，仅用于补回显项） */
function mergedOptions(): Array<{ userId: string; name: string }> {
  const map = new Map<string, UserBriefVO>()
  for (const option of options.value) {
    map.set(option.userId, option)
  }
  for (const [userId, cached] of selectedCache.value) {
    if (!map.has(userId)) {
      map.set(userId, cached)
    }
  }
  return [...map.values()]
}

/** 选中/取消选中时维护回显缓存 */
watch(
  model,
  (ids) => {
    const next = new Map(selectedCache.value)
    for (const option of options.value) {
      if (ids.includes(option.userId)) {
        next.set(option.userId, option)
      }
    }
    selectedCache.value = next
  },
  { deep: true },
)

search('')
</script>

<template>
  <el-select
    v-model="model"
    :placeholder="props.placeholder ?? '搜索用户名或昵称'"
    :disabled="props.disabled"
    :loading="loading"
    multiple
    filterable
    remote
    :remote-method="debouncedSearch"
    collapse-tags
    collapse-tags-tooltip
    class="runtime-user-select"
    @focus="onFocus"
  >
    <el-option
      v-for="option in mergedOptions()"
      :key="option.userId"
      :label="option.name"
      :value="option.userId"
    />
  </el-select>
</template>

<style scoped>
.runtime-user-select {
  width: 100%;
}
</style>
