/**
 * 审批操作栏（运行态）配置：按钮皮肤、图标与各操作弹窗的表单字段。
 * 与 `types/workflow.ts` 的 ApprovalActionKey / ApprovalActionPayload 一一对应。
 */
import type { Component } from 'vue'
import {
  Back,
  ChatDotRound,
  CircleCheck,
  Close,
  CopyDocument,
  Plus,
  Promotion,
  RefreshLeft,
  Share,
} from '@element-plus/icons-vue'
import type { AddSignDirection, ApprovalActionKey } from '@/types/workflow'

/** 下拉选项（人员 / 节点） */
export interface ActionOption {
  id: string
  label: string
}

/** 弹窗表单字段 */
export interface ActionField {
  /** 字段名，与 ApprovalActionPayload 中对应字段同名 */
  key: 'receiverId' | 'receiverIds' | 'targetNodeId' | 'comment'
  label: string
  /** select=人员/节点下拉；textarea=意见输入 */
  type: 'select' | 'textarea'
  placeholder: string
  required: boolean
  /** 仅 select：是否多选 */
  multiple?: boolean
  /** 输入控件上方的补充说明（如「取消后，该审批流程将自动结束」） */
  tip?: string
}

/** 弹窗底部提交按钮；加签为「向前加签 / 向后加签」两个按钮 */
export interface ActionSubmit {
  label: string
  direction?: AddSignDirection
}

/** 单个操作的完整界面配置 */
export interface ActionMeta {
  key: ApprovalActionKey
  label: string
  icon: Component
  /** plain=描边按钮（通过/拒绝/评论）；text=无边框文字按钮（其余） */
  skin: 'plain' | 'text'
  /** 描边按钮语义色 */
  type?: 'success' | 'danger' | 'primary'
  fields: ActionField[]
  submits: ActionSubmit[]
}

/** 工具栏按钮展示顺序（与设计稿一致） */
export const ACTION_ORDER: ApprovalActionKey[] = [
  'PASS',
  'REJECT',
  'COMMENT',
  'COPY',
  'TRANSFER',
  'DELEGATE',
  'ADD_SIGN',
  'RETURN',
  'CANCEL',
]

/** 审批意见输入框（通过/拒绝共用） */
const APPROVE_COMMENT: ActionField = {
  key: 'comment',
  label: '审批意见',
  type: 'textarea',
  placeholder: '请输入审批意见',
  required: false,
}

export const ACTION_META: Record<ApprovalActionKey, ActionMeta> = {
  PASS: {
    key: 'PASS',
    label: '通过',
    icon: CircleCheck,
    skin: 'plain',
    type: 'success',
    fields: [APPROVE_COMMENT],
    submits: [{ label: '通过' }],
  },
  REJECT: {
    key: 'REJECT',
    label: '拒绝',
    icon: Close,
    skin: 'plain',
    type: 'danger',
    fields: [{ ...APPROVE_COMMENT, required: true }],
    submits: [{ label: '拒绝' }],
  },
  COMMENT: {
    key: 'COMMENT',
    label: '评论',
    icon: ChatDotRound,
    skin: 'plain',
    type: 'primary',
    fields: [
      { key: 'comment', label: '评论内容', type: 'textarea', placeholder: '请输入评论内容', required: true },
    ],
    submits: [{ label: '评论' }],
  },
  COPY: {
    key: 'COPY',
    label: '抄送',
    icon: CopyDocument,
    skin: 'text',
    fields: [
      {
        key: 'receiverIds',
        label: '抄送人',
        type: 'select',
        placeholder: '请选择抄送人',
        required: true,
        multiple: true,
      },
      { key: 'comment', label: '抄送意见', type: 'textarea', placeholder: '请输入抄送意见', required: false },
    ],
    submits: [{ label: '抄送' }],
  },
  TRANSFER: {
    key: 'TRANSFER',
    label: '转办',
    icon: Share,
    skin: 'text',
    fields: [
      { key: 'receiverId', label: '新审批人', type: 'select', placeholder: '请选择', required: true },
      { ...APPROVE_COMMENT, required: true },
    ],
    submits: [{ label: '转办' }],
  },
  DELEGATE: {
    key: 'DELEGATE',
    label: '委派',
    icon: Promotion,
    skin: 'text',
    fields: [
      { key: 'receiverId', label: '接收人', type: 'select', placeholder: '请选择', required: true },
      { ...APPROVE_COMMENT, required: true },
    ],
    submits: [{ label: '委派' }],
  },
  ADD_SIGN: {
    key: 'ADD_SIGN',
    label: '加签',
    icon: Plus,
    skin: 'text',
    fields: [
      { key: 'receiverId', label: '加签处理人', type: 'select', placeholder: '请选择', required: true },
      { ...APPROVE_COMMENT, required: true },
    ],
    submits: [
      { label: '向前加签', direction: 'BEFORE' },
      { label: '向后加签', direction: 'AFTER' },
    ],
  },
  RETURN: {
    key: 'RETURN',
    label: '退回',
    icon: Back,
    skin: 'text',
    fields: [
      { key: 'targetNodeId', label: '退回节点', type: 'select', placeholder: '请选择', required: true },
      { key: 'comment', label: '退回理由', type: 'textarea', placeholder: '请输入退回理由', required: true },
    ],
    submits: [{ label: '退回' }],
  },
  CANCEL: {
    key: 'CANCEL',
    label: '取消',
    icon: RefreshLeft,
    skin: 'text',
    fields: [
      {
        key: 'comment',
        label: '取消理由',
        type: 'textarea',
        placeholder: '请输入取消理由',
        required: true,
        tip: '取消后，该审批流程将自动结束',
      },
    ],
    submits: [{ label: '确认' }],
  },
}
