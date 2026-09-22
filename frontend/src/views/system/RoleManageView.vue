<script setup lang="ts">
import { onMounted, reactive, ref, shallowRef } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { fetchRolePage, submitRoleCreate, submitRoleRemove, submitRoleUpdate } from '@/api/role'
import type { RoleVO } from '@/types/auth'

const loading = ref(false)
const list = shallowRef<RoleVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const keyword = ref('')

async function loadRoles(): Promise<void> {
  loading.value = true
  try {
    const page = await fetchRolePage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined,
    })
    list.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadRoles()
})

function handleSearch(): void {
  pageNum.value = 1
  void loadRoles()
}

function changePage(page: number): void {
  pageNum.value = page
  void loadRoles()
}

function changePageSize(size: number): void {
  pageSize.value = size
  pageNum.value = 1
  void loadRoles()
}

/* ---------------- 新增 ---------------- */
const createVisible = ref(false)
const createFormRef = ref<FormInstance | null>(null)
const createForm = reactive({
  roleCode: '',
  roleName: '',
  description: '',
})

const createRules: FormRules = {
  roleCode: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { pattern: /^[A-Z][A-Z0-9_]*$/, message: '需大写字母开头，仅含大写字母/数字/下划线', trigger: 'blur' },
    { min: 4, max: 64, message: '角色编码长度为 4~64 位', trigger: 'blur' },
  ],
  roleName: [
    { required: true, message: '请输入角色名称', trigger: 'blur' },
    { max: 64, message: '角色名称不能超过 64 个字符', trigger: 'blur' },
  ],
  description: [{ max: 200, message: '描述不能超过 200 个字符', trigger: 'blur' }],
}

const submitting = ref(false)

function openCreate(): void {
  createForm.roleCode = ''
  createForm.roleName = ''
  createForm.description = ''
  createVisible.value = true
}

async function submitCreate(): Promise<void> {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    await submitRoleCreate({
      roleCode: createForm.roleCode,
      roleName: createForm.roleName,
      description: createForm.description || undefined,
    })
    ElMessage.success('角色创建成功')
    createVisible.value = false
    await loadRoles()
  } catch {
    // 业务失败已由拦截器统一提示（如 40029 编码已存在）
  } finally {
    submitting.value = false
  }
}

/* ---------------- 编辑 ---------------- */
const editVisible = ref(false)
const editFormRef = ref<FormInstance | null>(null)
const editTarget = shallowRef<RoleVO | null>(null)
const editForm = reactive({
  roleName: '',
  description: '',
  status: 1,
})

const editRules: FormRules = {
  roleName: [
    { required: true, message: '请输入角色名称', trigger: 'blur' },
    { max: 64, message: '角色名称不能超过 64 个字符', trigger: 'blur' },
  ],
  description: [{ max: 200, message: '描述不能超过 200 个字符', trigger: 'blur' }],
}

function openEdit(row: RoleVO): void {
  editTarget.value = row
  editForm.roleName = row.roleName
  editForm.description = row.description ?? ''
  editForm.status = row.status
  editVisible.value = true
}

async function submitEdit(): Promise<void> {
  const target = editTarget.value
  if (!target) {
    return
  }
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    await submitRoleUpdate(target.id, {
      roleName: editForm.roleName,
      description: editForm.description || undefined,
      status: editForm.status,
    })
    ElMessage.success('角色已更新')
    editVisible.value = false
    await loadRoles()
  } catch {
    // 业务失败已由拦截器统一提示（内置角色 40031）
  } finally {
    submitting.value = false
  }
}

/* ---------------- 删除 ---------------- */
async function handleRemove(row: RoleVO): Promise<void> {
  const confirmed = await ElMessageBox.confirm(
    `确定删除角色「${row.roleName}」吗？删除后不可恢复。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
  )
    .then(() => true)
    .catch(() => false)
  if (!confirmed) {
    return
  }
  try {
    await submitRoleRemove(row.id)
    ElMessage.success('删除成功')
    // 当前页删空自动回退一页
    if (list.value.length === 1 && pageNum.value > 1) {
      pageNum.value -= 1
    }
    await loadRoles()
  } catch {
    // 被绑定(40030)/内置角色(40031)等失败已由拦截器统一提示
  }
}
</script>

<template>
  <div v-loading="loading" class="page-card">
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="按角色编码 / 名称搜索"
        clearable
        class="search-input"
        :prefix-icon="Search"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <div class="toolbar-actions">
        <el-button :icon="Refresh" @click="loadRoles">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增角色</el-button>
      </div>
    </div>

    <el-table :data="list" class="table">
      <el-table-column prop="roleCode" label="角色编码" width="170">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ row.roleCode }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="roleName" label="角色名称" min-width="140" />
      <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.description ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="内置" width="80" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.builtIn === 1" size="small" type="warning">内置</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="130" align="center">
        <template #default="{ row }">
          <template v-if="row.builtIn !== 1">
            <el-button link type="primary" size="small" @click="openEdit(row as RoleVO)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleRemove(row as RoleVO)">删除</el-button>
          </template>
          <span v-else class="builtin-hint">不可操作</span>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无角色数据" />
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

    <el-dialog
      v-model="createVisible"
      title="新增角色"
      width="480px"
      @closed="createFormRef?.resetFields()"
    >
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="88px">
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="createForm.roleCode" placeholder="如 AUDITOR，建后不可修改" maxlength="64" />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="createForm.roleName" placeholder="请输入角色名称" maxlength="64" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入角色描述（可选）"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="editVisible"
      title="编辑角色"
      width="480px"
      @closed="editFormRef?.resetFields()"
    >
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="88px">
        <el-form-item label="角色编码">
          <el-input :model-value="editTarget?.roleCode" disabled />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="editForm.roleName" placeholder="请输入角色名称" maxlength="64" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="editForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入角色描述（可选）"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="editForm.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
            inline-prompt
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitEdit">确定</el-button>
      </template>
    </el-dialog>
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

.builtin-hint {
  font-size: 12px;
  color: #c0c4cc;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
