package com.paddleocr.web.service;

import com.paddleocr.web.mapper.entity.UserDO;
import com.paddleocr.web.model.dto.RegisterDTO;
import com.paddleocr.web.model.vo.UserInfoVO;

import java.util.List;


/**
 * 用户服务（认证域）：注册、查询、锁定策略、角色编码查询
 *
 * @author paddleocr
 * @date 2026-09-22
 */
public interface UserService {

    /**
     * 注册账号（默认挂 DEFAULT 机构、自动绑定 USER 角色）
     *
     * @param dto 注册请求
     * @return 注册后用户信息（机构 + 角色编码集合，注册后角色恒为 ["USER"]）
     */
    UserInfoVO register(RegisterDTO dto);

    /**
     * 按用户名查询用户（供认证装载）
     *
     * @param username 用户名
     * @return 用户 DO，不存在时返回 null
     */
    UserDO getByUsername(String username);

    /**
     * 组装用户信息出参（机构 + 角色编码集合，实时查库）
     *
     * @param userId 用户 ID
     * @return 用户信息 VO
     */
    UserInfoVO getUserInfo(Long userId);

    /**
     * 查询用户启用的角色编码集合（角色每次查库，撤权即时生效）
     *
     * @param userId 用户 ID
     * @return 角色编码集合，无绑定时返回空集合
     */
    List<String> listRoleCodes(Long userId);

    /**
     * 记录一次登录失败（SQL 原子自增，达阈值自动写锁定到期时间）
     *
     * @param userId 用户 ID
     */
    void recordLoginFailure(Long userId);

    /**
     * 登录成功：清零失败计数并写入最近登录时间/IP（各自短事务，不包 BCrypt 校验）
     *
     * @param userId 用户 ID
     * @param loginIp 登录 IP
     */
    void recordLoginSuccess(Long userId, String loginIp);
}
