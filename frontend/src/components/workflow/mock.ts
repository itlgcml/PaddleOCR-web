/** 纯前端演示用 mock 数据（二期替换为接口数据） */

export interface MockOption {
  id: string
  label: string
}

/** 条件字段池 */
export const MOCK_FIELDS: MockOption[] = [
  { id: 'amount', label: '报销金额' },
  { id: 'dept', label: '所属部门' },
  { id: 'days', label: '请假天数' },
]
