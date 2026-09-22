package com.paddleocr.web.config;

import com.paddleocr.web.security.AuthProperties;
import com.paddleocr.web.security.JwtAuthenticationFilter;
import com.paddleocr.web.security.RestAccessDeniedHandler;
import com.paddleocr.web.security.RestAuthEntryPoint;
import com.paddleocr.web.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置：认证与请求拦截统一收口过滤器链
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** 匿名放行白名单：注册/登录/刷新/健康检查 */
    private static final String[] AUTH_WHITELIST = {
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/health"
    };

    /** BCrypt 加密强度（与 ocr-service 的 PasswordSupport 保持一致） */
    private static final int BCRYPT_STRENGTH = 10;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthEntryPoint restAuthEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;
    private final UserDetailsServiceImpl userDetailsService;
    private final AuthProperties authProperties;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // 降级开关：关闭时 /api/** 全量放行（软鉴权，生产慎用）
        String[] permitPaths = Boolean.TRUE.equals(authProperties.getEnabled())
                ? AUTH_WHITELIST : new String[]{"/api/**"};
        http.csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()
                // 预检放行（CorsConfig 为 MVC 层实现，Security 侧不启用 http.cors()，两套配置不叠加）
                .antMatchers(HttpMethod.OPTIONS).permitAll()
                .antMatchers(permitPaths).permitAll()
                // 系统设置域（机构/角色/用户管理）仅 ADMIN，URL 级统一收口（不用方法级 @PreAuthorize）
                .antMatchers("/api/orgs/**", "/api/roles/**", "/api/users/**").hasRole("ADMIN")
                .antMatchers("/api/**").authenticated()
                // /druid、/swagger-ui 等非 /api 路径不在链上管
                .anyRequest().permitAll()
                .and()
                .exceptionHandling()
                .authenticationEntryPoint(restAuthEntryPoint)
                .accessDeniedHandler(restAccessDeniedHandler)
                .and()
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(BCryptPasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    /**
     * 关闭 JwtAuthenticationFilter 的 Servlet 容器自动注册（Filter Bean 会被 Boot 自动注册导致双跑），
     * 过滤器统一由 SecurityFilterChain 编排，不走 FilterRegistrationBean 裸注册
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
