<script setup lang="ts">
import { computed } from 'vue'
import { ArrowDown, ArrowUp, Close, Plus } from '@element-plus/icons-vue'
import { isBranchContainer, NodeType, type ConditionProps, type WorkflowNode } from '@/types/workflow'
import NodeCard from './NodeCard.vue'
import AddNodeButton from './AddNodeButton.vue'

defineOptions({ inheritAttrs: false })

const props = defineProps<{ node: WorkflowNode | null; activeId?: string | null }>()

const branches = computed<WorkflowNode[]>(() => props.node?.conditionNodes ?? [])

/** 是否为分支容器（条件分支 / 并行节点）：容器自身不渲染卡片 */
const isBranch = computed(() => props.node !== null && isBranchContainer(props.node.type))

/** 是否为并行节点：无默认列，加列文案与条件分支区分 */
const isParallel = computed(() => props.node?.type === NodeType.PARALLEL)

/** 是否默认条件列（固定最右、不可删不可移） */
function isDefault(branch: WorkflowNode): boolean {
  return (branch.props as ConditionProps).isDefault === true
}
</script>

<template>
  <div v-if="node" class="wf-chain">
    <!-- 普通节点：卡片 + 「+」 -->
    <template v-if="!isBranch">
      <NodeCard :node="node" :active="node.id === activeId" v-bind="$attrs" />
      <AddNodeButton :parent-id="node.id" :parent-type="node.type" v-bind="$attrs" />
    </template>

    <!-- 分支容器（条件分支 / 并行节点，自身不渲染卡片）：多列并排，列内子流程 -->
    <div v-else class="wf-branch">
      <div class="wf-branch-body">
        <div
          v-for="(branch, idx) in branches"
          :key="branch.id"
          class="wf-branch-col"
          :class="{ 'is-first': idx === 0 && branches.length > 1, 'is-last': idx === branches.length - 1 && branches.length > 1 }"
        >
          <!-- 分流横线段：位于条件头上方，首/末列收口到自身列中心 -->
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
            <AddNodeButton v-if="!branch.childNode" :parent-id="branch.id" :parent-type="branch.type" v-bind="$attrs" />
          </div>
          <!-- 列底出线：自适应占满剩余高度，保证长短列都延伸到汇合横线 -->
          <span class="wf-branch-col-out" />
          <!-- 汇合横线段：贴列容器底部，首/末列收口到自身列中心 -->
          <span class="wf-branch-join" />
        </div>
        <button class="wf-branch-add" type="button" @click="$emit('add-branch', node.id)">
          <el-icon :size="12"><Plus /></el-icon>
          <span>{{ isParallel ? '添加并行分支' : '添加条件' }}</span>
        </button>
      </div>
    </div>

    <!-- 分支汇合点 → 主干后继：与普通节点一致的「+」添加节点入口（引出竖线由按钮自身绘制） -->
    <AddNodeButton
      v-if="isBranch"
      :parent-id="node.id"
      :parent-type="node.type"
      v-bind="$attrs"
    />

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

/* 上内边距 68 = 入口竖线 44 + 列进线 24（横线所在高度即两者的交点）；
   其中 44 里预留给「添加条件」按钮的高度，避免按钮压住横线 */
.wf-branch-body {
  position: relative;
  display: flex;
  /* 按内容自然宽度布局：嵌套分支时避免列被父级压缩导致横线缩短甚至不可见 */
  width: max-content;
  padding: 68px 0 20px;
}

/* 入口：自上一节点引下的居中竖线，补齐到分流横线（横线位于 body 内 44px 处） */
.wf-branch-body::before {
  position: absolute;
  z-index: 0;
  top: 0;
  left: 50%;
  width: 2px;
  height: 44px;
  margin-left: -1px;
  background: #dedfdf;
  content: '';
}

/* 出口：自汇合横线中点向下引出到下一节点的汇流竖线 */
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

/* 分流/汇合横线段：各列自绘一段，相邻列在列间 padding 处首尾相接；
   端点列收口到自身列中心 —— 50% 与列内竖线（::before/col-out 的 left:50%）同一基准，
   端点必然与竖线对齐，不依赖任何 JS 测量，也不会超出分支两端 */
.wf-branch-hr,
.wf-branch-join {
  position: absolute;
  z-index: 0;
  right: 0;
  left: 0;
  height: 2px;
  background: #dedfdf;
}

/* 分流横线段：位于列内容顶上方 24px（= body 上内边距 68px 的下半段） */
.wf-branch-hr {
  top: -24px;
}

/* 汇合横线段：贴列容器底部 */
.wf-branch-join {
  bottom: 0;
}

/* 只有多列时端点列才收口；单列保留完整横线。
   必须用直接子级选择器：否则嵌套分支时，外层列的 is-first/is-last
   会连带命中内层列的 hr/join，使其同时带上 left:50% 与 right:50%（宽度 0）导致横线消失 */
.wf-branch-col.is-first > .wf-branch-hr,
.wf-branch-col.is-first > .wf-branch-join {
  left: 50%;
}

.wf-branch-col.is-last > .wf-branch-hr,
.wf-branch-col.is-last > .wf-branch-join {
  right: 50%;
}

/* 分流横线 → 条件头顶端的进线（固定 24px，对应 body 上内边距 68px 的下半段） */
.wf-branch-col::before {
  position: absolute;
  z-index: 0;
  top: -24px;
  left: 50%;
  width: 2px;
  height: 24px;
  margin-left: -1px;
  background: #dedfdf;
  content: '';
}

/* 列底出线：撑满列剩余高度（列被 flex 拉伸对齐，长短列均连到汇合横线） */
.wf-branch-col-out {
  position: relative;
  z-index: 0;
  flex: 1 1 auto;
  width: 2px;
  min-height: 20px;
  background: #dedfdf;
}

.wf-branch-col {
  position: relative;
  display: flex;
  /* 列宽固定取内容自然宽度：嵌套分支时避免 fit-content 解析不稳定，
     导致列中心漂移、横线端点错位（此前「缺半条/超出两端」的根因） */
  width: max-content;
  flex-shrink: 0;
  flex-direction: column;
  align-items: center;
  padding: 0 24px;
}

/* 实体层：z-index 1，避免被 muted 的连线（::before/横线）压住 */
.wf-cond-head {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 6px;
  align-items: center;
  justify-content: center;
  min-width: 180px;
  max-width: 220px;
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

/* 条件头引出到列内首节点的竖线（连线层 z-index 0，含条件头原 8px 下外边距，保证不断线） */
.wf-cond-link {
  position: relative;
  z-index: 0;
  flex-shrink: 0;
  width: 2px;
  height: 20px;
  background: #dedfdf;
}

/* 位于分流横线正中上方（横向居中，底边距横线 2px，不遮挡横线） */
.wf-branch-add {
  position: absolute;
  top: 44px;
  left: 50%;
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
  transform: translate(-50%, calc(-100% - 2px));
}

.wf-branch-add:hover {
  color: #409eff;
  border-color: #409eff;
}
</style>
