package com.paddleocr.web.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * 自定义认证主体：扩展 userId 字段（供 JWT 签发与业务读取）
 *
 * @author paddleocr
 * @date 2026-09-22
 */
public class LoginUser extends User {

    private static final long serialVersionUID = 1L;

    /** 用户 ID（雪花 ID） */
    private final Long userId;

    public LoginUser(Long userId, String username, String password, boolean enabled,
                     boolean accountNonLocked, Collection<? extends GrantedAuthority> authorities) {
        super(username, password, enabled, true, true, accountNonLocked, authorities);
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }
}
