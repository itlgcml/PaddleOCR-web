package com.paddleocr.web.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码：0 成功；4xxxx 客户端；5xxxx 系统；503xx OCR 服务段
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(0, "成功"),
    PARAM_ERROR(40000, "参数错误"),
    NOT_FOUND(40400, "资源不存在"),
    FILE_TYPE_NOT_ALLOWED(40010, "不支持的文件类型"),
    FILE_TOO_LARGE(40011, "文件大小超出限制"),

    // ---- 鉴权段 ----
    /** 未登录或令牌缺失（Header 无 Authorization） */
    UNAUTHORIZED(40100, "未登录或令牌缺失"),
    /** 令牌无效（签名不符 / 被篡改） */
    TOKEN_INVALID(40101, "令牌无效"),
    /** 令牌已过期 */
    TOKEN_EXPIRED(40102, "令牌已过期"),
    /** 已登出吊销的 Token 再访问（Redis 黑名单命中） */
    TOKEN_REVOKED(40103, "登录已失效，请重新登录"),
    /** 无权限访问（@PreAuthorize 校验不通过） */
    FORBIDDEN(40300, "无权限访问"),

    // ---- 账号域 ----
    /** 用户名已存在（注册重名） */
    USERNAME_EXISTS(40020, "用户名已存在"),
    /** 邮箱已占用（V2 预留，本期 email 非唯一不触发） */
    EMAIL_EXISTS(40021, "邮箱已被使用"),
    /** 用户名或密码错误（统一提示，防账号枚举） */
    PASSWORD_ERROR(40022, "用户名或密码错误"),
    /** 账号已被禁用 */
    USER_DISABLED(40023, "账号已被禁用"),
    /** 登录失败次数过多，账号锁定中 */
    ACCOUNT_LOCKED(40024, "登录失败次数过多，请 15 分钟后再试"),
    /** 刷新令牌无效（过期 / 类型不符 / 已吊销） */
    REFRESH_TOKEN_INVALID(40025, "刷新令牌无效"),

    // ---- 机构域 ----
    /** 机构不存在（查无 / 已删） */
    ORG_NOT_FOUND(40026, "机构不存在"),
    /** 存在下级机构，禁止删除 */
    ORG_HAS_CHILDREN(40027, "存在下级机构，禁止删除"),
    /** 机构下存在员工，禁止删除 */
    ORG_HAS_USERS(40028, "机构下存在员工，禁止删除"),
    /** 机构编码已存在 */
    ORG_CODE_EXISTS(40033, "机构编码已存在"),

    // ---- 角色域 ----
    /** 角色编码已存在 */
    ROLE_CODE_EXISTS(40029, "角色编码已存在"),
    /** 角色已分配用户，禁止删除 */
    ROLE_IN_USE(40030, "角色已分配用户，禁止删除"),
    /** 内置角色禁止此操作 */
    ROLE_BUILT_IN(40031, "内置角色禁止此操作"),
    /** 角色不存在或已停用 */
    ROLE_NOT_FOUND(40034, "角色不存在或已停用"),

    // ---- 用户管理域 ----
    /** 管理端操作的目标用户不存在 */
    USER_NOT_FOUND(40032, "用户不存在"),

    // ---- 审批流程域 ----
    /** 流程编码已存在（新增撞 uk_code，含并发兜底） */
    PROCESS_CODE_EXISTS(40040, "流程编码已存在"),
    /** 流程不存在（查无 / 已删） */
    PROCESS_NOT_FOUND(40041, "流程不存在"),
    /** 流程设计不合法（发布校验未通过） */
    PROCESS_DESIGN_INVALID(40042, "流程设计不合法"),
    /** 流程状态不允许该操作 */
    PROCESS_STATUS_ILLEGAL(40043, "流程状态不允许该操作"),
    /** 流程版本不存在 */
    PROCESS_VERSION_NOT_FOUND(40044, "流程版本不存在"),
    /** 请勿重复提交（分布式锁未获取） */
    PROCESS_REPEAT_SUBMIT(40045, "请勿重复提交"),

    // ---- 审批流程运行态（实例/任务/抄送；40050 已被律所选聘占用） ----
    /** 流程不存在或未发布（发起/预览时流程缺失/DRAFT/DISABLED/无已发布版本） */
    PROCESS_NOT_PUBLISHED(40046, "流程不存在或未发布"),
    /** 无权发起该流程（发起范围校验失败） */
    INSTANCE_START_FORBIDDEN(40047, "您无权发起该流程"),
    /** 业务单据不存在（快照加载失败/已删） */
    INSTANCE_BIZ_NOT_FOUND(40048, "业务单据不存在"),
    /** 该单据已有进行中的审批 */
    INSTANCE_ALREADY_RUNNING(40049, "该单据已有进行中的审批"),
    /** 审批实例不存在（查无/已删） */
    INSTANCE_NOT_FOUND(40051, "审批实例不存在"),
    /** 实例状态不允许该操作 */
    INSTANCE_STATUS_ILLEGAL(40052, "实例状态不允许该操作"),
    /** 审批任务不存在（查无/已删） */
    TASK_NOT_FOUND(40053, "审批任务不存在"),
    /** 非当前任务的审批人 */
    TASK_ASSIGNEE_MISMATCH(40054, "您不是该任务的审批人"),
    /** 任务已被处理（非 PENDING，或签被抢先） */
    TASK_ALREADY_DONE(40055, "任务已被处理"),
    /** 退回目标节点不合法 */
    TASK_RETURN_TARGET_INVALID(40056, "退回目标节点不合法"),
    /** 转办/委托/加签/抄送目标人员无效或不可用 */
    TASK_RECEIVER_INVALID(40057, "目标人员无效或不可用"),
    /** 该节点未启用此操作（按钮 enabled=false） */
    TASK_ACTION_DISABLED(40058, "该节点未启用此操作"),
    /** 无权查看该审批（非参与人） */
    INSTANCE_VIEW_FORBIDDEN(40059, "您无权查看该审批"),
    /** 单据审批中，禁止编辑或删除 */
    BIZ_IN_APPROVAL(40060, "单据审批中，禁止编辑或删除"),
    /** 流程节点不存在（nodeId 不在流程当前发布版本快照中） */
    PROCESS_NODE_NOT_FOUND(40061, "流程节点不存在"),
    /** 人工路由节点未选择下一步（PASS 未携带 routeTargetId） */
    TASK_MANUAL_ROUTE_REQUIRED(40062, "请选择下一步审批"),
    /** 人工路由目标不合法（routeTargetId 不是该人工路由节点的分支列） */
    TASK_MANUAL_ROUTE_INVALID(40063, "人工路由目标不合法"),
    /** 减签目标任务不合法（非当前任务直接派生的 PENDING 加签任务，或已办理） */
    TASK_REDUCE_SIGN_INVALID(40064, "减签目标任务不合法或不可删除"),

    // ---- 子流程（SUB_PROCESS） ----
    /** 子流程设计不合法（subDoc 缺失/根非 START/递归校验未通过/advanceMode 非法） */
    SUB_PROCESS_DESIGN_INVALID(40065, "子流程设计不合法"),
    /** 子流程嵌套层数超限（> approval.sub-process.max-depth，默认 3） */
    SUB_PROCESS_DEPTH_EXCEEDED(40066, "子流程嵌套层数超限"),
    /** 子流程节点不能放在分支列内（CONDITIONS/PARALLEL 的 conditionNodes） */
    SUB_PROCESS_PLACEMENT_INVALID(40067, "子流程节点不能放在分支列内"),
    /** 子流程实例不支持该操作（如直接撤销，撤销须从根实例发起） */
    SUB_INSTANCE_OPERATION_FORBIDDEN(40068, "子流程实例不支持该操作"),

    // ---- 律所选聘域 ----
    /** 律所选聘记录不存在（查无 / 已删） */
    LAW_FIRM_SELECT_NOT_FOUND(40050, "律所选聘记录不存在"),

    // ---- 系统段 ----
    SYSTEM_ERROR(50000, "系统内部错误"),
    DB_ERROR(50001, "数据库操作失败"),
    OCR_SERVICE_UNAVAILABLE(50301, "OCR 识别服务不可用"),
    OCR_SERVICE_TIMEOUT(50302, "OCR 识别超时"),
    OCR_RECOGNIZE_FAILED(50303, "OCR 识别失败");

    private final int code;
    private final String message;
}
