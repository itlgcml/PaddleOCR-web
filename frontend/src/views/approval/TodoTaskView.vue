<script setup lang="ts">
import { onMounted, reactive, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { fetchTodoPage } from '@/api/approval-runtime'
import type { TaskVO } from '@/types/approval'

/** 我的待办：PENDING 任务分页（含加签/转办/委派派生任务），办理跳详情页办理视角 */
const router = useRouter()

const loading = ref(false)
const records = shallowRef<TaskVO[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '' })

onMounted(() => {
  void load()
})

async function load(): Promise<void> {
  if (loading.value) {
    return
  }
  loading.value = true
  try {
    const page = await fetchTodoPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      keyword: query.keyword.trim() || undefined,
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
  onSearch()
}

function goHandle(row: TaskVO): void {
  void router.push(`/approval/detail/${row.instanceId}?taskId=${row.id}`)
}

const SIGN_LABELS: Record<string, string> = {
  TRANSFER: '转办',
  DELEGATE: '委派',
  BEFORE_SIGN: '前加签',
  AFTER_SIGN: '后加签',
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
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table v-loading="loading" :data="records" border>
      <el-table-column label="流程名称" min-width="160">
        <template #default="{ row }">
          <span class="process-cell">{{ row.processName }}</span>
          <el-tag v-if="row.subProcessName" size="small" type="info" effect="plain">子流程</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="bizTitle" label="业务标题" min-width="180" show-overflow-tooltip />
      <el-table-column prop="nodeName" label="当前节点" min-width="120" show-overflow-tooltip />
      <el-table-column prop="startUserName" label="发起人" width="110" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag v-if="SIGN_LABELS[row.signType]" size="small" type="warning" effect="plain">
            {{ SIGN_LABELS[row.signType] }}
          </el-tag>
          <el-tag v-else-if="row.multiMode === 'AND'" size="small" type="danger" effect="plain">会签</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="到达时间" width="170" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="goHandle(row)">办理</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无待办任务" :image-size="72" />
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
  width: 240px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.process-cell {
  display: inline-block;
  max-width: 160px;
  margin-right: 4px;
  overflow: hidden;
  vertical-align: middle;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
