package com.paddleocr.web.security;

import com.paddleocr.web.mapper.entity.UserDO;
import com.paddleocr.web.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring Security 认证模型装载：用户 + 角色编码 → UserDetails（authorities）
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    /** Spring Security 角色权限前缀约定：hasRole('ADMIN') 匹配 ROLE_ADMIN */
    private static final String ROLE_PREFIX = "ROLE_";

    /** 启用状态值 */
    private static final int STATUS_ENABLED = 1;

    private final UserService userService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // LambdaQueryWrapper eq 自动带 deleted=0
        UserDO user = userService.getByUsername(username);
        if (user == null) {
            // DaoAuthenticationProvider 默认掩码为 BadCredentialsException → 40022（防账号枚举）
            throw new UsernameNotFoundException("user not found: " + username);
        }
        // 角色每次查库（撤权即时生效，不依赖 JWT/缓存）
        List<SimpleGrantedAuthority> authorities = userService.listRoleCodes(user.getId()).stream()
                .map(roleCode -> new SimpleGrantedAuthority(ROLE_PREFIX + roleCode))
                .collect(Collectors.toList());
        boolean enabled = Integer.valueOf(STATUS_ENABLED).equals(user.getStatus());
        boolean accountNonLocked = user.getLockedUntil() == null
                || user.getLockedUntil().isBefore(LocalDateTime.now());
        return new LoginUser(user.getId(), user.getUsername(), user.getPasswordHash(),
                enabled, accountNonLocked, authorities);
    }
}
