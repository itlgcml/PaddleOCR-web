<script setup lang="ts">
import { onMounted, reactive, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { fetchDonePage } from '@/api/approval-runtime'
import type { TaskVO } from '@/types/approval'

/** 我的已办：本人主动办理过的任务（PASSED/REJECTED/TRANSFERRED），支持动作过滤 */
const router = useRouter()

const loading = ref(false)
const records = shallowRef<TaskVO[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', action: '' })

const ACTION_OPTIONS = [
  { value: 'PASS', label: '通过' },
  { value: 'REJECT', label: '拒绝' },
  { value: 'RETURN', label: '退回' },
  { value: 'TRANSFER', label: '转办' },
  { value: 'DELEGATE', label: '委派' },
  { value: 'ADD_SIGN', label: '加签' },
  { value: 'COMMENT', label: '评论' },
  { value: 'COPY', label: '抄送' },
  { value: 'SUBMIT', label: '重新提交' },
]

const ACTION_LABELS: Record<string, string> = Object.fromEntries(
  ACTION_OPTIONS.map((option) => [option.value, option.label]),
)

onMounted(() => {
  void load()
})

async function load(): Promise<void> {
  if (loading.value) {
    return
  }
  loading.value = true
  try {
    const page = await fetchDonePage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      keyword: query.keyword.trim() || undefined,
      action: query.action || undefined,
    })
    records.value = page.records
    total.value = page.total
  } catch {
    // 失败提示由拦截器统一处理
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  query.pageNum = 1
  void load()
}

function onReset(): void {
  query.keyword = ''
  query.action = ''
  onSearch()
}

function goDetail(row: TaskVO): void {
  void router.push(`/approval/detail/${row.instanceId}`)
}
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <el-form inline @submit.prevent="onSearch">
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            placeholder="流程名称 / 业务标题"
            clearable
            class="toolbar-input"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item label="办理动作">
          <el-select v-model="query.action" placeholder="全部" clearable class="toolbar-select">
            <el-option v-for="item in ACTION_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table v-loading="loading" :data="records" border>
      <el-table-column prop="processName" label="流程名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="bizTitle" label="业务标题" min-width="180" show-overflow-tooltip />
      <el-table-column prop="nodeName" label="办理节点" min-width="120" show-overflow-tooltip />
      <el-table-column prop="startUserName" label="发起人" width="110" />
      <el-table-column label="我的动作" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.action === 'REJECT'" size="small" type="danger" effect="plain">拒绝</el-tag>
          <el-tag v-else-if="row.action === 'RETURN'" size="small" type="warning" effect="plain">退回</el-tag>
          <el-tag v-else-if="row.action && ACTION_LABELS[row.action]" size="small" effect="plain">
            {{ ACTION_LABELS[row.action] }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="comment" label="审批意见" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.comment || '-' }}</template>
      </el-table-column>
      <el-table-column prop="finishTime" label="办理时间" width="170" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="goDetail(row)">查看</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无已办任务" :image-size="72" />
      </template>
    </el-table>

    <div class="pagination-row">
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="load"
        @size-change="onSearch"
      />
    </div>
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 4px;
}

.toolbar-input {
  width: 220px;
}

.toolbar-select {
  width: 140px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
