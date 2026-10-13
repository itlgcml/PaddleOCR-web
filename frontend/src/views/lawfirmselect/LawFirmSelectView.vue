<script setup lang="ts">
import { onMounted, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { fetchLawFirmSelectPage, submitLawFirmSelectRemove } from '@/api/lawfirmselect'
import { fetchBizApprovalStatus } from '@/api/approval-runtime'
import type { BizApprovalStatusVO } from '@/types/approval'
import StartApprovalDialog from '@/views/approval/components/StartApprovalDialog.vue'
import type { LawFirmSelectVO } from '@/types/lawfirmselect'

const router = useRouter()

const loading = ref(false)
const list = shallowRef<LawFirmSelectVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const titleQuery = ref('')

/** bizId → 审批状态（RUNNING 时编辑/删除禁用，与后端 40060 拦截双保险） */
const approvalStatusMap = shallowRef<Record<string, BizApprovalStatusVO>>({})

/** 发起审批弹窗目标 */
const startVisible = ref(false)
const startTarget = shallowRef<LawFirmSelectVO | null>(null)

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
    approvalStatusMap.value = {}
    void loadApprovalStatus()
  } finally {
    loading.value = false
  }
}

/* ---------------- 审批状态 / 提交审批 ---------------- */

function approvalOf(row: LawFirmSelectVO): BizApprovalStatusVO | undefined {
  return approvalStatusMap.value[row.id]
}

/** 审批中单据禁止编辑/删除（后端 40060 兜底） */
function isRunning(row: LawFirmSelectVO): boolean {
  return approvalOf(row)?.status === 'RUNNING'
}

/** 审批状态标签（无实例时 undefined → 显示"未提交"） */
const APPROVAL_TAG_META: Record<string, { label: string; type: 'primary' | 'success' | 'danger' | 'info' }> = {
  RUNNING: { label: '审批中', type: 'primary' },
  COMPLETED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已拒绝', type: 'danger' },
  CANCELED: { label: '已撤销', type: 'info' },
}

function approvalTag(row: LawFirmSelectVO): { label: string; type: 'primary' | 'success' | 'danger' | 'info' } | undefined {
  return APPROVAL_TAG_META[approvalOf(row)?.status ?? '']
}

/** 后端 bizIds 单次上限 20，分批拉取当页单据审批状态 */
async function loadApprovalStatus(): Promise<void> {
  const ids = list.value.map((row) => row.id)
  if (ids.length === 0) {
    return
  }
  const merged: Record<string, BizApprovalStatusVO> = {}
  try {
    for (let start = 0; start < ids.length; start += 20) {
      const chunk = ids.slice(start, start + 20)
      const statusMap = await fetchBizApprovalStatus('LAW_FIRM_SELECT', chunk)
      Object.assign(merged, statusMap)
    }
    approvalStatusMap.value = merged
  } catch {
    // 失败提示由拦截器统一处理，状态列降级为"未提交"
  }
}

function onSubmitApproval(row: LawFirmSelectVO): void {
  startTarget.value = row
  startVisible.value = true
}

/** 发起成功后轻量刷新审批状态列（不整页刷新） */
function onApprovalStarted(): void {
  void loadApprovalStatus()
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
      <el-table-column label="审批状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag v-if="approvalTag(row as LawFirmSelectVO)" size="small" effect="light" :type="approvalTag(row as LawFirmSelectVO)!.type">
            {{ approvalTag(row as LawFirmSelectVO)!.label }}
          </el-tag>
          <el-tag v-else size="small" type="info" effect="plain">未提交</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="190" align="center">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            size="small"
            :disabled="isRunning(row as LawFirmSelectVO)"
            @click="openEdit(row as LawFirmSelectVO)"
          >
            编辑
          </el-button>
          <el-button
            link
            type="danger"
            size="small"
            :disabled="isRunning(row as LawFirmSelectVO)"
            @click="handleRemove(row as LawFirmSelectVO)"
          >
            删除
          </el-button>
          <el-button
            link
            type="primary"
            size="small"
            :disabled="isRunning(row as LawFirmSelectVO)"
            @click="onSubmitApproval(row as LawFirmSelectVO)"
          >
            提交审批
          </el-button>
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

    <StartApprovalDialog
      v-model="startVisible"
      biz-type="LAW_FIRM_SELECT"
      :biz-id="startTarget?.id ?? ''"
      :biz-title="startTarget?.title"
      @started="onApprovalStarted"
    />
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
