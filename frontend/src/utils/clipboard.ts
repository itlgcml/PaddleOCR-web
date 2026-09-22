/**
 * 剪贴板工具（纯函数，不属于 composable）
 */

/** 复制文本到剪贴板（需 secure context），成功返回 true */
export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    return false
  }
}
