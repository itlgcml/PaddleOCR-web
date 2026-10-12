/**
 * 审批流「指定人员 / 指定角色」下拉的远程搜索数据源。
 * 后端搜索逻辑保持不变：人员走 `/api/users/page`（username 前缀匹配用户名/昵称），
 * 角色走 `/api/roles/page`（keyword 前缀匹配角色编码/角色名称）。
 */
import { reactive, ref } from 'vue'
import { useDebounceFn } from '@vueuse/core'
import { fetchRolePage } from '@/api/role'
import { fetchUserPage } from '@/api/user'
import type { RoleVO, UserPageVO } from '@/types/auth'

/** 下拉选项：主名称为展示名，副名称为用户名/角色编码，便于区分同名项 */
export interface PrincipalOption {
  id: string
  label: string
  sub: string
}

/** 下拉搜索类型：USER=指定人员；ROLE=指定角色 */
export type PrincipalType = 'USER' | 'ROLE'

/** 单次搜索返回条数 */
const SEARCH_PAGE_SIZE = 20
/** 预热缓存条数（后端 pageSize 上限 100） */
const WARM_PAGE_SIZE = 100
/** 输入防抖毫秒 */
const SEARCH_DEBOUNCE_MS = 300

/** id → 选项（进程内缓存：搜索与预热写入，画布回显与只读态标签读取） */
const optionCache = reactive(new Map<string, PrincipalOption>())

/** 预热任务（模块级共享，保证整个页面只发一次） */
let warmTask: Promise<void> | null = null

/** 回显展示名：未命中缓存时退回 id */
export function principalLabel(id: string): string {
  return optionCache.get(id)?.label ?? id
}

/** 回显选项：未命中缓存时为 undefined（调用方自行用 id 兜底） */
export function principalOption(id: string): PrincipalOption | undefined {
  return optionCache.get(id)
}

function toUserOption(user: UserPageVO): PrincipalOption {
  const label = user.nickname?.trim() ? user.nickname : user.username
  const option: PrincipalOption = { id: user.id, label, sub: user.username }
  optionCache.set(user.id, option)
  return option
}

function toRoleOption(role: RoleVO): PrincipalOption {
  const option: PrincipalOption = { id: role.id, label: role.roleName, sub: role.roleCode }
  optionCache.set(role.id, option)
  return option
}

/**
 * 预热名称缓存：各拉首页用户/角色，供已保存流程回显名称（后端无按 id 批量查询接口）。
 * 模块级去重，重复调用只发一次请求；失败时清空任务，允许下次重试。
 */
export function warmPrincipalLabels(): void {
  if (warmTask !== null) {
    return
  }
  warmTask = (async () => {
    try {
      const [users, roles] = await Promise.all([
        fetchUserPage({ pageNum: 1, pageSize: WARM_PAGE_SIZE }),
        fetchRolePage({ pageNum: 1, pageSize: WARM_PAGE_SIZE }),
      ])
      for (const item of users.records) {
        toUserOption(item)
      }
      for (const item of roles.records) {
        toRoleOption(item)
      }
    } catch {
      // 失败提示由拦截器统一处理；清空任务以便下次进入时重试
      warmTask = null
    }
  })()
}

/**
 * 单个下拉的搜索状态：输入即查（防抖），支持多次搜索多次选择。
 */
export function usePrincipalOptions(type: PrincipalType) {
  const options = ref<PrincipalOption[]>([])
  const loading = ref(false)

  /** 请求序号：丢弃迟到响应，避免慢请求覆盖最新搜索结果 */
  let lastSeq = 0

  /** 按关键词拉一页；空关键词取首页，便于直接浏览选择 */
  async function load(keyword: string): Promise<void> {
    const seq = lastSeq + 1
    lastSeq = seq
    loading.value = true
    try {
      if (type === 'USER') {
        const page = await fetchUserPage({
          pageNum: 1,
          pageSize: SEARCH_PAGE_SIZE,
          username: keyword || undefined,
        })
        if (seq !== lastSeq) {
          return
        }
        options.value = page.records.map(toUserOption)
      } else {
        const page = await fetchRolePage({
          pageNum: 1,
          pageSize: SEARCH_PAGE_SIZE,
          keyword: keyword || undefined,
        })
        if (seq !== lastSeq) {
          return
        }
        options.value = page.records.map(toRoleOption)
      }
    } catch {
      // 失败提示由拦截器统一处理；清空避免停留在上一次的无关结果
      if (seq === lastSeq) {
        options.value = []
      }
    } finally {
      if (seq === lastSeq) {
        loading.value = false
      }
    }
  }

  /** 输入防抖：连续输入只发最后一次请求 */
  const debouncedLoad = useDebounceFn(load, SEARCH_DEBOUNCE_MS)

  return { options, loading, load: debouncedLoad }
}
