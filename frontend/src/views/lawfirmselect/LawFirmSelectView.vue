<script setup lang="ts">
import { onMounted, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { fetchLawFirmSelectPage, submitLawFirmSelectRemove } from '@/api/lawfirmselect'
import type { LawFirmSelectVO } from '@/types/lawfirmselect'

const router = useRouter()

const loading = ref(false)
const list = shallowRef<LawFirmSelectVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const titleQuery = ref('')

/** 选聘金额展示：千位分隔 + 固定两位小数 */
function formatAmount(amount: number): string {
  return amount.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 序号跨页连续 */
function indexMethod(index: number): number {
  return (pageNum.value - 1) * pageSize.value + index + 1
}

async function loadRecords(): Promise<void> {
  loading.value = true
  try {
    const page = await fetchLawFirmSelectPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      title: titleQuery.value || undefined,
    })
    list.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadRecords()
})

function handleSearch(): void {
  pageNum.value = 1
  void loadRecords()
}

function changePage(page: number): void {
  pageNum.value = page
  void loadRecords()
}

function changePageSize(size: number): void {
  pageSize.value = size
  pageNum.value = 1
  void loadRecords()
}

/* ---------------- 新增 / 编辑（跳独立页面） ---------------- */
function goCreate(): void {
  void router.push({ name: 'LawFirmSelectCreate' })
}

/** 编辑：跳独立编辑页，携带记录 id（详情由编辑页加载） */
function openEdit(row: LawFirmSelectVO): void {
  void router.push({ name: 'LawFirmSelectEdit', query: { id: row.id } })
}

/* ---------------- 删除 ---------------- */
async function handleRemove(row: LawFirmSelectVO): Promise<void> {
  const confirmed = await ElMessageBox.confirm(
    `确定删除「${row.title}」吗？删除后不可恢复。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
  )
    .then(() => true)
    .catch(() => false)
  if (!confirmed) {
    return
  }
  try {
    await submitLawFirmSelectRemove(row.id)
    ElMessage.success('删除成功')
    // 当前页删空自动回退一页
    if (list.value.length === 1 && pageNum.value > 1) {
      pageNum.value -= 1
    }
    await loadRecords()
  } catch {
    // 业务失败已由拦截器统一提示
  }
}
</script>

<template>
  <div v-loading="loading" class="page-card">
    <div class="toolbar">
      <el-input
        v-model="titleQuery"
        placeholder="按标题搜索"
        clearable
        class="search-input"
        :prefix-icon="Search"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <div class="toolbar-actions">
        <el-button :icon="Refresh" @click="loadRecords">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="goCreate">新增选聘</el-button>
      </div>
    </div>

    <el-table :data="list" class="table">
      <el-table-column type="index" label="序号" width="70" align="center" :index="indexMethod" />
      <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
      <el-table-column label="选聘金额（元）" width="150" align="right">
        <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
      </el-table-column>
      <el-table-column prop="reason" label="选聘原因" min-width="220" show-overflow-tooltip />
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="130" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row as LawFirmSelectVO)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleRemove(row as LawFirmSelectVO)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无律所选聘数据" />
      </template>
    </el-table>

    <div class="pagination">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :current-page="pageNum"
        :page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        @current-change="changePage"
        @size-change="changePageSize"
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
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.search-input {
  width: 280px;
}

.toolbar-actions {
  display: flex;
  gap: 4px;
}

.table {
  width: 100%;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
