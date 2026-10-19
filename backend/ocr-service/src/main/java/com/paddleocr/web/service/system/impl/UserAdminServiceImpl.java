package com.paddleocr.web.service.system.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.paddleocr.web.model.PageResultVO;
import com.paddleocr.web.model.dto.UserCreateDTO;
import com.paddleocr.web.model.dto.UserPageQuery;
import com.paddleocr.web.model.vo.OrgBriefVO;
import com.paddleocr.web.model.vo.RoleBriefVO;
import com.paddleocr.web.model.vo.UserPageVO;
import com.paddleocr.web.service.system.OrgService;
import com.paddleocr.web.service.system.UserAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户管理服务实现（系统设置域）：分页查询、机构调整、角色全量替换、启禁用
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAdminServiceImpl implements UserAdminService {

    /** 默认页码 */
    private static final int DEFAULT_PAGE_NUM = 1;

    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 每页条数上限 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 启用状态值 */
    private static final int STATUS_ENABLED = 1;

    /** 新增用户缺省机构编码（与注册保持一致） */
    private static final String DEFAULT_ORG_CODE = "DEFAULT";

    /** 新增用户缺省角色编码（与注册保持一致） */
    private static final String DEFAULT_ROLE_CODE = "USER";

    private final UserMapper userMapper;
    private final OrgMapper orgMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final OrgService orgService;

    @Override
    public PageResultVO<UserPageVO> pageUsers(UserPageQuery query) {
        int pageNum = query.getPageNum() == null ? DEFAULT_PAGE_NUM : Math.max(query.getPageNum(), DEFAULT_PAGE_NUM);
        int pageSize = query.getPageSize() == null ? DEFAULT_PAGE_SIZE
                : Math.min(Math.max(query.getPageSize(), 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<UserDO>()
                .eq(query.getStatus() != null, UserDO::getStatus, query.getStatus())
                .orderByDesc(UserDO::getId);
        if (query.getOrgId() != null) {
            // 机构过滤含全部子孙机构：机构树点击父级需一并查出下级用户
            wrapper.in(UserDO::getOrgId, orgService.listSubtreeOrgIds(query.getOrgId()));
        }
        if (StrUtil.isNotBlank(query.getUsername())) {
            // 用户名/昵称前缀匹配（页面搜索严禁左模糊/全模糊）
            wrapper.and(w -> w.likeRight(UserDO::getUsername, query.getUsername())
                    .or()
                    .likeRight(UserDO::getNickname, query.getUsername()));
        }
        Page<UserDO> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<UserPageVO> records = buildUserPageVOList(page.getRecords());
        return buildPageResult(records, page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String saveUser(UserCreateDTO dto) {
        // 用户名唯一性校验（并发兜底靠 DuplicateKeyException → USERNAME_EXISTS）
        Long sameNameCount = userMapper.selectCount(
                new LambdaQueryWrapper<UserDO>().eq(UserDO::getUsername, dto.getUsername()));
        if (sameNameCount != null && sameNameCount > 0L) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }

        UserDO user = new UserDO();
        user.setOrgId(resolveOrgId(dto.getOrgId()));
        user.setUsername(dto.getUsername());
        user.setNickname(StrUtil.isBlank(dto.getNickname()) ? dto.getUsername() : dto.getNickname());
        user.setEmail(dto.getEmail());
        user.setPasswordHash(PasswordSupport.encode(dto.getPassword()));
        user.setStatus(STATUS_ENABLED);
        user.setFailedAttempts(0);
        userMapper.insert(user);

        bindRoles(user.getId(), dto.getRoleIds());
        log.info("新增用户成功, userId={}, username={}", user.getId(), user.getUsername());
        return String.valueOf(user.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserOrg(Long userId, Long orgId) {
        getExistingUser(userId);
        OrgDO org = orgMapper.selectById(orgId);
        if (org == null || !Integer.valueOf(STATUS_ENABLED).equals(org.getStatus())) {
            throw new BusinessException(ResultCode.ORG_NOT_FOUND);
        }
        userMapper.update(null, new LambdaUpdateWrapper<UserDO>()
                .eq(UserDO::getId, userId)
                .set(UserDO::getOrgId, orgId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserRoles(Long userId, List<Long> roleIds) {
        getExistingUser(userId);
        // 重复元素服务端去重（防撞 uk_user_role 唯一索引）
        Set<Long> distinctRoleIds = roleIds == null
                ? Collections.emptySet() : new LinkedHashSet<>(roleIds);
        // 逐个校验存在且启用
        for (Long roleId : distinctRoleIds) {
            RoleDO role = roleMapper.selectById(roleId);
            if (role == null || !Integer.valueOf(STATUS_ENABLED).equals(role.getStatus())) {
                throw new BusinessException(ResultCode.ROLE_NOT_FOUND);
            }
        }
        // 全量替换：事务内物理 delete + batch insert
        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleDO>().eq(UserRoleDO::getUserId, userId));
        for (Long roleId : distinctRoleIds) {
            insertUserRole(userId, roleId);
        }
        log.info("用户角色全量替换完成, userId={}, roleIds={}", userId, distinctRoleIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(Long userId, Integer status) {
        getExistingUser(userId);
        userMapper.update(null, new LambdaUpdateWrapper<UserDO>()
                .eq(UserDO::getId, userId)
                .set(UserDO::getStatus, status));
        log.info("用户状态更新, userId={}, status={}", userId, status);
    }

    /**
     * 批量组装用户分页记录（机构/角色两次单表集合查询内存组装）
     */
    private List<UserPageVO> buildUserPageVOList(List<UserDO> users) {
        if (users.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> orgIds = users.stream().map(UserDO::getOrgId)
                .filter(orgId -> orgId != null).collect(Collectors.toSet());
        Map<Long, OrgDO> orgMap = orgIds.isEmpty() ? Collections.emptyMap()
                : orgMapper.selectBatchIds(orgIds).stream()
                        .collect(Collectors.toMap(OrgDO::getId, org -> org));

        List<Long> userIds = users.stream().map(UserDO::getId).collect(Collectors.toList());
        List<UserRoleDO> bindings = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRoleDO>().in(UserRoleDO::getUserId, userIds));
        Map<Long, List<RoleBriefVO>> roleMap = buildRoleMap(bindings);

        return users.stream().map(user -> {
            UserPageVO vo = new UserPageVO();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
            vo.setEmail(user.getEmail());
            vo.setOrg(buildOrgBrief(user.getOrgId(), orgMap));
            vo.setRoles(roleMap.getOrDefault(user.getId(), Collections.emptyList()));
            vo.setStatus(user.getStatus());
            vo.setLastLoginTime(user.getLastLoginTime());
            vo.setCreateTime(user.getCreateTime());
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 按用户 ID 分组组装角色简要信息
     */
    private Map<Long, List<RoleBriefVO>> buildRoleMap(List<UserRoleDO> bindings) {
        if (bindings.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> roleIds = bindings.stream().map(UserRoleDO::getRoleId)
                .distinct().collect(Collectors.toSet());
        Map<Long, RoleDO> roleById = roleMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(RoleDO::getId, role -> role));
        Map<Long, List<RoleBriefVO>> grouped = bindings.stream()
                .filter(b -> roleById.containsKey(b.getRoleId()))
                .collect(Collectors.groupingBy(UserRoleDO::getUserId,
                        Collectors.mapping(b -> toRoleBrief(roleById.get(b.getRoleId())),
                                Collectors.toList())));
        return grouped;
    }

    /**
     * 解析新增用户的所属机构：null 取 DEFAULT；非空校验存在且启用
     */
    private Long resolveOrgId(Long orgId) {
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
     * 绑定新增用户的角色：缺省绑默认角色 USER；指定则逐个校验存在且启用后绑定（入参去重）
     */
    private void bindRoles(Long userId, List<Long> roleIds) {
        Set<Long> distinctRoleIds = roleIds == null
                ? Collections.emptySet() : new LinkedHashSet<>(roleIds);
        if (distinctRoleIds.isEmpty()) {
            RoleDO defaultRole = roleMapper.selectOne(
                    new LambdaQueryWrapper<RoleDO>().eq(RoleDO::getRoleCode, DEFAULT_ROLE_CODE));
            if (defaultRole == null) {
                log.error("内置角色 {} 缺失，请检查初始化数据", DEFAULT_ROLE_CODE);
                throw new BusinessException(ResultCode.SYSTEM_ERROR);
            }
            insertUserRole(userId, defaultRole.getId());
            return;
        }
        for (Long roleId : distinctRoleIds) {
            RoleDO role = roleMapper.selectById(roleId);
            if (role == null || !Integer.valueOf(STATUS_ENABLED).equals(role.getStatus())) {
                throw new BusinessException(ResultCode.ROLE_NOT_FOUND);
            }
            insertUserRole(userId, roleId);
        }
    }

    /**
     * 写入用户-角色绑定关系
     */
    private void insertUserRole(Long userId, Long roleId) {
        UserRoleDO binding = new UserRoleDO();
        binding.setUserId(userId);
        binding.setRoleId(roleId);
        userRoleMapper.insert(binding);
    }

    /**
     * 查询存在的用户（已删/不存在统一 40032）
     */
    private UserDO getExistingUser(Long userId) {
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }

    /**
     * DO 转角色简要 VO
     */
    private RoleBriefVO toRoleBrief(RoleDO role) {
        RoleBriefVO brief = new RoleBriefVO();
        brief.setId(role.getId());
        brief.setRoleCode(role.getRoleCode());
        brief.setRoleName(role.getRoleName());
        return brief;
    }

    /**
     * 组装机构简要信息（未分配时返回 null）
     */
    private OrgBriefVO buildOrgBrief(Long orgId, Map<Long, OrgDO> orgMap) {
        OrgDO org = orgId == null ? null : orgMap.get(orgId);
        if (org == null) {
            return null;
        }
        OrgBriefVO brief = new OrgBriefVO();
        brief.setId(org.getId());
        brief.setOrgName(org.getOrgName());
        return brief;
    }

    /**
     * 组装分页结果
     */
    private PageResultVO<UserPageVO> buildPageResult(List<UserPageVO> records, Page<UserDO> page) {
        PageResultVO<UserPageVO> result = new PageResultVO<>();
        result.setRecords(records);
        result.setTotal(page.getTotal());
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        return result;
    }
}
