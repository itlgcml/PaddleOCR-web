/**
 * 认证 / 机构 / 角色 / 用户管理 全部 VO 与 DTO 镜像
 * （唯一事实源：backend-docs/用户注册与登录功能.md §9）
 *
 * 铁律：后端雪花 Long 已序列化为字符串，所有 id 一律 string；
 * 时间字段为 `yyyy-MM-dd HH:mm:ss` 字符串。
 */

/** 机构简要信息（OrgBriefVO） */
export interface OrgBriefVO {
  id: string
  orgName: string
}

/** 当前用户信息（UserInfoVO） */
export interface UserInfoVO {
  id: string
  username: string
  nickname: string | null
  email: string | null
  org: OrgBriefVO | null
  /** 角色编码集合，如 ['USER'] / ['ADMIN'] */
  roles: string[]
  createTime: string
}

/** 登录响应（LoginVO） */
export interface LoginVO {
  accessToken: string
  refreshToken: string
  tokenType: string
  /** accessToken 有效秒数（默认 7200） */
  expiresIn: number
  user: UserInfoVO
}

/** 刷新令牌响应（TokenVO） */
export interface TokenVO {
  accessToken: string
  tokenType: string
  expiresIn: number
}

/** 注册请求（RegisterDTO） */
export interface RegisterDTO {
  username: string
  password: string
  nickname?: string
  email?: string
  /** 缺省挂 DEFAULT 默认机构 */
  orgId?: string
}

/** 登录请求（LoginDTO） */
export interface LoginDTO {
  username: string
  password: string
}

/** 刷新令牌请求（RefreshTokenDTO） */
export interface RefreshTokenDTO {
  refreshToken: string
}

/** 登出请求（LogoutDTO，Body 可选） */
export interface LogoutDTO {
  refreshToken?: string
}

/** 机构树节点（OrgTreeVO，递归树） */
export interface OrgTreeVO {
  id: string
  /** 父机构 ID，根节点为 "0" */
  parentId: string
  orgCode: string
  orgName: string
  sort: number
  /** 1=启用 0=停用 */
  status: number
  /** 子节点，叶子为 [] */
  children: OrgTreeVO[]
}

/** 机构新增（OrgSaveDTO） */
export interface OrgSaveDTO {
  parentId: string
  /** ^[A-Z][A-Z0-9_]*$，建后不可改 */
  orgCode: string
  orgName: string
  sort?: number
}

/** 机构修改（OrgUpdateDTO；orgCode / parentId 禁止修改） */
export interface OrgUpdateDTO {
  orgName: string
  sort?: number
  status?: number
}

/** 角色（RoleVO） */
export interface RoleVO {
  id: string
  roleCode: string
  roleName: string
  description: string | null
  /** 1=启用 0=停用 */
  status: number
  /** 1=内置角色（ADMIN/USER），禁止编辑/删除 */
  builtIn: number
  createTime: string
}

/** 角色新增（RoleSaveDTO） */
export interface RoleSaveDTO {
  /** 4~64 位，^[A-Z][A-Z0-9_]*$，不得为 ADMIN/USER */
  roleCode: string
  roleName: string
  description?: string
}

/** 角色修改（RoleUpdateDTO） */
export interface RoleUpdateDTO {
  roleName: string
  description?: string
  status?: number
}

/** 用户所属角色简要信息（用户分页 roles 元素） */
export interface RoleBriefVO {
  id: string
  roleCode: string
  roleName: string
}

/** 用户分页行（UserPageVO） */
export interface UserPageVO {
  id: string
  username: string
  nickname: string | null
  email: string | null
  org: OrgBriefVO | null
  roles: RoleBriefVO[]
  /** 1=启用 0=禁用 */
  status: number
  lastLoginTime: string | null
  createTime: string
}

/** 调整所属机构（UserOrgAssignDTO） */
export interface UserOrgAssignDTO {
  orgId: string
}

/** 分配角色·全量替换（UserRoleAssignDTO） */
export interface UserRoleAssignDTO {
  /** 可为空数组（全解绑） */
  roleIds: string[]
}

/** 启用/禁用（UserStatusDTO） */
export interface UserStatusDTO {
  status: number
}
