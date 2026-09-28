<script setup lang="ts">
import { computed } from 'vue'
import { ArrowDown, ArrowUp, Close, Plus } from '@element-plus/icons-vue'
import { NodeType, type ConditionProps, type WorkflowNode } from '@/types/workflow'
import NodeCard from './NodeCard.vue'
import AddNodeButton from './AddNodeButton.vue'

defineOptions({ inheritAttrs: false })

const props = defineProps<{ node: WorkflowNode | null; activeId?: string | null }>()

const branches = computed<WorkflowNode[]>(() => props.node?.conditionNodes ?? [])

/** 是否默认条件列（固定最右、不可删不可移） */
function isDefault(branch: WorkflowNode): boolean {
  return (branch.props as ConditionProps).isDefault === true
}
</script>

<template>
  <div v-if="node" class="wf-chain">
    <!-- 普通节点：卡片 + 「+」 -->
    <template v-if="node.type !== NodeType.CONDITIONS">
      <NodeCard :node="node" :active="node.id === activeId" v-bind="$attrs" />
      <AddNodeButton :parent-id="node.id" v-bind="$attrs" />
    </template>

    <!-- 条件分支容器（自身不渲染卡片）：多列并排，列内子流程 -->
    <div v-else class="wf-branch">
      <div class="wf-branch-body">
        <div v-for="(branch, idx) in branches" :key="branch.id" class="wf-branch-col">
          <!-- 分流横线：横跨各列中心，位于条件头上方 -->
          <span class="wf-branch-hr" />
          <div class="wf-cond-head" v-bind="$attrs" @click="$emit('open', branch)">
            <span class="wf-cond-title">{{ branch.name }}</span>
            <span v-if="!isDefault(branch)" class="wf-cond-actions">
              <el-icon
                v-if="idx > 0"
                title="优先级上移"
                @click.stop="$emit('move-branch', branch.id, -1)"
              ><ArrowUp /></el-icon>
              <el-icon
                v-if="idx < branches.length - 1"
                title="优先级下移"
                @click.stop="$emit('move-branch', branch.id, 1)"
              ><ArrowDown /></el-icon>
              <el-icon title="删除分支" @click.stop="$emit('del-branch', branch.id)"><Close /></el-icon>
            </span>
          </div>
          <!-- 条件头引出到列内首节点的连线 -->
          <span class="wf-cond-link" />
          <div class="wf-branch-col-chain">
            <NodeChain :node="branch.childNode" :active-id="activeId" v-bind="$attrs" />
            <!-- 空列兜底：仅当列内无节点时显示（避免与列内末节点后的「+」重复） -->
            <AddNodeButton v-if="!branch.childNode" :parent-id="branch.id" v-bind="$attrs" />
          </div>
        </div>
        <button class="wf-branch-add" type="button" @click="$emit('add-branch', node.id)">
          <el-icon :size="12"><Plus /></el-icon>
          <span>添加条件</span>
        </button>
      </div>
    </div>

    <!-- 分支汇合点与主干后继的连线 -->
    <span v-if="node.type === NodeType.CONDITIONS && node.childNode" class="wf-branch-link" />

    <!-- 后继主链（分支容器下方为汇合后的主干） -->
    <NodeChain :node="node.childNode" :active-id="activeId" v-bind="$attrs" />
  </div>
</template>

<style scoped>
.wf-chain {
  display: flex;
  flex-direction: column;
  align-items: center;
}

/* ---------- 条件分支容器 ---------- */
.wf-branch {
  position: relative;
  display: flex;
}

.wf-branch-body {
  position: relative;
  display: flex;
  padding: 40px 0 20px;
}

/* 出口：居中汇流竖线（入口线由上一节点「+」的连线延伸至分流横线） */
.wf-branch-body::after {
  position: absolute;
  z-index: 0;
  bottom: 0;
  left: 50%;
  width: 2px;
  height: 20px;
  margin-left: -1px;
  background: #dedfdf;
  content: '';
}

/* 分流横线：与上一节点「+」按钮中心等高（按钮压在横线上） */
.wf-branch-hr {
  position: absolute;
  top: -24px;
  right: 0;
  left: 0;
  z-index: 0;
  height: 2px;
  background: #dedfdf;
}

.wf-branch-col:first-child .wf-branch-hr {
  left: 50%;
}

.wf-branch-col:last-of-type .wf-branch-hr {
  right: 50%;
}

/* 每个分支列各自的进/出连线（列中心，自横线向下连到条件头） */
.wf-branch-col::before,
.wf-branch-col::after {
  position: absolute;
  z-index: 0;
  left: 50%;
  width: 2px;
  height: 20px;
  margin-left: -1px;
  background: #dedfdf;
  content: '';
}

/* 自分流横线（容器上方 24px）向下连到条件头 */
.wf-branch-col::before {
  top: -24px;
  height: 64px;
}

.wf-branch-col::after {
  bottom: 0;
}

.wf-branch-col {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 24px;
}

.wf-cond-head {
  display: flex;
  gap: 6px;
  align-items: center;
  justify-content: center;
  min-width: 180px;
  max-width: 220px;
  margin: 0 0 8px;
  padding: 6px 10px;
  cursor: pointer;
  background: #ffffff;
  border: 1px dashed #8a94e6;
  border-radius: 4px;
  transition: box-shadow 0.2s;
}

.wf-cond-head:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
}

.wf-cond-title {
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  color: #8a94e6;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wf-cond-actions {
  display: none;
  flex-shrink: 0;
  color: #909399;
}

.wf-cond-head:hover .wf-cond-actions {
  display: inline-flex;
}

.wf-cond-actions .el-icon:hover {
  color: #409eff;
}

.wf-branch-col-chain {
  display: flex;
  flex-direction: column;
  align-items: center;
}

/* 条件头引出到列内首节点的竖线 */
.wf-cond-link {
  flex-shrink: 0;
  width: 2px;
  height: 12px;
  background: #dedfdf;
}

/* 分支汇合后引出到主干的竖线 */
.wf-branch-link {
  flex-shrink: 0;
  width: 2px;
  height: 24px;
  background: #dedfdf;
}

/* 位于「+」按钮正下方、分支列右上侧 */
.wf-branch-add {
  position: absolute;
  top: 16px;
  right: -14px;
  z-index: 2;
  display: inline-flex;
  gap: 2px;
  align-items: center;
  padding: 2px 6px;
  font-size: 12px;
  color: #8a94e6;
  cursor: pointer;
  background: #ffffff;
  border: 1px solid #dedfdf;
  border-radius: 10px;
  transform: translateY(-50%);
}

.wf-branch-add:hover {
  color: #409eff;
  border-color: #409eff;
}
</style>
