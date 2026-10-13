<script setup lang="ts">
import { onMounted, reactive, ref, shallowRef } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { fetchMyInstancePage, submitInstanceCancel } from '@/api/approval-runtime'
import type { InstanceItemVO } from '@/types/approval'

/** 我发起的：实例分页（状态过滤），RUNNING 可撤销（输入理由） */
const router = useRouter()

const loading = ref(false)
const records = shallowRef<InstanceItemVO[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, status: '', keyword: '' })

const STATUS_OPTIONS = [
  { value: 'RUNNING', label: '进行中' },
  { value: 'COMPLETED', label: '已通过' },
  { value: 'REJECTED', label: '已拒绝' },
  { value: 'CANCELED', label: '已撤销' },
]

const STATUS_META: Record<string, { label: string; type: 'primary' | 'success' | 'danger' | 'info' }> = {
  RUNNING: { label: '进行中', type: 'primary' },
  COMPLETED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已拒绝', type: 'danger' },
  CANCELED: { label: '已撤销', type: 'info' },
}

function statusMeta(status: string) {
  return STATUS_META[status] ?? { label: status, type: 'info' as const }
}

onMounted(() => {
  void load()
})

async function load(): Promise<void> {
  if (loading.value) {
    return
  }
  loading.value = true
  try {
    const page = await fetchMyInstancePage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      status: query.status || undefined,
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
  query.status = ''
  query.keyword = ''
  onSearch()
}

function goDetail(row: InstanceItemVO): void {
  void router.push(`/approval/detail/${row.id}`)
}

async function onCancel(row: InstanceItemVO): Promise<void> {
  try {
    const { value } = await ElMessageBox.prompt(
      `撤销「${row.bizTitle}」的审批流程？撤销后不可恢复，待办将全部取消。`,
      '撤销流程',
      {
        confirmButtonText: '确认撤销',
        cancelButtonText: '取消',
        inputPlaceholder: '请输入撤销理由（必填）',
        inputValidator: (input: string) => (input && input.trim().length > 0 ? true : '撤销理由不能为空'),
        type: 'warning',
      },
    )
    await submitInstanceCancel(row.id, value.trim())
    ElMessage.success('流程已撤销')
    await load()
  } catch {
    // 用户取消或提交失败（失败提示由拦截器统一处理）
  }
}
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <el-form inline @submit.prevent="onSearch">
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable class="toolbar-select">
            <el-option v-for="item in STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
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
      <el-table-column prop="processName" label="流程名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="bizTitle" label="业务标题" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusMeta(row.status).type" size="small" effect="light">
            {{ statusMeta(row.status).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="当前审批人" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.status === 'RUNNING' && row.currentApproverNames.length > 0 ? row.currentApproverNames.join('、') : '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="startTime" label="发起时间" width="170" />
      <el-table-column prop="finishTime" label="结束时间" width="170">
        <template #default="{ row }">{{ row.finishTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="goDetail(row)">详情</el-button>
          <el-button v-if="row.status === 'RUNNING'" type="danger" link @click="onCancel(row)">撤销</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无发起记录" :image-size="72" />
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

.toolbar-select {
  width: 130px;
}

.toolbar-input {
  width: 220px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
