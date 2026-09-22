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

    // ---- 系统段 ----
    SYSTEM_ERROR(50000, "系统内部错误"),
    DB_ERROR(50001, "数据库操作失败"),
    OCR_SERVICE_UNAVAILABLE(50301, "OCR 识别服务不可用"),
    OCR_SERVICE_TIMEOUT(50302, "OCR 识别超时"),
    OCR_RECOGNIZE_FAILED(50303, "OCR 识别失败");

    private final int code;
    private final String message;
}
