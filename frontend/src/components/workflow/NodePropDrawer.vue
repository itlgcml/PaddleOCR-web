<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Plus, Delete } from '@element-plus/icons-vue'
import {
  NodeType,
  type ApproverProps,
  type ConditionGroup,
  type ConditionProps,
  type ConditionRule,
  type StartProps,
  type WorkflowNode,
} from '@/types/workflow'
import { deepClone } from '@/utils/clone'
import { MOCK_FIELDS, MOCK_MEMBERS, MOCK_ROLES } from './mock'

const props = defineProps<{ node: WorkflowNode | null }>()

const emit = defineEmits<{
  (e: 'save', id: string, props: unknown, name: string): void
  (e: 'close'): void
}>()

const visible = defineModel<boolean>({ default: false })

const drawerTitle = computed(() => TITLE_MAP[props.node?.type ?? ''] ?? '节点配置')
const TITLE_MAP: Record<string, string> = {
  [NodeType.START]: '发起人',
  [NodeType.APPROVER]: '审批人',
  [NodeType.CONDITION]: '条件规则',
  [NodeType.PARALLEL_BRANCH]: '并行分支',
}

/** 编辑态：名称 + 属性临时副本（保存时一次性提交） */
const draftName = ref('')
const draftStart = ref<StartProps>({ rangeType: 'ALL', memberIds: [] })
const draftApprover = ref<ApproverProps>({ source: 'MEMBER', memberIds: [], leaderLevel: 1, multiMode: 'OR', emptyPolicy: 'AUTO_PASS', canRejectToStart: false })
const draftCondition = ref<ConditionProps>({ groups: [], isDefault: false })

watch(
  () => props.node,
  (node) => {
    if (!node) {
      return
    }
    draftName.value = node.name
    // 深拷贝，避免半成品编辑直接污染画布数据；只填充当前节点类型对应的一份草稿
    const raw = deepClone(node.props)
    switch (node.type) {
      case NodeType.START:
        draftStart.value = raw as StartProps
        break
      case NodeType.APPROVER:
        draftApprover.value = raw as ApproverProps
        break
      case NodeType.CONDITION:
        draftCondition.value = raw as ConditionProps
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
    case NodeType.CONDITION:
      emit('save', props.node.id, deepClone(draftCondition.value), draftCondition.value.isDefault ? '其他条件' : draftName.value)
      break
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
  const field = MOCK_FIELDS[0]
  return { fieldKey: field.id, fieldName: field.label, operator: 'GT', value: '', link: 'AND' }
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

/** 新条件组的默认名称「组n」：跳过已被占用的序号 */
function nextGroupName(): string {
  const used = new Set(draftCondition.value.groups.map((group) => group.name))
  let index = 1
  while (used.has(`组${index}`)) {
    index += 1
  }
  return `组${index}`
}

function onFieldChange(rule: ConditionRule, fieldId: string): void {
  const field = MOCK_FIELDS.find((f) => f.id === fieldId)
  if (field) {
    rule.fieldKey = field.id
    rule.fieldName = field.label
  }
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
  { value: 'MEMBER', label: '指定成员' },
  { value: 'LEADER', label: '主管' },
  { value: 'ROLE', label: '角色' },
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
      <el-form label-width="90px" label-position="left">
        <el-form-item label="节点名称">
          <el-input v-model="draftName" :disabled="node.type === NodeType.CONDITION && draftCondition.isDefault" maxlength="20" />
        </el-form-item>

        <!-- 发起人 -->
        <template v-if="node.type === NodeType.START">
          <el-form-item label="可发起人">
            <el-radio-group v-model="draftStart.rangeType">
              <el-radio-button value="ALL">所有人</el-radio-button>
              <el-radio-button value="MEMBER">指定成员</el-radio-button>
              <el-radio-button value="ROLE">指定角色</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="draftStart.rangeType !== 'ALL'" label="选择范围">
            <el-select v-model="draftStart.memberIds" multiple filterable placeholder="请选择" style="width: 100%">
              <el-option
                v-for="opt in draftStart.rangeType === 'ROLE' ? MOCK_ROLES : MOCK_MEMBERS"
                :key="opt.id"
                :label="opt.label"
                :value="opt.id"
              />
            </el-select>
          </el-form-item>
        </template>

        <!-- 审批人 -->
        <template v-else-if="node.type === NodeType.APPROVER">
          <el-form-item label="审批人">
            <el-select v-model="draftApprover.source" style="width: 100%">
              <el-option v-for="opt in SOURCE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="draftApprover.source === 'MEMBER' || draftApprover.source === 'ROLE'" label="选择对象">
            <el-select v-model="draftApprover.memberIds" multiple filterable placeholder="请选择" style="width: 100%">
              <el-option
                v-for="opt in draftApprover.source === 'ROLE' ? MOCK_ROLES : MOCK_MEMBERS"
                :key="opt.id"
                :label="opt.label"
                :value="opt.id"
              />
            </el-select>
          </el-form-item>
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
          <el-form-item label="退回发起">
            <el-switch v-model="draftApprover.canRejectToStart" active-text="允许退回发起人" />
          </el-form-item>
        </template>

        <!-- 条件：支持多个条件组，组内与组间均可选择「且 / 或」 -->
        <template v-else-if="node.type === NodeType.CONDITION">
          <el-alert v-if="draftCondition.isDefault" type="info" :closable="false" show-icon title="其他条件：其余分支均不满足时进入，无需配置规则" />
          <template v-else>
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
                    <el-select :model-value="rule.fieldKey" style="width: 104px" @update:model-value="onFieldChange(rule, $event)">
                      <el-option v-for="f in MOCK_FIELDS" :key="f.id" :label="f.label" :value="f.id" />
                    </el-select>
                    <el-select v-model="rule.operator" style="width: 104px">
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
      </el-form>
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
