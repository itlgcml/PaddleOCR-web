<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import ApprovalStatusBar from './components/ApprovalStatusBar.vue'
import BizFormSnapshot from './components/BizFormSnapshot.vue'
import InstanceTimeline from './components/InstanceTimeline.vue'
import {
  fetchInstanceDetail,
  fetchTaskDetail,
  submitCcRead,
  submitInstanceCancel,
  submitTaskAction,
  type TaskActionPayload,
} from '@/api/approval-runtime'
import type { InstanceDetailVO, TaskDetailVO } from '@/types/approval'

/**
 * 实例详情页（四视角合一）：
 * - 发起人视角：表单快照 + 时间线 + 撤销
 * - 办理视角（query.taskId 或 myPendingTaskId 自动切换）：动作按钮栏
 * - 抄送视角（query.ccId）：进入即自动标记已读
 * - 查看视角：只读
 */
const route = useRoute()
const router = useRouter()

const instanceId = computed(() => String(route.params.instanceId ?? ''))
const routeTaskId = computed(() => {
  const taskId = route.query.taskId
  return typeof taskId === 'string' && taskId.length > 0 ? taskId : null
})
const routeCcId = computed(() => {
  const ccId = route.query.ccId
  return typeof ccId === 'string' && ccId.length > 0 ? ccId : null
})

const loading = ref(false)
const submitting = ref(false)
/** 办理视角数据（query.taskId 或 myPendingTaskId 自动加载） */
const taskDetail = shallowRef<TaskDetailVO | null>(null)
/** 基础详情（办理视角时取 taskDetail，否则实例详情） */
const detail = shallowRef<InstanceDetailVO | null>(null)

const barRef = ref<InstanceType<typeof ApprovalStatusBar>>()

/** 办理视角生效条件：任务可操作（assignee=当前用户且 PENDING） */
const actionableTask = computed(() => (taskDetail.value?.actionable ? taskDetail.value : null))

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
  void loadAll()
})

async function loadAll(): Promise<void> {
  if (!instanceId.value || loading.value) {
    return
  }
  loading.value = true
  try {
    const instance = await fetchInstanceDetail(instanceId.value)
    detail.value = instance
    const targetTaskId = routeTaskId.value ?? instance.myPendingTaskId
    if (targetTaskId) {
      try {
        taskDetail.value = await fetchTaskDetail(targetTaskId)
        detail.value = taskDetail.value
      } catch {
        // 任务不存在或无权限（如他人任务）：回退查看视角
        taskDetail.value = null
      }
    }
    if (routeCcId.value) {
      // 抄送进入即阅（幂等，失败不阻断查看）
      submitCcRead(routeCcId.value).catch(() => undefined)
    }
  } catch {
    // 失败提示由拦截器统一处理（含 40059 无权限）
  } finally {
    loading.value = false
  }
}

async function onActionSubmit(payload: TaskActionPayload): Promise<void> {
  const task = taskDetail.value
  if (!task || submitting.value) {
    return
  }
  submitting.value = true
  try {
    await submitTaskAction(task.task.id, payload)
    if (payload.action === 'REDUCE_SIGN') {
      // 减签：保持弹窗打开，刷新后同意弹窗内提示列表同步更新，可继续办理
      ElMessage.success('已删除该加签任务')
      await loadAll()
    } else {
      ElMessage.success('操作成功')
      barRef.value?.close()
      await loadAll()
    }
  } catch {
    // 失败提示由拦截器统一处理，弹窗保持打开允许重试
  } finally {
    submitting.value = false
  }
}

async function onCancelInstance(comment: string): Promise<void> {
  if (!detail.value || submitting.value) {
    return
  }
  submitting.value = true
  try {
    await submitInstanceCancel(detail.value.id, comment)
    ElMessage.success('流程已撤销')
    await loadAll()
  } catch {
    // 失败提示由拦截器统一处理
  } finally {
    submitting.value = false
  }
}

function goBack(): void {
  if (window.history.length > 1) {
    router.back()
  } else {
    void router.push('/approval/todo')
  }
}

/** 跳转到指定实例详情（子流程实例 / 父流程实例共用） */
function goInstance(id: string): void {
  void router.push({ name: 'ApprovalInstanceDetail', params: { instanceId: id } })
}

/** 返回父流程实例详情 */
function goParent(): void {
  if (detail.value?.parentInstanceId) {
    goInstance(detail.value.parentInstanceId)
  }
}
</script>

<template>
  <div v-loading="loading" class="instance-detail">
    <div class="detail-header">
      <el-button text @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
      <template v-if="detail">
        <div class="header-main">
          <span class="header-title">{{ detail.bizTitle }}</span>
          <el-tag v-if="detail.subProcessName" type="info" effect="plain">子流程</el-tag>
          <el-tag :type="statusMeta(detail.status).type" effect="light">
            {{ statusMeta(detail.status).label }}
          </el-tag>
        </div>
        <div class="header-meta">
          <span>流程：{{ detail.processName }}（v{{ detail.processVersion }}）</span>
          <span>发起人：{{ detail.startUserName }}</span>
          <span>发起时间：{{ detail.startTime }}</span>
          <span v-if="detail.finishTime">结束时间：{{ detail.finishTime }}</span>
        </div>
        <el-link
          v-if="detail.parentInstanceId"
          class="header-parent-link"
          type="primary"
          :underline="false"
          @click="goParent"
        >
          返回父流程
        </el-link>
      </template>
    </div>

    <template v-if="detail">
      <ApprovalStatusBar
        v-if="actionableTask || detail.canCancel"
        ref="barRef"
        :buttons="actionableTask?.buttons ?? []"
        :return-targets="actionableTask?.returnTargets ?? []"
        :manual-route-options="actionableTask?.manualRouteOptions ?? []"
        :manual-route-multiple="actionableTask?.manualRouteMultiple ?? false"
        :pending-sign-tasks="actionableTask?.pendingSignTasks ?? []"
        :can-cancel="detail.canCancel"
        :loading="submitting"
        @submit="onActionSubmit"
        @cancel="onCancelInstance"
      />

      <div v-if="actionableTask" class="task-info-card">
        <span class="task-node">{{ actionableTask.task.nodeName }}</span>
        <el-tag v-if="actionableTask.task.multiMode === 'AND'" size="small" type="warning" effect="plain">
          会签
        </el-tag>
        <el-tag v-if="actionableTask.task.signType === 'TRANSFER'" size="small" effect="plain">转办</el-tag>
        <el-tag v-else-if="actionableTask.task.signType === 'DELEGATE'" size="small" effect="plain">委派</el-tag>
        <el-tag
          v-else-if="actionableTask.task.signType === 'BEFORE_SIGN' || actionableTask.task.signType === 'AFTER_SIGN'"
          size="small"
          effect="plain"
        >
          加签
        </el-tag>
        <span class="task-time">到达时间：{{ actionableTask.task.createTime }}</span>
        <span class="task-tip">该任务由你办理，处理完成后流程自动推进</span>
      </div>

      <div class="detail-grid">
        <div class="grid-main">
          <el-card shadow="never" class="detail-card">
            <template #header><span class="card-title">业务内容</span></template>
            <BizFormSnapshot :biz-type="detail.bizType" :form-json="detail.formSnapshotJson" />
          </el-card>
        </div>
        <div class="grid-side">
          <el-card v-if="detail.subInstances?.length" shadow="never" class="detail-card">
            <template #header><span class="card-title">子流程实例</span></template>
            <div class="sub-instance-list">
              <div
                v-for="sub in detail.subInstances"
                :key="sub.id"
                class="sub-instance-item"
                @click="goInstance(sub.id)"
              >
                <span class="sub-instance-name">{{ sub.name }}</span>
                <el-tag v-if="sub.advanceMode === 'IMMEDIATE'" size="small" effect="plain">旁路</el-tag>
                <el-tag :type="statusMeta(sub.status).type" size="small" effect="light">
                  {{ statusMeta(sub.status).label }}
                </el-tag>
              </div>
            </div>
          </el-card>
          <el-card shadow="never" class="detail-card">
            <template #header><span class="card-title">审批记录</span></template>
            <InstanceTimeline :records="detail.records" />
          </el-card>
        </div>
      </div>
    </template>

    <el-empty v-else-if="!loading" description="实例不存在或无权查看" />
  </div>
</template>

<style scoped>
.instance-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-header {
  padding: 16px;
  background-color: #ffffff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
}

.header-main {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-top: 6px;
}

.header-title {
  font-size: 18px;
  font-weight: 600;
  line-height: 28px;
  color: #303133;
}

.header-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 24px;
  margin-top: 8px;
  font-size: 13px;
  line-height: 22px;
  color: #909399;
}

.header-parent-link {
  margin-top: 8px;
  font-size: 13px;
}

.sub-instance-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sub-instance-item {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 8px 10px;
  cursor: pointer;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.sub-instance-item:hover {
  background-color: #ecf5ff;
}

.sub-instance-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-info-card {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding: 10px 16px;
  font-size: 13px;
  line-height: 22px;
  background-color: #ecf5ff;
  border: 1px solid #d9ecff;
  border-radius: 6px;
}

.task-node {
  font-size: 14px;
  font-weight: 600;
  color: #409eff;
}

.task-time,
.task-tip {
  color: #909399;
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
  align-items: start;
}

@media (max-width: 1100px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}

.grid-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.grid-side {
  min-width: 0;
}

.detail-card :deep(.el-card__header) {
  padding: 12px 16px;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
</style>
