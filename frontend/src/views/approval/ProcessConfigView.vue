<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Search } from '@element-plus/icons-vue'
import { fetchProcessPage, submitProcessDisable, submitProcessRemove } from '@/api/approval'
import type { ProcessStatus, ProcessVO } from '@/types/approval'

const statusLabelMap: Record<ProcessStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  DISABLED: '已停用',
}

const statusTagMap: Record<ProcessStatus, 'info' | 'success' | 'danger'> = {
  DRAFT: 'info',
  PUBLISHED: 'success',
  DISABLED: 'danger',
}

const router = useRouter()

const list = ref<ProcessVO[]>([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const keyword = ref('')
const statusFilter = ref<ProcessStatus | ''>('')

/** 拉取分页数据（成功分支，失败提示由拦截器统一处理） */
async function loadList(): Promise<void> {
  loading.value = true
  try {
    const result = await fetchProcessPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: keyword.value.trim() || undefined,
      status: statusFilter.value || undefined,
    })
    list.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadList()
})

/** 检索/筛选：重置到第 1 页 */
function handleSearch(): void {
  pageNum.value = 1
  void loadList()
}

function handlePageChange(page: number): void {
  pageNum.value = page
  void loadList()
}

function handleSizeChange(size: number): void {
  pageSize.value = size
  pageNum.value = 1
  void loadList()
}

/** 新增：进入流程配置向导（第 1 步基本信息，第 2 步流程设计） */
function handleCreate(): void {
  void router.push({ name: 'ProcessConfigEdit' })
}

/** 编辑：进入流程配置向导，携带流程 id（详情与草稿由向导页加载） */
function handleEdit(row: ProcessVO): void {
  void router.push({ name: 'ProcessConfigEdit', query: { id: row.id } })
}

/** 停用流程（仅已发布态显示入口，二次确认防误触） */
function handleDisable(row: ProcessVO): void {
  void ElMessageBox.confirm(
    `停用后「${row.name}」将不能发起新的审批，历史单据不受影响。确定停用吗？`,
    '停用流程',
    { type: 'warning', confirmButtonText: '停用', cancelButtonText: '取消' },
  )
    .then(async () => {
      await submitProcessDisable(row.id)
      ElMessage.success(`流程「${row.name}」已停用`)
      void loadList()
    })
    .catch(() => undefined)
}

/** 删除流程（仅草稿态显示入口，二次确认防误触） */
function handleRemove(row: ProcessVO): void {
  void ElMessageBox.confirm(
    `删除后「${row.name}」的配置将不可恢复。确定删除吗？`,
    '删除流程',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
  )
    .then(async () => {
      await submitProcessRemove(row.id)
      ElMessage.success(`流程「${row.name}」已删除`)
      void loadList()
    })
    .catch(() => undefined)
}
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <div class="toolbar-title">流程配置</div>
      <div class="toolbar-actions">
        <el-select v-model="statusFilter" placeholder="状态" clearable class="status-filter" @change="handleSearch">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已停用" value="DISABLED" />
        </el-select>
        <el-input
          v-model="keyword"
          placeholder="按编码/名称搜索"
          clearable
          class="keyword-input"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button type="primary" :icon="Plus" @click="handleCreate">新增流程</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="list" class="table">
      <el-table-column prop="name" label="流程名称" min-width="180" />
      <el-table-column prop="code" label="流程编码" min-width="200" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagMap[row.status as ProcessStatus]" size="small">
            {{ statusLabelMap[row.status as ProcessStatus] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="当前版本" width="100" align="center">
        <template #default="{ row }">
          {{ row.currentVersion ?? '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="180" />
      <el-table-column label="操作" width="200" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button v-if="row.status === 'PUBLISHED'" link type="warning" size="small" @click="handleDisable(row)">停用</el-button>
          <el-button v-if="row.status === 'DRAFT'" link type="danger" size="small" @click="handleRemove(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无流程配置" />
      </template>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="pageNum"
        :page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<style scoped>
.page-card {
  min-height: 100%;
  padding: 20px;
  background-color: #ffffff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.toolbar-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-filter {
  width: 110px;
}

.keyword-input {
  width: 220px;
}

.table {
  width: 100%;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
