package com.paddleocr.web.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paddleocr.web.PasswordSupport;
import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import com.paddleocr.web.mapper.OrgMapper;
import com.paddleocr.web.mapper.RoleMapper;
import com.paddleocr.web.mapper.UserMapper;
import com.paddleocr.web.mapper.UserRoleMapper;
import com.paddleocr.web.mapper.entity.OrgDO;
import com.paddleocr.web.mapper.entity.RoleDO;
import com.paddleocr.web.mapper.entity.UserDO;
import com.paddleocr.web.mapper.entity.UserRoleDO;
import com.paddleocr.web.model.dto.RegisterDTO;
import com.paddleocr.web.model.vo.OrgBriefVO;
import com.paddleocr.web.model.vo.UserInfoVO;
import com.paddleocr.web.security.AuthProperties;
import com.paddleocr.web.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户服务实现（认证域）
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    /** 注册默认角色编码 */
    private static final String DEFAULT_ROLE_CODE = "USER";

    /** 注册缺省机构编码 */
    private static final String DEFAULT_ORG_CODE = "DEFAULT";

    /** 启用状态值 */
    private static final int STATUS_ENABLED = 1;

    private final UserMapper userMapper;
    private final OrgMapper orgMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final AuthProperties authProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserInfoVO register(RegisterDTO dto) {
        // 用户名唯一性校验（并发兜底靠 DuplicateKeyException → USERNAME_EXISTS）
        Long sameNameCount = userMapper.selectCount(
                new LambdaQueryWrapper<UserDO>().eq(UserDO::getUsername, dto.getUsername()));
        if (sameNameCount != null && sameNameCount > 0L) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }
        Long orgId = resolveRegisterOrgId(dto.getOrgId());

        UserDO user = new UserDO();
        user.setOrgId(orgId);
        user.setUsername(dto.getUsername());
        user.setNickname(StrUtil.isBlank(dto.getNickname()) ? dto.getUsername() : dto.getNickname());
        user.setEmail(dto.getEmail());
        user.setPasswordHash(PasswordSupport.encode(dto.getPassword()));
        user.setStatus(STATUS_ENABLED);
        user.setFailedAttempts(0);
        userMapper.insert(user);

        bindDefaultRole(user.getId());
        log.info("用户注册成功, userId={}, username={}", user.getId(), user.getUsername());
        return getUserInfo(user.getId());
    }

    @Override
    public UserDO getByUsername(String username) {
        // eq 条件自动带 deleted=0
        return userMapper.selectOne(
                new LambdaQueryWrapper<UserDO>().eq(UserDO::getUsername, username));
    }

    @Override
    public UserInfoVO getUserInfo(Long userId) {
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        UserInfoVO vo = new UserInfoVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setOrg(buildOrgBrief(user.getOrgId()));
        vo.setRoles(listRoleCodes(userId));
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }

    @Override
    public List<String> listRoleCodes(Long userId) {
        // n:m 两次单表查询内存组装（多表 join 才写 XML）
        List<UserRoleDO> bindings = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRoleDO>().eq(UserRoleDO::getUserId, userId));
        if (bindings.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> roleIds = bindings.stream().map(UserRoleDO::getRoleId).distinct()
                .collect(Collectors.toList());
        return roleMapper.selectBatchIds(roleIds).stream()
                .filter(role -> Integer.valueOf(STATUS_ENABLED).equals(role.getStatus()))
                .map(RoleDO::getRoleCode)
                .collect(Collectors.toList());
    }

    @Override
    public void recordLoginFailure(Long userId) {
        // SQL 原子自增，禁止先查后写的 read-modify-write（并发会丢失更新、绕过阈值）
        userMapper.increaseFailedAttempts(userId,
                authProperties.getMaxFailedAttempts(),
                authProperties.getLockDurationMinutes());
    }

    @Override
    public void recordLoginSuccess(Long userId, String loginIp) {
        // 各自短事务的单条 UPDATE：清零失败计数 + 写入登录信息
        userMapper.resetLoginFailure(userId);
        userMapper.updateLoginInfo(userId, LocalDateTime.now(), loginIp);
    }

    /**
     * 解析注册机构：null 取 DEFAULT；非空校验存在且启用
     */
    private Long resolveRegisterOrgId(Long orgId) {
        if (orgId == null) {
            OrgDO defaultOrg = orgMapper.selectOne(
                    new LambdaQueryWrapper<OrgDO>().eq(OrgDO::getOrgCode, DEFAULT_ORG_CODE));
            if (defaultOrg == null) {
                log.error("默认机构 {} 缺失，请检查初始化数据", DEFAULT_ORG_CODE);
                throw new BusinessException(ResultCode.ORG_NOT_FOUND);
            }
            return defaultOrg.getId();
        }
        OrgDO org = orgMapper.selectById(orgId);
        if (org == null || !Integer.valueOf(STATUS_ENABLED).equals(org.getStatus())) {
            throw new BusinessException(ResultCode.ORG_NOT_FOUND);
        }
        return org.getId();
    }

    /**
     * 绑定注册默认角色 USER（保证 /me 永远有角色返回、鉴权语义完整）
     */
    private void bindDefaultRole(Long userId) {
        RoleDO role = roleMapper.selectOne(
                new LambdaQueryWrapper<RoleDO>().eq(RoleDO::getRoleCode, DEFAULT_ROLE_CODE));
        if (role == null) {
            log.error("内置角色 {} 缺失，请检查初始化数据", DEFAULT_ROLE_CODE);
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
        UserRoleDO binding = new UserRoleDO();
        binding.setUserId(userId);
        binding.setRoleId(role.getId());
        userRoleMapper.insert(binding);
    }

    /**
     * 组装机构简要信息（未分配时返回 null）
     */
    private OrgBriefVO buildOrgBrief(Long orgId) {
        if (orgId == null) {
            return null;
        }
        OrgDO org = orgMapper.selectById(orgId);
        if (org == null) {
            return null;
        }
        OrgBriefVO brief = new OrgBriefVO();
        brief.setId(org.getId());
        brief.setOrgName(org.getOrgName());
        return brief;
    }
}
