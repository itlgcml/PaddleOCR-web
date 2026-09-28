/** 纯前端演示用 mock 数据（二期替换为接口数据） */

export interface MockOption {
  id: string
  label: string
}

export const MOCK_MEMBERS: MockOption[] = [
  { id: '10001', label: '张三' },
  { id: '10002', label: '李四' },
  { id: '10003', label: '王五' },
  { id: '10004', label: '赵六' },
  { id: '10005', label: '钱七' },
]

export const MOCK_ROLES: MockOption[] = [
  { id: '20001', label: '财务' },
  { id: '20002', label: '人事' },
  { id: '20003', label: '行政' },
]

/** 条件字段池 */
export const MOCK_FIELDS: MockOption[] = [
  { id: 'amount', label: '报销金额' },
  { id: 'dept', label: '所属部门' },
  { id: 'days', label: '请假天数' },
]

export function memberLabel(id: string): string {
  return MOCK_MEMBERS.find((m) => m.id === id)?.label ?? MOCK_ROLES.find((r) => r.id === id)?.label ?? id
}
