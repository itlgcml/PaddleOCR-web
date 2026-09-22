package com.paddleocr.web.service.system.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import com.paddleocr.web.mapper.RoleMapper;
import com.paddleocr.web.mapper.UserRoleMapper;
import com.paddleocr.web.mapper.entity.RoleDO;
import com.paddleocr.web.mapper.entity.UserRoleDO;
import com.paddleocr.web.model.PageResultVO;
import com.paddleocr.web.model.dto.RoleSaveDTO;
import com.paddleocr.web.model.dto.RoleUpdateDTO;
import com.paddleocr.web.model.vo.RoleVO;
import com.paddleocr.web.service.system.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色服务实现（系统设置域）：分页增删改、内置角色保护、绑定校验
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    /** 内置角色标记值 */
    private static final int BUILT_IN = 1;

    /** 非内置角色标记值 */
    private static final int NOT_BUILT_IN = 0;

    /** 启用状态值 */
    private static final int STATUS_ENABLED = 1;

    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;

    @Override
    public PageResultVO<RoleVO> pageRoles(int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<RoleDO> wrapper = new LambdaQueryWrapper<RoleDO>()
                .orderByAsc(RoleDO::getId);
        if (StrUtil.isNotBlank(keyword)) {
            // 页面搜索严禁左模糊/全模糊，统一前缀匹配
            wrapper.and(w -> w.likeRight(RoleDO::getRoleCode, keyword)
                    .or()
                    .likeRight(RoleDO::getRoleName, keyword));
        }
        Page<RoleDO> page = roleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<RoleVO> records = page.getRecords().stream()
                .map(this::toRoleVO)
                .collect(Collectors.toList());
        return buildPageResult(records, page);
    }

    @Override
    public List<RoleVO> listEnabledRoles() {
        return roleMapper.selectList(new LambdaQueryWrapper<RoleDO>()
                        .eq(RoleDO::getStatus, STATUS_ENABLED)
                        .orderByAsc(RoleDO::getRoleCode))
                .stream()
                .map(this::toRoleVO)
                .collect(Collectors.toList());
    }

    @Override
    public String saveRole(RoleSaveDTO dto) {
        Long sameCodeCount = roleMapper.selectCount(
                new LambdaQueryWrapper<RoleDO>().eq(RoleDO::getRoleCode, dto.getRoleCode()));
        if (sameCodeCount != null && sameCodeCount > 0L) {
            // 内置 ADMIN/USER 恒存在，撞唯一索引同时覆盖"不得为 ADMIN/USER"约束
            throw new BusinessException(ResultCode.ROLE_CODE_EXISTS);
        }
        RoleDO role = new RoleDO();
        role.setRoleCode(dto.getRoleCode());
        role.setRoleName(dto.getRoleName());
        role.setDescription(dto.getDescription());
        role.setStatus(STATUS_ENABLED);
        role.setBuiltIn(NOT_BUILT_IN);
        roleMapper.insert(role);
        log.info("角色新增成功, roleId={}, roleCode={}", role.getId(), role.getRoleCode());
        return String.valueOf(role.getId());
    }

    @Override
    public void updateRole(Long id, RoleUpdateDTO dto) {
        RoleDO role = getExistingRole(id);
        if (Integer.valueOf(BUILT_IN).equals(role.getBuiltIn())) {
            throw new BusinessException(ResultCode.ROLE_BUILT_IN);
        }
        LambdaUpdateWrapper<RoleDO> wrapper = new LambdaUpdateWrapper<RoleDO>()
                .eq(RoleDO::getId, id)
                .set(RoleDO::getRoleName, dto.getRoleName())
                .set(dto.getDescription() != null, RoleDO::getDescription, dto.getDescription())
                .set(dto.getStatus() != null, RoleDO::getStatus, dto.getStatus());
        roleMapper.update(null, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeRole(Long id) {
        RoleDO role = getExistingRole(id);
        if (Integer.valueOf(BUILT_IN).equals(role.getBuiltIn())) {
            throw new BusinessException(ResultCode.ROLE_BUILT_IN);
        }
        Long bindingCount = userRoleMapper.selectCount(
                new LambdaQueryWrapper<UserRoleDO>().eq(UserRoleDO::getRoleId, id));
        if (bindingCount != null && bindingCount > 0L) {
            throw new BusinessException(ResultCode.ROLE_IN_USE);
        }
        roleMapper.deleteById(id);
        log.info("角色删除成功, roleId={}, roleCode={}", id, role.getRoleCode());
    }

    /**
     * 查询存在的角色（已删/不存在统一 40034）
     */
    private RoleDO getExistingRole(Long id) {
        RoleDO role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ResultCode.ROLE_NOT_FOUND);
        }
        return role;
    }

    /**
     * DO 转 VO
     */
    private RoleVO toRoleVO(RoleDO role) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setRoleCode(role.getRoleCode());
        vo.setRoleName(role.getRoleName());
        vo.setDescription(role.getDescription());
        vo.setStatus(role.getStatus());
        vo.setBuiltIn(role.getBuiltIn());
        vo.setCreateTime(role.getCreateTime());
        return vo;
    }

    /**
     * 组装分页结果
     */
    private PageResultVO<RoleVO> buildPageResult(List<RoleVO> records, Page<RoleDO> page) {
        PageResultVO<RoleVO> result = new PageResultVO<>();
        result.setRecords(records);
        result.setTotal(page.getTotal());
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        return result;
    }
}
