<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'

type ProcessStatus = 'draft' | 'published' | 'disabled'

const statusLabelMap: Record<ProcessStatus, string> = {
  draft: '草稿',
  published: '已发布',
  disabled: '已停用',
}

const statusTagMap: Record<ProcessStatus, 'info' | 'success' | 'danger'> = {
  draft: 'info',
  published: 'success',
  disabled: 'danger',
}

interface ProcessConfigItem {
  id: string
  name: string
  code: string
  status: ProcessStatus
  updatedAt: string
}

const router = useRouter()

/** 前端写死的样例数据 */
const list = ref<ProcessConfigItem[]>([
  { id: '1', name: '请假审批', code: 'LEAVE_APPROVAL', status: 'published', updatedAt: '2026-09-20 10:30' },
  { id: '2', name: '报销审批', code: 'EXPENSE_APPROVAL', status: 'draft', updatedAt: '2026-09-18 14:20' },
  { id: '3', name: '采购审批', code: 'PURCHASE_APPROVAL', status: 'disabled', updatedAt: '2026-09-15 09:05' },
])

/** 新增：进入流程配置向导（第 1 步基本信息，第 2 步流程设计） */
function handleCreate(): void {
  void router.push({ name: 'ProcessConfigEdit' })
}

/** 编辑：进入流程配置向导，并携带流程标识预填基本信息 */
function handleEdit(row: ProcessConfigItem): void {
  void router.push({
    name: 'ProcessConfigEdit',
    query: { id: row.id, name: row.name, code: row.code },
  })
}

/** 停用流程 */
function handleDisable(row: ProcessConfigItem): void {
  row.status = 'disabled'
  ElMessage.success(`流程「${row.name}」已停用`)
}
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <div class="toolbar-title">流程配置</div>
      <div class="toolbar-actions">
        <el-button type="primary" :icon="Plus" @click="handleCreate">新增流程</el-button>
      </div>
    </div>

    <el-table :data="list" class="table">
      <el-table-column prop="name" label="流程名称" min-width="180" />
      <el-table-column prop="code" label="流程编码" min-width="200" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagMap[row.status as ProcessStatus]" size="small">
            {{ statusLabelMap[row.status as ProcessStatus] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="180" />
      <el-table-column label="操作" width="160" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button v-if="row.status === 'published'" link type="warning" size="small" @click="handleDisable(row)">停用</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无流程配置" />
      </template>
    </el-table>
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
  gap: 4px;
}

.table {
  width: 100%;
}
</style>
