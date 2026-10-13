<script setup lang="ts">
import type { TaskRecordVO } from '@/types/approval'

/** 动作文案映射（含系统动作） */
const ACTION_LABELS: Record<string, string> = {
  START: '发起流程',
  PASS: '通过',
  REJECT: '拒绝',
  RETURN: '退回',
  TRANSFER: '转办',
  DELEGATE: '委派',
  ADD_SIGN: '加签',
  SUBMIT: '重新提交',
  CANCEL: '撤销',
  COMMENT: '评论',
  COPY: '抄送',
  AUTO_PASS: '自动通过',
  ENGINE: '系统处理',
}

/** 时间线节点颜色：系统动作灰显，其余按语义着色 */
const ACTION_TYPES: Record<string, 'primary' | 'success' | 'danger' | 'warning' | 'info'> = {
  START: 'primary',
  PASS: 'success',
  AUTO_PASS: 'info',
  REJECT: 'danger',
  RETURN: 'warning',
  TRANSFER: 'primary',
  DELEGATE: 'primary',
  ADD_SIGN: 'primary',
  SUBMIT: 'primary',
  CANCEL: 'info',
  COMMENT: 'primary',
  COPY: 'primary',
  ENGINE: 'info',
}

const props = defineProps<{ records: TaskRecordVO[] }>()

function labelOf(action: string): string {
  return ACTION_LABELS[action] ?? action
}

function typeOf(action: string): 'primary' | 'success' | 'danger' | 'warning' | 'info' {
  return ACTION_TYPES[action] ?? 'info'
}
</script>

<template>
  <div class="record-timeline">
    <el-timeline v-if="props.records.length > 0">
      <el-timeline-item
        v-for="record in props.records"
        :key="record.id"
        :timestamp="record.createTime"
        :type="typeOf(record.action)"
        placement="top"
      >
        <div class="record-head">
          <span class="record-operator">{{ record.operatorName }}</span>
          <span class="record-action">{{ labelOf(record.action) }}</span>
          <span v-if="record.nodeName" class="record-node">{{ record.nodeName }}</span>
        </div>
        <div v-if="record.detail" class="record-detail">{{ record.detail }}</div>
        <div v-if="record.comment" class="record-comment">{{ record.comment }}</div>
      </el-timeline-item>
    </el-timeline>
    <el-empty v-else description="暂无审批记录" :image-size="72" />
  </div>
</template>

<style scoped>
.record-timeline {
  padding: 4px 4px 0;
}

.record-head {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 14px;
  line-height: 22px;
}

.record-operator {
  font-weight: 600;
  color: #303133;
}

.record-action {
  color: #409eff;
}

.record-node {
  padding: 0 8px;
  font-size: 12px;
  line-height: 20px;
  color: #909399;
  background-color: #f4f4f5;
  border-radius: 10px;
}

.record-detail {
  margin-top: 2px;
  font-size: 13px;
  line-height: 20px;
  color: #909399;
}

.record-comment {
  margin-top: 4px;
  padding: 6px 10px;
  font-size: 13px;
  line-height: 20px;
  color: #606266;
  word-break: break-all;
  background-color: #f7f8fa;
  border-radius: 4px;
}
</style>
