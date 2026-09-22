<script setup lang="ts">
import { computed, onMounted, reactive, ref, shallowRef } from 'vue'
import type { FormInstance, FormRules, TreeNodeData } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { fetchOrgTree, submitOrgCreate, submitOrgRemove, submitOrgUpdate } from '@/api/org'
import type { OrgTreeVO } from '@/types/auth'

const loading = ref(false)
const tree = shallowRef<OrgTreeVO[]>([])

const rootOrgId = computed(() => tree.value[0]?.id ?? '')

async function loadTree(): Promise<void> {
  loading.value = true
  try {
    tree.value = await fetchOrgTree()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadTree()
})

/** 父机构下拉树形配置：停用机构禁止被选为新父级 */
const treeSelectProps = {
  label: 'orgName',
  children: 'children',
  disabled: (data: TreeNodeData) => (data as OrgTreeVO).status === 0,
}

/* ---------------- 新增 ---------------- */
const createVisible = ref(false)
const createFormRef = ref<FormInstance | null>(null)
const createForm = reactive({
  parentId: '',
  orgCode: '',
  orgName: '',
  sort: 0,
})

const createRules: FormRules = {
  parentId: [{ required: true, message: '请选择父机构', trigger: 'change' }],
  orgCode: [
    { required: true, message: '请输入机构编码', trigger: 'blur' },
    { pattern: /^[A-Z][A-Z0-9_]*$/, message: '需大写字母开头，仅含大写字母/数字/下划线', trigger: 'blur' },
    { max: 32, message: '机构编码不能超过 32 位', trigger: 'blur' },
  ],
  orgName: [
    { required: true, message: '请输入机构名称', trigger: 'blur' },
    { max: 64, message: '机构名称不能超过 64 个字符', trigger: 'blur' },
  ],
  sort: [{ type: 'number', min: 0, max: 999, message: '排序为 0~999 的数字', trigger: 'blur' }],
}

const submitting = ref(false)

function openCreate(parentId?: string): void {
  createForm.parentId = parentId ?? rootOrgId.value
  createForm.orgCode = ''
  createForm.orgName = ''
  createForm.sort = 0
  createVisible.value = true
}

async function submitCreate(): Promise<void> {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    await submitOrgCreate({
      parentId: createForm.parentId,
      orgCode: createForm.orgCode,
      orgName: createForm.orgName,
      sort: createForm.sort,
    })
    ElMessage.success('机构创建成功')
    createVisible.value = false
    await loadTree()
  } catch {
    // 业务失败已由拦截器统一提示（如 40026 编码重复）
  } finally {
    submitting.value = false
  }
}

/* ---------------- 编辑 ---------------- */
const editVisible = ref(false)
const editFormRef = ref<FormInstance | null>(null)
const editTarget = shallowRef<OrgTreeVO | null>(null)
const editForm = reactive({
  orgName: '',
  sort: 0,
  status: 1,
})

const editRules: FormRules = {
  orgName: [
    { required: true, message: '请输入机构名称', trigger: 'blur' },
    { max: 64, message: '机构名称不能超过 64 个字符', trigger: 'blur' },
  ],
  sort: [{ type: 'number', min: 0, max: 999, message: '排序为 0~999 的数字', trigger: 'blur' }],
}

function openEdit(row: OrgTreeVO): void {
  editTarget.value = row
  editForm.orgName = row.orgName
  editForm.sort = row.sort
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
    await submitOrgUpdate(target.id, {
      orgName: editForm.orgName,
      sort: editForm.sort,
      status: editForm.status,
    })
    ElMessage.success('机构已更新')
    editVisible.value = false
    await loadTree()
  } catch {
    // 业务失败已由拦截器统一提示
  } finally {
    submitting.value = false
  }
}

/* ---------------- 删除 ---------------- */
async function handleRemove(row: OrgTreeVO): Promise<void> {
  const confirmed = await ElMessageBox.confirm(
    `确定删除机构「${row.orgName}」吗？删除后不可恢复。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
  )
    .then(() => true)
    .catch(() => false)
  if (!confirmed) {
    return
  }
  try {
    await submitOrgRemove(row.id)
    ElMessage.success('删除成功')
    await loadTree()
  } catch {
    // 仍有子机构(40027)/在职员工(40028)等失败已由拦截器统一提示
  }
}
</script>

<template>
  <div v-loading="loading" class="page-card">
    <div class="toolbar">
      <div class="toolbar-title">机构列表</div>
      <div class="toolbar-actions">
        <el-button :icon="Refresh" @click="loadTree">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate()">新增机构</el-button>
      </div>
    </div>

    <el-table
      :data="tree"
      row-key="id"
      :tree-props="{ children: 'children' }"
      default-expand-all
      class="table"
    >
      <el-table-column prop="orgName" label="机构名称" min-width="240" />
      <el-table-column prop="orgCode" label="机构编码" width="160" />
      <el-table-column prop="sort" label="排序" width="80" align="center" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openCreate(row.id)">新增下级</el-button>
          <el-button link type="primary" size="small" @click="openEdit(row as OrgTreeVO)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleRemove(row as OrgTreeVO)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无机构数据" />
      </template>
    </el-table>

    <el-dialog
      v-model="createVisible"
      title="新增机构"
      width="480px"
      @closed="createFormRef?.resetFields()"
    >
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="88px">
        <el-form-item label="父机构" prop="parentId">
          <el-tree-select
            v-model="createForm.parentId"
            :data="tree"
            :props="treeSelectProps"
            node-key="id"
            check-strictly
            default-expand-all
            :render-after-expand="false"
            style="width: 100%"
            placeholder="请选择父机构"
          />
        </el-form-item>
        <el-form-item label="机构编码" prop="orgCode">
          <el-input v-model="createForm.orgCode" placeholder="如 DEPT_DEV，建后不可修改" maxlength="32" />
        </el-form-item>
        <el-form-item label="机构名称" prop="orgName">
          <el-input v-model="createForm.orgName" placeholder="请输入机构名称" maxlength="64" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="createForm.sort" :min="0" :max="999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="editVisible"
      title="编辑机构"
      width="480px"
      @closed="editFormRef?.resetFields()"
    >
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="88px">
        <el-form-item label="父机构">
          <el-input :model-value="editTarget?.orgName" disabled />
        </el-form-item>
        <el-form-item label="机构编码">
          <el-input :model-value="editTarget?.orgCode" disabled />
        </el-form-item>
        <el-form-item label="机构名称" prop="orgName">
          <el-input v-model="editForm.orgName" placeholder="请输入机构名称" maxlength="64" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="editForm.sort" :min="0" :max="999" />
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
