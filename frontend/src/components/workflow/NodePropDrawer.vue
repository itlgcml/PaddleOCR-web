<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Plus, Delete } from '@element-plus/icons-vue'
import {
  NodeType,
  type ApproverProps,
  type CcProps,
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
  [NodeType.CC]: '抄送人',
  [NodeType.CONDITION]: '条件规则',
}

/** 编辑态：名称 + 属性临时副本（保存时一次性提交） */
const draftName = ref('')
const draftStart = ref<StartProps>({ rangeType: 'ALL', memberIds: [] })
const draftApprover = ref<ApproverProps>({ source: 'MEMBER', memberIds: [], leaderLevel: 1, multiMode: 'OR', emptyPolicy: 'AUTO_PASS', canRejectToStart: false })
const draftCc = ref<CcProps>({ memberIds: [], enableHandAdd: false })
const draftCondition = ref<ConditionProps>({ rules: [], isDefault: false })

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
      case NodeType.CC:
        draftCc.value = raw as CcProps
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
    case NodeType.CC:
      emit('save', props.node.id, deepClone(draftCc.value), draftName.value)
      break
    case NodeType.CONDITION:
      emit('save', props.node.id, deepClone(draftCondition.value), draftCondition.value.isDefault ? '默认条件' : draftName.value)
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

/** 条件规则行操作 */
function addRule(): void {
  const field = MOCK_FIELDS[0]
  draftCondition.value.rules.push({ fieldKey: field.id, fieldName: field.label, operator: 'GT', value: '' })
}

function removeRule(idx: number): void {
  draftCondition.value.rules.splice(idx, 1)
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

const memberOptions = computed(() => [...MOCK_MEMBERS, ...MOCK_ROLES])
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

        <!-- 抄送人 -->
        <template v-else-if="node.type === NodeType.CC">
          <el-form-item label="抄送人">
            <el-select v-model="draftCc.memberIds" multiple filterable placeholder="请选择抄送人" style="width: 100%">
              <el-option v-for="opt in memberOptions" :key="opt.id" :label="opt.label" :value="opt.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="手写抄送">
            <el-switch v-model="draftCc.enableHandAdd" active-text="允许发起人补充抄送人" />
          </el-form-item>
        </template>

        <!-- 条件 -->
        <template v-else-if="node.type === NodeType.CONDITION">
          <el-alert v-if="draftCondition.isDefault" type="info" :closable="false" show-icon title="默认条件：其余分支均不满足时进入，无需配置规则" />
          <template v-else>
            <div v-for="(rule, idx) in draftCondition.rules" :key="idx" class="wf-rule-row">
              <el-select :model-value="rule.fieldKey" style="width: 110px" @update:model-value="onFieldChange(rule, $event)">
                <el-option v-for="f in MOCK_FIELDS" :key="f.id" :label="f.label" :value="f.id" />
              </el-select>
              <el-select v-model="rule.operator" style="width: 110px">
                <el-option v-for="op in OPERATOR_OPTIONS" :key="op.value" :label="op.label" :value="op.value" />
              </el-select>
              <el-input v-model="rule.value" placeholder="值" style="flex: 1" />
              <el-button :icon="Delete" circle text type="danger" @click="removeRule(idx)" />
            </div>
            <div v-if="draftCondition.rules.length > 1" class="wf-rule-tip">条件之间为「且」的关系</div>
            <el-button type="primary" plain :icon="Plus" @click="addRule">添加条件</el-button>
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

.wf-rule-row {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-bottom: 8px;
}

.wf-rule-tip {
  margin: -2px 0 10px;
  font-size: 12px;
  color: #909399;
}
</style>
