/**
 * 深拷贝可序列化数据（节点属性、流程文档等纯数据结构）。
 * 存在 Pinia / ref 中的对象会被 Vue 转为 reactive 代理，代理对象无法被 structuredClone 克隆
 * （抛 DataCloneError），故统一走 JSON 深拷贝。
 */
export function deepClone<T>(value: T): T {
  if (value === null || typeof value !== 'object') {
    return value
  }
  return JSON.parse(JSON.stringify(value)) as T
}
