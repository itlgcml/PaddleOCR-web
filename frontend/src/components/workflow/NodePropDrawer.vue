<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Plus, Delete, EditPen } from '@element-plus/icons-vue'
import {
  APPROVER_BUTTON_LABELS,
  createApproverButtons,
  NodeType,
  normalizeApproverButtons,
  SUB_PROCESS_ADVANCE_HINTS,
  SUB_PROCESS_ADVANCE_LABELS,
  type ApproverButtonKey,
  type ApproverProps,
  type ConditionGroup,
  type ConditionProps,
  type ConditionRule,
  type ConditionsProps,
  type ParallelBranchProps,
  type StartProps,
  type SubProcessAdvanceMode,
  type SubProcessProps,
  type WorkflowNode,
} from '@/types/workflow'
import { deepClone } from '@/utils/clone'
import { useWorkflowStore } from '@/stores/workflow'
import PrincipalSelect from './PrincipalSelect.vue'

const props = defineProps<{ node: WorkflowNode | null }>()

const emit = defineEmits<{
  (e: 'save', id: string, props: unknown, name: string): void
  (e: 'close'): void
}>()

const visible = defineModel<boolean>({ default: false })

const store = useWorkflowStore()

/** 当前节点是否为「人工路由」模式下的条件分支列（此类列编辑选项名而非条件规则） */
const isManualCondition = computed(
  () => props.node?.type === NodeType.CONDITION && store.conditionsRoutingOf(props.node.id) === 'MANUAL',
)

const drawerTitle = computed(() => {
  if (isManualCondition.value) {
    return '人工路由选项'
  }
  return TITLE_MAP[props.node?.type ?? ''] ?? '节点配置'
})
const TITLE_MAP: Record<string, string> = {
  [NodeType.START]: '发起人',
  [NodeType.APPROVER]: '审批人',
  [NodeType.CONDITIONS]: '条件分支',
  [NodeType.CONDITION]: '条件规则',
  [NodeType.PARALLEL]: '并行分支',
  [NodeType.PARALLEL_BRANCH]: '并行分支',
  [NodeType.SUB_PROCESS]: '子流程',
}

/** 子流程推进模式选项：等待完成（默认）/ 启动即继续 */
const ADVANCE_OPTIONS: { value: SubProcessAdvanceMode; label: string }[] = [
  { value: 'WAIT', label: SUB_PROCESS_ADVANCE_LABELS.WAIT },
  { value: 'IMMEDIATE', label: SUB_PROCESS_ADVANCE_LABELS.IMMEDIATE },
]

/** 当前是否处于子流程内嵌设计视图（子设计的发起人节点：发起范围沿用父流程，不可配置） */
const isSubDocStart = computed(() => store.subProcessDepth > 0 && props.node?.type === NodeType.START)

/** 编辑态：名称 + 属性临时副本（保存时一次性提交） */
const draftName = ref('')
const draftStart = ref<StartProps>({ rangeType: 'ALL', memberIds: [], roleIds: [] })
const draftApprover = ref<ApproverProps>({ source: 'MEMBER', memberIds: [], roleIds: [], leaderLevel: 1, multiMode: 'OR', emptyPolicy: 'AUTO_PASS', canRejectToStart: false, rejectTargetId: '', buttons: createApproverButtons() })
const draftCondition = ref<ConditionProps>({ groups: [], isDefault: false, priority: 0, label: '' })
const draftConditions = ref<ConditionsProps>({ routing: 'AUTO' })
/** 子流程草稿只编辑推进模式：内嵌设计（subDoc）在子视图内直接编辑，保存时原样保留 */
const draftSubProcess = ref<{ advanceMode: SubProcessAdvanceMode }>({ advanceMode: 'WAIT' })

/** 审批人抽屉的页签：节点设置 / 按钮设置 */
const approverTab = ref<'NODE' | 'BUTTON'>('NODE')

/** 按钮设置：正在修改显示名称的按钮（null 表示无） */
const editingButtonKey = ref<ApproverButtonKey | null>(null)

/** 操作按钮的固定名称（左侧「操作按钮」列展示） */
function buttonLabel(key: ApproverButtonKey): string {
  return APPROVER_BUTTON_LABELS[key]
}

watch(
  () => props.node,
  (node) => {
    if (!node) {
      return
    }
    draftName.value = node.name
    approverTab.value = 'NODE'
    // 深拷贝，避免半成品编辑直接污染画布数据；只填充当前节点类型对应的一份草稿
    const raw = deepClone(node.props)
    switch (node.type) {
      case NodeType.START: {
        // 旧草稿可能缺 roleIds，读取时统一补齐
        const start = raw as StartProps
        draftStart.value = { rangeType: start.rangeType, memberIds: start.memberIds ?? [], roleIds: start.roleIds ?? [] }
        break
      }
      case NodeType.APPROVER: {
        // 旧草稿可能缺 roleIds / rejectTargetId，读取时统一补齐
        const appr = raw as ApproverProps
        draftApprover.value = {
          ...appr,
          memberIds: appr.memberIds ?? [],
          roleIds: appr.roleIds ?? [],
          rejectTargetId: appr.rejectTargetId ?? '',
          buttons: normalizeApproverButtons(appr.buttons),
        }
        break
      }
      case NodeType.CONDITIONS:
      case NodeType.PARALLEL: {
        // 旧草稿可能缺 routing，读取时补齐为 AUTO
        draftConditions.value = { routing: (raw as ConditionsProps).routing ?? 'AUTO' }
        break
      }
      case NodeType.CONDITION: {
        // 旧草稿可能缺 priority / label，读取时补齐
        const cond = raw as ConditionProps
        draftCondition.value = { ...cond, priority: cond.priority ?? 0, label: cond.label ?? '' }
        break
      }
      case NodeType.PARALLEL_BRANCH:
        draftCondition.value = { groups: (raw as ParallelBranchProps).groups ?? [], isDefault: false, priority: 0, label: '' }
        break
      case NodeType.SUB_PROCESS:
        // 旧草稿可能缺 advanceMode，读取时补齐为 WAIT
        draftSubProcess.value = { advanceMode: (raw as SubProcessProps).advanceMode ?? 'WAIT' }
        break
      default:
        break
    }
  },
  { immediate: true },
)

function handleSave(): void {
  if (!props.node) {
    return
  }
  switch (props.node.type) {
    case NodeType.START:
      emit('save', props.node.id, deepClone(draftStart.value), draftName.value)
      break
    case NodeType.APPROVER:
      emit('save', props.node.id, deepClone(draftApprover.value), draftName.value)
      break
    case NodeType.CONDITIONS:
    case NodeType.PARALLEL:
      emit('save', props.node.id, deepClone(draftConditions.value), draftName.value)
      break
    case NodeType.CONDITION:
      emit('save', props.node.id, deepClone(draftCondition.value),
        isManualCondition.value ? draftName.value : (draftCondition.value.isDefault ? '其他条件' : draftName.value))
      break
    case NodeType.PARALLEL_BRANCH:
      emit('save', props.node.id, { groups: deepClone(draftCondition.value.groups) }, draftName.value)
      break
    case NodeType.SUB_PROCESS: {
      // 内嵌设计（subDoc）在子视图内直接编辑落库，此处原样保留，仅提交推进模式
      const current = props.node.props as SubProcessProps
      emit('save', props.node.id, {
        subDoc: current.subDoc,
        advanceMode: draftSubProcess.value.advanceMode,
      }, draftName.value)
      break
    }
    default:
      emit('save', props.node.id, deepClone(props.node.props), draftName.value)
  }
  visible.value = false
}

function handleClose(): void {
  emit('close')
  visible.value = false
}

/** 条件组 / 条件规则操作（仅改草稿，保存时一次性提交） */
function createRule(): ConditionRule {
  return { fieldKey: '', fieldName: '', operator: 'GT', value: '', link: 'AND' }
}

function addGroup(): void {
  draftCondition.value.groups.push({ name: nextGroupName(), link: 'AND', rules: [createRule()] })
}

function removeGroup(index: number): void {
  draftCondition.value.groups.splice(index, 1)
}

function addRule(group: ConditionGroup): void {
  group.rules.push(createRule())
}

function removeRule(group: ConditionGroup, index: number): void {
  group.rules.splice(index, 1)
}

/** 「英文名称」聚焦时展示提示；记录当前聚焦的规则，避免多行提示互相干扰 */
const focusedRule = ref<ConditionRule | null>(null)

/** 新条件组的默认名称「组n」：跳过已被占用的序号 */
function nextGroupName(): string {
  const used = new Set(draftCondition.value.groups.map((group) => group.name))
  let index = 1
  while (used.has(`组${index}`)) {
    index += 1
  }
  return `组${index}`
}

const OPERATOR_OPTIONS = [
  { value: 'GT', label: '大于' },
  { value: 'GTE', label: '大于等于' },
  { value: 'LT', label: '小于' },
  { value: 'LTE', label: '小于等于' },
  { value: 'EQ', label: '等于' },
  { value: 'NEQ', label: '不等于' },
  { value: 'CONTAIN', label: '包含' },
  { value: 'NOT_CONTAIN', label: '不包含' },
]

const SOURCE_OPTIONS = [
  { value: 'MEMBER', label: '指定人员与角色' },
  { value: 'LEADER', label: '主管' },
  { value: 'SELF_SELECT', label: '发起人自选' },
  { value: 'INITIATOR', label: '发起人本人' },
]

const EMPTY_POLICY_OPTIONS = [
  { value: 'AUTO_PASS', label: '自动通过' },
  { value: 'TO_LEADER', label: '自动转交上级主管' },
  { value: 'TO_ADMIN', label: '转交管理员' },
]

</script>

<template>
  <el-drawer
    v-model="visible"
    :title="drawerTitle"
    size="560px"
    append-to-body
    destroy-on-close
    @closed="handleClose"
  >
    <div v-if="node" class="wf-drawer-body">
      <!-- 审批人：节点设置 / 按钮设置 -->
      <el-tabs v-if="node.type === NodeType.APPROVER" v-model="approverTab" class="wf-tabs">
        <el-tab-pane label="节点设置" name="NODE" />
        <el-tab-pane label="按钮设置" name="BUTTON" />
      </el-tabs>

      <el-form v-show="node.type !== NodeType.APPROVER || approverTab === 'NODE'" label-width="90px" label-position="left">
        <el-form-item label="节点名称">
          <el-input
            v-model="draftName"
            :disabled="node.type === NodeType.CONDITION && draftCondition.isDefault && !isManualCondition"
            maxlength="20"
          />
        </el-form-item>

        <!-- 发起人：与「基本信息」步骤的发起范围控件保持一致，但只读 -->
        <template v-if="node.type === NodeType.START">
          <!-- 子流程内嵌设计的发起人：发起人与发起范围沿用父流程实例，无需在此配置 -->
          <el-alert
            v-if="isSubDocStart"
            type="info"
            :closable="false"
            show-icon
            title="子流程沿用父流程的发起人与发起范围，此处不可配置"
          />
          <template v-else>
            <el-form-item label="谁可以发起">
              <el-radio-group v-model="draftStart.rangeType" disabled>
                <el-radio value="ALL">全体</el-radio>
                <el-radio value="SPECIFIC">指定</el-radio>
              </el-radio-group>
            </el-form-item>
            <template v-if="draftStart.rangeType === 'SPECIFIC'">
              <el-form-item label="指定人员">
                <PrincipalSelect v-model="draftStart.memberIds" type="USER" disabled placeholder="未选择人员" />
              </el-form-item>
              <el-form-item label="指定角色">
                <PrincipalSelect v-model="draftStart.roleIds" type="ROLE" disabled placeholder="未选择角色" />
              </el-form-item>
            </template>
            <el-alert
              type="info"
              :closable="false"
              show-icon
              title="发起人范围由「基本信息」步骤配置，此处不可修改"
            />
          </template>
        </template>

        <!-- 审批人 -->
        <template v-else-if="node.type === NodeType.APPROVER">
          <el-form-item label="审批人">
            <el-select v-model="draftApprover.source" style="width: 100%">
              <el-option v-for="opt in SOURCE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
          <template v-if="draftApprover.source === 'MEMBER'">
            <el-form-item label="指定人员">
              <PrincipalSelect v-model="draftApprover.memberIds" type="USER" placeholder="请选择人员" />
            </el-form-item>
            <el-form-item label="指定角色">
              <PrincipalSelect v-model="draftApprover.roleIds" type="ROLE" placeholder="请选择角色" />
            </el-form-item>
          </template>
          <el-form-item v-if="draftApprover.source === 'LEADER'" label="主管层级">
            <el-input-number v-model="draftApprover.leaderLevel" :min="1" :max="10" />
          </el-form-item>
          <el-form-item label="多人审批时">
            <el-radio-group v-model="draftApprover.multiMode">
              <el-radio value="OR">或签（一人通过即可）</el-radio>
              <el-radio value="AND">会签（全部通过）</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="审批人为空">
            <el-select v-model="draftApprover.emptyPolicy" style="width: 100%">
              <el-option v-for="opt in EMPTY_POLICY_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
        </template>

        <!-- 分支容器（条件分支 / 并行）：路由方式（自动 / 人工） -->
        <template v-else-if="node.type === NodeType.CONDITIONS || node.type === NodeType.PARALLEL">
          <el-form-item label="路由方式">
            <el-radio-group v-model="draftConditions.routing">
              <el-radio value="AUTO">{{ node.type === NodeType.PARALLEL ? '自动（全部分支并行）' : '自动（按条件路由）' }}</el-radio>
              <el-radio value="MANUAL">{{ node.type === NodeType.PARALLEL ? '人工（审批人勾选分支）' : '人工（审批人选择）' }}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-alert
            v-if="draftConditions.routing === 'MANUAL'"
            type="info"
            :closable="false"
            show-icon
            :title="node.type === NodeType.PARALLEL
              ? '人工并行：上游审批人通过时勾选要执行的分支（可多选），仅勾选的分支并行执行'
              : '人工路由：上游审批人通过后，从各分支列的「选项名称」中选择下一步'"
          />
          <el-alert
            v-else
            type="info"
            :closable="false"
            show-icon
            :title="node.type === NodeType.PARALLEL
              ? '自动并行：全部分支同时执行，全部完成后汇合继续'
              : '自动路由：按各分支列配置的条件规则自动匹配下一步'"
          />
        </template>

        <!-- 子流程：推进方式（等待完成 / 启动即继续），内部设计在子视图中编辑 -->
        <template v-else-if="node.type === NodeType.SUB_PROCESS">
          <el-form-item label="推进方式">
            <el-radio-group v-model="draftSubProcess.advanceMode">
              <el-radio v-for="opt in ADVANCE_OPTIONS" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </el-radio>
            </el-radio-group>
          </el-form-item>
          <el-alert
            type="info"
            :closable="false"
            show-icon
            :title="SUB_PROCESS_ADVANCE_HINTS[draftSubProcess.advanceMode]"
          />
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="点击卡片右侧箭头进入子流程内部设计；子流程内可继续添加节点与嵌套子流程（层数受上限约束）"
          />
        </template>

        <!-- 条件分支 / 并行分支：均支持配置条件组，组内与组间可「且 / 或」 -->
        <template v-else-if="node.type === NodeType.CONDITION || node.type === NodeType.PARALLEL_BRANCH">
          <!-- 人工路由选项：只需配置选项名称 -->
          <template v-if="isManualCondition">
            <el-alert type="info" :closable="false" show-icon title="该选项名称将作为审批人选择下一步时的候选项" />
            <el-form-item label="选项名称">
              <el-input v-model="draftCondition.label" placeholder="如：发给 B / 直接发 C" maxlength="20" />
            </el-form-item>
          </template>
          <!-- 自动路由：条件组编辑 -->
          <template v-else>
          <!-- 并行分支：未配置条件则默认执行 -->
          <el-alert
            v-if="node.type === NodeType.PARALLEL_BRANCH"
            type="info"
            :closable="false"
            show-icon
            title="未配置条件时该分支默认执行；配置条件后仅满足条件时进入"
          />
          <!-- 条件分支「其他条件」：无需配置规则 -->
          <el-alert
            v-else-if="draftCondition.isDefault"
            type="info"
            :closable="false"
            show-icon
            title="其他条件：其余分支均不满足时进入，无需配置规则"
          />
          <!-- 条件组编辑（条件分支非默认列 / 并行分支） -->
          <template v-if="node.type === NodeType.PARALLEL_BRANCH || !draftCondition.isDefault">
            <template v-for="(group, groupIdx) in draftCondition.groups" :key="groupIdx">
              <!-- 组间连接符：本组与上一组的关系 -->
              <div v-if="groupIdx > 0" class="wf-logic-row">
                <el-select v-model="group.link" size="small" style="width: 72px">
                  <el-option label="且" value="AND" />
                  <el-option label="或" value="OR" />
                </el-select>
              </div>
              <div class="wf-group">
                <div class="wf-group-head">
                  <el-input v-model="group.name" class="wf-group-name" size="small" maxlength="20" placeholder="条件组名称" />
                  <el-icon class="wf-group-del" title="删除条件组" @click="removeGroup(groupIdx)"><Delete /></el-icon>
                </div>
                <template v-for="(rule, ruleIdx) in group.rules" :key="ruleIdx">
                  <!-- 组内连接符：本条与上一条的关系 -->
                  <div v-if="ruleIdx > 0" class="wf-logic-row">
                    <el-select v-model="rule.link" size="small" style="width: 72px">
                      <el-option label="且" value="AND" />
                      <el-option label="或" value="OR" />
                    </el-select>
                  </div>
                  <div class="wf-rule-row">
                    <el-input v-model="rule.fieldName" placeholder="中文名称" style="width: 92px" />
                    <el-tooltip content="此字段与业务字段需一致" placement="top" :visible="focusedRule === rule">
                      <el-input
                        v-model="rule.fieldKey"
                        placeholder="英文名称"
                        style="width: 92px"
                        @focus="focusedRule = rule"
                        @blur="focusedRule = null"
                      />
                    </el-tooltip>
                    <el-select v-model="rule.operator" style="width: 88px">
                      <el-option v-for="op in OPERATOR_OPTIONS" :key="op.value" :label="op.label" :value="op.value" />
                    </el-select>
                    <el-input v-model="rule.value" placeholder="值" style="flex: 1" />
                    <el-button :icon="Delete" circle text type="danger" @click="removeRule(group, ruleIdx)" />
                  </div>
                </template>
                <el-button type="primary" plain size="small" :icon="Plus" @click="addRule(group)">添加条件</el-button>
              </div>
            </template>
            <el-button class="wf-group-add" type="primary" plain :icon="Plus" @click="addGroup">添加条件组</el-button>
          </template>
          </template>
        </template>
      </el-form>

      <!-- 按钮设置：操作按钮的显示名称与启用开关 -->
      <div v-if="node.type === NodeType.APPROVER && approverTab === 'BUTTON'" class="wf-button-setting">
        <div class="wf-tab-title">操作按钮</div>
        <el-table :data="draftApprover.buttons" size="small" border style="width: 100%">
          <el-table-column label="操作按钮" width="96">
            <template #default="{ row }">
              {{ buttonLabel(row.key) }}
            </template>
          </el-table-column>
          <el-table-column label="显示名称">
            <template #default="{ row }">
              <el-input
                v-if="editingButtonKey === row.key"
                v-model="row.label"
                size="small"
                maxlength="10"
                autofocus
                @blur="editingButtonKey = null"
                @keyup.enter="editingButtonKey = null"
              />
              <span v-else class="wf-btn-label">
                {{ row.label }}
                <el-icon class="wf-btn-edit" title="修改显示名称" @click="editingButtonKey = row.key"><EditPen /></el-icon>
              </span>
            </template>
          </el-table-column>
          <el-table-column label="启用" width="72" align="center">
            <template #default="{ row }">
              <el-switch v-model="row.enabled" />
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="handleSave">保 存</el-button>
    </template>
  </el-drawer>
</template>

<style scoped>
.wf-drawer-body {
  padding: 0 4px;
}

/* 审批人抽屉页签：节点设置 / 按钮设置 */
.wf-tabs {
  margin-bottom: 12px;
}

.wf-tab-title {
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

/* 按钮设置：显示名称的只读态 + 编辑图标 */
.wf-btn-label {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  color: #606266;
}

.wf-btn-edit {
  color: #c0c4cc;
  cursor: pointer;
}

.wf-btn-edit:hover {
  color: #409eff;
}

/* 条件组卡片 */
.wf-group {
  padding: 8px 10px 10px;
  margin-bottom: 10px;
  background: #fafbfc;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}

.wf-group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

/* 条件组名称可直接编辑 */
.wf-group-name {
  width: 160px;
}

.wf-group-del {
  color: #909399;
  cursor: pointer;
}

.wf-group-del:hover {
  color: #f56c6c;
}

/* 组内 / 组间的「且 / 或」连接符：单独一行居中 */
.wf-logic-row {
  display: flex;
  justify-content: center;
  margin-bottom: 8px;
}

.wf-rule-row {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-bottom: 8px;
}

.wf-group-add {
  width: 100%;
}
</style>
