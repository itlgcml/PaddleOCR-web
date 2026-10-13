<script setup lang="ts">
import { computed } from 'vue'

/**
 * 表单快照渲染：按 bizType 分支渲染业务表单（详情页展示发起时快照）。
 * 新单据类型在此扩展分支；未知类型按 key-value 兜底展示。
 */
const props = defineProps<{
  bizType: string
  formJson: string | null
}>()

interface LawFirmSelectSnapshot {
  title?: string
  amount?: string
  reason?: string
}

/** 快照解析结果：Record<string, string> 形态的扁平键值 */
const snapshot = computed<Record<string, string> | null>(() => {
  if (!props.formJson) {
    return null
  }
  try {
    const parsed: unknown = JSON.parse(props.formJson)
    if (typeof parsed !== 'object' || parsed === null) {
      return null
    }
    return Object.fromEntries(
      Object.entries(parsed as Record<string, unknown>)
        .filter(([, value]) => value !== null && value !== undefined)
        .map(([key, value]) => [key, String(value)]),
    )
  } catch {
    return null
  }
})

const isLawFirmSelect = computed(() => props.bizType === 'LAW_FIRM_SELECT')

const lawFirmSnapshot = computed<LawFirmSelectSnapshot>(() => {
  const raw = snapshot.value ?? {}
  return { title: raw.title, amount: raw.amount, reason: raw.reason }
})

/** 金额千分位（快照里是字符串数字） */
function formatAmount(value: string | undefined): string {
  if (!value) {
    return '-'
  }
  const num = Number(value)
  return Number.isFinite(num)
    ? num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    : value
}

/** 未知类型的兜底字段名映射：无映射时直接展示英文键 */
const GENERIC_LABELS: Record<string, string> = {
  title: '标题',
  amount: '金额',
  reason: '事由',
}

function labelOf(key: string): string {
  return GENERIC_LABELS[key] ?? key
}

const genericEntries = computed<Array<[string, string]>>(() => Object.entries(snapshot.value ?? {}))
</script>

<template>
  <div class="biz-snapshot">
    <template v-if="isLawFirmSelect && lawFirmSnapshot.title">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="标题">{{ lawFirmSnapshot.title }}</el-descriptions-item>
        <el-descriptions-item label="选聘金额（元）">
          {{ formatAmount(lawFirmSnapshot.amount) }}
        </el-descriptions-item>
        <el-descriptions-item label="选聘原因">
          {{ lawFirmSnapshot.reason || '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </template>

    <el-descriptions v-else-if="genericEntries.length > 0" :column="1" border>
      <el-descriptions-item v-for="[key, value] in genericEntries" :key="key" :label="labelOf(key)">
        {{ value }}
      </el-descriptions-item>
    </el-descriptions>

    <el-empty v-else description="暂无表单快照" :image-size="72" />
    <div class="biz-snapshot-tip">以下为发起时的表单内容快照，单据后续修改不反映在此处</div>
  </div>
</template>

<style scoped>
.biz-snapshot-tip {
  margin-top: 8px;
  font-size: 12px;
  line-height: 20px;
  color: #909399;
}
</style>
