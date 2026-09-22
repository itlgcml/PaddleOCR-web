<script setup lang="ts">
import { computed, onMounted, reactive, ref, shallowRef } from 'vue'
import { storeToRefs } from 'pinia'
import type { FormInstance, FormRules, TreeNodeData } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { fetchAllRoles } from '@/api/role'
import { fetchOrgTree } from '@/api/org'
import { fetchUserPage, submitUserOrg, submitUserRoles, submitUserStatus } from '@/api/user'
import type { OrgTreeVO, RoleVO, UserPageVO } from '@/types/auth'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const { user: currentUser } = storeToRefs(auth)

/* ---------------- 筛选与分页 ---------------- */
const loading = ref(false)
const list = shallowRef<UserPageVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const filters = reactive({
  username: '',
  orgId: '',
  status: undefined as number | undefined,
})

const orgTree = shallowRef<OrgTreeVO[]>([])
const treeSelectProps = {
  label: 'orgName',
  children: 'children',
  disabled: (data: TreeNodeData) => (data as OrgTreeVO).status === 0,
}

const statusParam = computed(() => (typeof filters.status === 'number' ? filters.status : undefined))

async function loadUsers(): Promise<void> {
  loading.value = true
  try {
    const page = await fetchUserPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      username: filters.username || undefined,
      orgId: filters.orgId || undefined,
      status: statusParam.value,
    })
    list.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

async function loadOrgTree(): Promise<void> {
  orgTree.value = await fetchOrgTree()
}

onMounted(() => {
  void loadUsers()
  void loadOrgTree()
})

function handleSearch(): void {
  pageNum.value = 1
  void loadUsers()
}

function handleReset(): void {
  filters.username = ''
  filters.orgId = ''
  filters.status = undefined
  handleSearch()
}

function changePage(page: number): void {
  pageNum.value = page
  void loadUsers()
}

function changePageSize(size: number): void {
  pageSize.value = size
  pageNum.value = 1
  void loadUsers()
}

function isSelf(row: UserPageVO): boolean {
  return row.id === currentUser.value?.id
}

/* ---------------- 调整机构 ---------------- */
const orgDialogVisible = ref(false)
const orgFormRef = ref<FormInstance | null>(null)
const orgTarget = shallowRef<UserPageVO | null>(null)
const orgForm = reactive({ orgId: '' })
const orgSubmitting = ref(false)

const orgRules: FormRules = {
  orgId: [{ required: true, message: '请选择所属机构', trigger: 'change' }],
}

function openOrgDialog(row: UserPageVO): void {
  orgTarget.value = row
  orgForm.orgId = row.org?.id ?? ''
  orgDialogVisible.value = true
}

async function submitOrgAssign(): Promise<void> {
  const target = orgTarget.value
  if (!target) {
    return
  }
  const valid = await orgFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  orgSubmitting.value = true
  try {
    await submitUserOrg(target.id, { orgId: orgForm.orgId })
    ElMessage.success('机构调整成功')
    orgDialogVisible.value = false
    await loadUsers()
  } catch {
    // 业务失败已由拦截器统一提示
  } finally {
    orgSubmitting.value = false
  }
}

/* ---------------- 分配角色 ---------------- */
const roleDialogVisible = ref(false)
const roleTarget = shallowRef<UserPageVO | null>(null)
const selectedRoleIds = ref<string[]>([])
const roleSubmitting = ref(false)
const allRoles = shallowRef<RoleVO[]>([])

/** 下拉选项 = 启用角色 ∪ 用户已有角色（保证已选但被停用的角色也能正常回显） */
const roleSelectOptions = computed<RoleVO[]>(() => {
  const map = new Map<string, RoleVO>()
  for (const role of allRoles.value) {
    map.set(role.id, role)
  }
  for (const brief of roleTarget.value?.roles ?? []) {
    if (!map.has(brief.id)) {
      map.set(brief.id, {
        id: brief.id,
        roleCode: brief.roleCode,
        roleName: brief.roleName,
        description: null,
        status: 0,
        builtIn: 0,
        createTime: '',
      })
    }
  }
  return [...map.values()]
})

async function openRoleDialog(row: UserPageVO): Promise<void> {
  roleTarget.value = row
  selectedRoleIds.value = row.roles.map((role) => role.id)
  roleDialogVisible.value = true
  if (allRoles.value.length === 0) {
    try {
      allRoles.value = await fetchAllRoles()
    } catch {
      // 加载失败已由拦截器统一提示，对话框仍可提交已有选项
    }
  }
}

async function submitRoleAssign(): Promise<void> {
  const target = roleTarget.value
  if (!target) {
    return
  }
  roleSubmitting.value = true
  try {
    await submitUserRoles(target.id, { roleIds: selectedRoleIds.value })
    ElMessage.success('角色分配成功')
    roleDialogVisible.value = false
    await loadUsers()
  } catch {
    // 不允许操作自己(40300)等已由拦截器统一提示
  } finally {
    roleSubmitting.value = false
  }
}

/* ---------------- 启用/禁用 ---------------- */
async function toggleStatus(row: UserPageVO): Promise<void> {
  const disabling = row.status === 1
  const confirmed = await ElMessageBox.confirm(
    `确定${disabling ? '禁用' : '启用'}用户「${row.username}」吗？`,
    '操作确认',
    { type: 'warning', confirmButtonText: disabling ? '禁用' : '启用', cancelButtonText: '取消' },
  )
    .then(() => true)
    .catch(() => false)
  if (!confirmed) {
    return
  }
  try {
    await submitUserStatus(row.id, { status: disabling ? 0 : 1 })
    ElMessage.success(disabling ? '已禁用' : '已启用')
    await loadUsers()
  } catch {
    // 不允许禁用自己(40300)等已由拦截器统一提示
  }
}
</script>

<template>
  <div v-loading="loading" class="page-card">
    <div class="toolbar">
      <div class="filters">
        <el-input
          v-model="filters.username"
          placeholder="用户名 / 昵称"
          clearable
          class="filter-username"
          :prefix-icon="Search"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-tree-select
          v-model="filters.orgId"
          :data="orgTree"
          :props="treeSelectProps"
          node-key="id"
          check-strictly
          default-expand-all
          :render-after-expand="false"
          clearable
          class="filter-org"
          placeholder="所属机构"
          @change="handleSearch"
        />
        <el-select
          v-model="filters.status"
          placeholder="状态"
          clearable
          class="filter-status"
          @change="handleSearch"
        >
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
      <el-button :icon="Refresh" @click="loadUsers">刷新</el-button>
    </div>

    <el-table :data="list" class="table">
      <el-table-column prop="username" label="用户名" min-width="130">
        <template #default="{ row }">
          <span class="username">{{ row.username }}</span>
          <el-tag v-if="isSelf(row as UserPageVO)" size="small" type="primary" class="self-tag">我</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="nickname" label="昵称" min-width="110">
        <template #default="{ row }">{{ row.nickname ?? '—' }}</template>
      </el-table-column>
      <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.email ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="所属机构" min-width="130">
        <template #default="{ row }">{{ row.org?.orgName ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="角色" min-width="150">
        <template #default="{ row }">
          <template v-if="row.roles.length > 0">
            <el-tag v-for="role in row.roles" :key="role.id" size="small" effect="plain" class="role-tag">
              {{ role.roleName }}
            </el-tag>
          </template>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最近登录" width="170">
        <template #default="{ row }">{{ row.lastLoginTime ?? '—' }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="240" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openOrgDialog(row as UserPageVO)">调整机构</el-button>
          <template v-if="!isSelf(row as UserPageVO)">
            <el-button link type="primary" size="small" @click="openRoleDialog(row as UserPageVO)">分配角色</el-button>
            <el-button link :type="row.status === 1 ? 'danger' : 'success'" size="small" @click="toggleStatus(row as UserPageVO)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
          </template>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无用户数据" />
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
      v-model="orgDialogVisible"
      :title="`调整机构 - ${orgTarget?.username ?? ''}`"
      width="480px"
      @closed="orgFormRef?.resetFields()"
    >
      <el-form ref="orgFormRef" :model="orgForm" :rules="orgRules" label-width="88px">
        <el-form-item label="所属机构" prop="orgId">
          <el-tree-select
            v-model="orgForm.orgId"
            :data="orgTree"
            :props="treeSelectProps"
            node-key="id"
            check-strictly
            default-expand-all
            :render-after-expand="false"
            style="width: 100%"
            placeholder="请选择所属机构（停用机构不可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="orgDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="orgSubmitting" @click="submitOrgAssign">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="roleDialogVisible"
      :title="`分配角色 - ${roleTarget?.username ?? ''}`"
      width="480px"
    >
      <el-form label-width="88px">
        <el-form-item label="当前机构">
          <el-input :model-value="roleTarget?.org?.orgName ?? '—'" disabled />
        </el-form-item>
        <el-form-item label="角色">
          <el-select
            v-model="selectedRoleIds"
            multiple
            filterable
            placeholder="请选择角色（多选，全量替换）"
            style="width: 100%"
          >
            <el-option
              v-for="role in roleSelectOptions"
              :key="role.id"
              :label="`${role.roleName}（${role.roleCode}）`"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleSubmitting" @click="submitRoleAssign">确定</el-button>
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

.filters {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.filter-username {
  width: 200px;
}

.filter-org {
  width: 200px;
}

.filter-status {
  width: 110px;
}

.table {
  width: 100%;
}

.username {
  font-weight: 500;
  color: #303133;
}

.self-tag {
  margin-left: 6px;
}

.role-tag {
  margin-right: 4px;
  margin-bottom: 2px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
