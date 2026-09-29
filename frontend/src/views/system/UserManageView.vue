<script setup lang="ts">
import { computed, onMounted, reactive, ref, shallowRef } from 'vue'
import { storeToRefs } from 'pinia'
import type { FormInstance, FormRules, TreeNodeData } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { fetchAllRoles } from '@/api/role'
import { fetchOrgTree } from '@/api/org'
import {
  fetchUserPage,
  submitUserCreate,
  submitUserOrg,
  submitUserRoles,
  submitUserStatus,
} from '@/api/user'
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
  /** 由左侧机构树点击设置，空串表示不限机构 */
  orgId: '',
  status: undefined as number | undefined,
})

/* ---------------- 左侧机构树 ---------------- */
const orgTree = shallowRef<OrgTreeVO[]>([])
const orgLoading = ref(false)
  /** 机构名称关键词（仅过滤树的展示，不参与接口查询） */
const orgKeyword = ref('')

/** 左侧树展示配置 */
const treeProps = {
  label: 'orgName',
  children: 'children',
}

/** 下拉树形配置（调整机构弹窗使用）：停用机构禁止被选为目标机构 */
const treeSelectProps = {
  label: 'orgName',
  children: 'children',
  disabled: (data: TreeNodeData) => (data as OrgTreeVO).status === 0,
}

/** 关键词过滤机构树：命中节点及其祖先链一并保留 */
function filterOrgNodes(nodes: OrgTreeVO[], keyword: string): OrgTreeVO[] {
  if (!keyword) {
    return nodes
  }
  const matched: OrgTreeVO[] = []
  for (const node of nodes) {
    const children = filterOrgNodes(node.children ?? [], keyword)
    if (node.orgName.includes(keyword) || children.length > 0) {
      matched.push({ ...node, children })
    }
  }
  return matched
}

const filteredOrgTree = computed<OrgTreeVO[]>(() =>
  filterOrgNodes(orgTree.value, orgKeyword.value.trim()),
)

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
  orgLoading.value = true
  try {
    orgTree.value = await fetchOrgTree()
  } finally {
    orgLoading.value = false
  }
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
  orgKeyword.value = ''
  handleSearch()
}

/** 点击机构树节点：以该机构作为筛选条件（后端含本级 + 全部子孙机构） */
function handleOrgSelect(data: TreeNodeData): void {
  filters.orgId = (data as OrgTreeVO).id
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

/** 启用角色列表按需加载（分配角色、新增用户共用） */
async function ensureRolesLoaded(): Promise<void> {
  if (allRoles.value.length > 0) {
    return
  }
  try {
    allRoles.value = await fetchAllRoles()
  } catch {
    // 加载失败已由拦截器统一提示，对话框仍可提交已有选项
  }
}

async function openRoleDialog(row: UserPageVO): Promise<void> {
  roleTarget.value = row
  selectedRoleIds.value = row.roles.map((role) => role.id)
  roleDialogVisible.value = true
  await ensureRolesLoaded()
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

/* ---------------- 新增用户 ---------------- */
const createUserVisible = ref(false)
const createUserFormRef = ref<FormInstance | null>(null)
const createUserSubmitting = ref(false)
const createUserForm = reactive({
  username: '',
  password: '',
  nickname: '',
  email: '',
  orgId: '',
  roleIds: [] as string[],
})

/** 校验规则与后端 UserCreateDTO 保持一致 */
const createUserRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名仅支持字母、数字、下划线', trigger: 'blur' },
    { min: 4, max: 32, message: '用户名长度须为 4~32 位', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 8, max: 20, message: '密码长度须为 8~20 位', trigger: 'blur' },
  ],
  nickname: [{ max: 64, message: '昵称不能超过 64 个字符', trigger: 'blur' }],
  email: [
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
    { max: 128, message: '邮箱不能超过 128 个字符', trigger: 'blur' },
  ],
}

function openCreateUser(): void {
  createUserForm.username = ''
  createUserForm.password = ''
  createUserForm.nickname = ''
  createUserForm.email = ''
  // 预填左侧机构树当前选中的机构，未选中则由后端挂默认机构
  createUserForm.orgId = filters.orgId
  createUserForm.roleIds = []
  createUserVisible.value = true
  void ensureRolesLoaded()
}

async function submitCreateUser(): Promise<void> {
  const valid = await createUserFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  createUserSubmitting.value = true
  try {
    await submitUserCreate({
      username: createUserForm.username,
      password: createUserForm.password,
      nickname: createUserForm.nickname || undefined,
      email: createUserForm.email || undefined,
      orgId: createUserForm.orgId || undefined,
      roleIds: createUserForm.roleIds.length > 0 ? createUserForm.roleIds : undefined,
    })
    ElMessage.success('用户创建成功')
    createUserVisible.value = false
    pageNum.value = 1
    await loadUsers()
  } catch {
    // 业务失败已由拦截器统一提示（如 40020 用户名已存在）
  } finally {
    createUserSubmitting.value = false
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
  <div class="page-layout">
    <aside v-loading="orgLoading" class="org-panel">
      <div class="org-panel-header">
        <span class="panel-title">机构树</span>
        <el-button
          link
          :icon="Refresh"
          aria-label="刷新机构树"
          title="刷新机构树"
          @click="loadOrgTree"
        />
      </div>
      <el-input
        v-model="orgKeyword"
        placeholder="搜索机构名称"
        clearable
        :prefix-icon="Search"
        class="org-search"
      />
      <el-tree
        :data="filteredOrgTree"
        :props="treeProps"
        node-key="id"
        highlight-current
        default-expand-all
        :expand-on-click-node="false"
        :current-node-key="filters.orgId"
        class="org-tree"
        @node-click="handleOrgSelect"
      >
        <template #default="{ data }">
          <span class="org-node">
            <span class="org-node-name">{{ (data as OrgTreeVO).orgName }}</span>
            <el-tag v-if="(data as OrgTreeVO).status === 0" size="small" type="info" effect="plain">
              停用
            </el-tag>
          </span>
        </template>
      </el-tree>
      <el-empty
        v-if="filteredOrgTree.length === 0"
        :description="orgKeyword ? '无匹配机构' : '暂无机构数据'"
        :image-size="60"
      />
    </aside>

    <section v-loading="loading" class="page-card">
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
        <el-button type="primary" :icon="Plus" @click="openCreateUser">新增用户</el-button>
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

      <el-dialog
        v-model="createUserVisible"
        title="新增用户"
        width="520px"
        @closed="createUserFormRef?.resetFields()"
      >
        <el-form
          ref="createUserFormRef"
          :model="createUserForm"
          :rules="createUserRules"
          label-width="88px"
        >
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="createUserForm.username"
              placeholder="登录名，4~32 位字母/数字/下划线"
              maxlength="32"
            />
          </el-form-item>
          <el-form-item label="初始密码" prop="password">
            <el-input
              v-model="createUserForm.password"
              type="password"
              show-password
              autocomplete="new-password"
              placeholder="8~20 位"
              maxlength="20"
            />
          </el-form-item>
          <el-form-item label="昵称" prop="nickname">
            <el-input v-model="createUserForm.nickname" placeholder="缺省取用户名" maxlength="64" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="createUserForm.email" placeholder="可选" maxlength="128" />
          </el-form-item>
          <el-form-item label="所属机构" prop="orgId">
            <el-tree-select
              v-model="createUserForm.orgId"
              :data="orgTree"
              :props="treeSelectProps"
              node-key="id"
              check-strictly
              default-expand-all
              :render-after-expand="false"
              clearable
              style="width: 100%"
              placeholder="缺省挂默认机构（停用机构不可选）"
            />
          </el-form-item>
          <el-form-item label="角色" prop="roleIds">
            <el-select
              v-model="createUserForm.roleIds"
              multiple
              filterable
              clearable
              placeholder="缺省绑定普通用户角色 USER"
              style="width: 100%"
            >
              <el-option
                v-for="role in allRoles"
                :key="role.id"
                :label="`${role.roleName}（${role.roleCode}）`"
                :value="role.id"
              />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="createUserVisible = false">取消</el-button>
          <el-button type="primary" :loading="createUserSubmitting" @click="submitCreateUser">
            确定
          </el-button>
        </template>
      </el-dialog>
    </section>
  </div>
</template>

<style scoped>
.page-layout {
  display: flex;
  gap: 16px;
  align-items: stretch;
  min-height: 100%;
}

.org-panel {
  display: flex;
  flex: 0 0 240px;
  flex-direction: column;
  width: 240px;
  min-height: 100%;
  padding: 16px 12px;
  overflow: hidden;
  background-color: #ffffff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.org-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.org-search {
  margin-bottom: 8px;
}

.org-tree {
  flex: 1;
  min-height: 160px;
  overflow: auto;
}

.org-tree :deep(.el-tree-node__content) {
  height: 32px;
}

.org-node {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  overflow: hidden;
}

.org-node-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.page-card {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
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
