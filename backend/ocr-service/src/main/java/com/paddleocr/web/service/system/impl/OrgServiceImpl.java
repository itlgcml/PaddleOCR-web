package com.paddleocr.web.service.system.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import com.paddleocr.web.mapper.OrgMapper;
import com.paddleocr.web.mapper.UserMapper;
import com.paddleocr.web.mapper.entity.OrgDO;
import com.paddleocr.web.mapper.entity.UserDO;
import com.paddleocr.web.model.dto.OrgSaveDTO;
import com.paddleocr.web.model.dto.OrgUpdateDTO;
import com.paddleocr.web.model.vo.OrgTreeVO;
import com.paddleocr.web.service.system.OrgService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 机构服务实现（系统设置域）：树查询、增删改、三重删除校验
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgServiceImpl implements OrgService {

    /** 根节点父 ID 约定值 */
    private static final long ROOT_PARENT_ID = 0L;

    /** 启用状态值 */
    private static final int STATUS_ENABLED = 1;

    /** 默认排序值 */
    private static final int DEFAULT_SORT = 0;

    private final OrgMapper orgMapper;
    private final UserMapper userMapper;

    @Override
    public List<OrgTreeVO> listTree() {
        // 机构数 < 数千，一次查全表内存组树（按 sort,id 排序）
        List<OrgDO> all = orgMapper.selectList(
                new LambdaQueryWrapper<OrgDO>()
                        .orderByAsc(OrgDO::getSort)
                        .orderByAsc(OrgDO::getId));
        Map<Long, List<OrgDO>> byParent = all.stream()
                .collect(Collectors.groupingBy(OrgDO::getParentId));
        List<OrgDO> roots = byParent.getOrDefault(ROOT_PARENT_ID, Collections.emptyList());
        return roots.stream().map(org -> buildNode(org, byParent)).collect(Collectors.toList());
    }

    @Override
    public String saveOrg(OrgSaveDTO dto) {
        OrgDO parent = orgMapper.selectById(dto.getParentId());
        if (parent == null || !Integer.valueOf(STATUS_ENABLED).equals(parent.getStatus())) {
            throw new BusinessException(ResultCode.ORG_NOT_FOUND);
        }
        Long sameCodeCount = orgMapper.selectCount(
                new LambdaQueryWrapper<OrgDO>().eq(OrgDO::getOrgCode, dto.getOrgCode()));
        if (sameCodeCount != null && sameCodeCount > 0L) {
            throw new BusinessException(ResultCode.ORG_CODE_EXISTS);
        }
        OrgDO org = new OrgDO();
        org.setParentId(parent.getId());
        // 祖先链 = 父.ancestors + "," + 父.id
        org.setAncestors(parent.getAncestors() + "," + parent.getId());
        org.setOrgCode(dto.getOrgCode());
        org.setOrgName(dto.getOrgName());
        org.setSort(dto.getSort() == null ? DEFAULT_SORT : dto.getSort());
        org.setStatus(STATUS_ENABLED);
        orgMapper.insert(org);
        log.info("机构新增成功, orgId={}, orgCode={}", org.getId(), org.getOrgCode());
        return String.valueOf(org.getId());
    }

    @Override
    public void updateOrg(Long id, OrgUpdateDTO dto) {
        OrgDO org = orgMapper.selectById(id);
        if (org == null) {
            throw new BusinessException(ResultCode.ORG_NOT_FOUND);
        }
        // orgCode、parent_id 本期不允许改（简化级联）
        LambdaUpdateWrapper<OrgDO> wrapper = new LambdaUpdateWrapper<OrgDO>()
                .eq(OrgDO::getId, id)
                .set(OrgDO::getOrgName, dto.getOrgName())
                .set(dto.getSort() != null, OrgDO::getSort, dto.getSort())
                .set(dto.getStatus() != null, OrgDO::getStatus, dto.getStatus());
        orgMapper.update(null, wrapper);
    }

    @Override
    public void removeOrg(Long id) {
        OrgDO org = orgMapper.selectById(id);
        if (org == null) {
            throw new BusinessException(ResultCode.ORG_NOT_FOUND);
        }
        Long childCount = orgMapper.selectCount(
                new LambdaQueryWrapper<OrgDO>().eq(OrgDO::getParentId, id));
        if (childCount != null && childCount > 0L) {
            throw new BusinessException(ResultCode.ORG_HAS_CHILDREN);
        }
        Long userCount = userMapper.selectCount(
                new LambdaQueryWrapper<UserDO>().eq(UserDO::getOrgId, id));
        if (userCount != null && userCount > 0L) {
            throw new BusinessException(ResultCode.ORG_HAS_USERS);
        }
        orgMapper.deleteById(id);
        log.info("机构删除成功, orgId={}, orgCode={}", id, org.getOrgCode());
    }

    /**
     * 递归组装机构树节点（叶子 children 为空数组）
     */
    private OrgTreeVO buildNode(OrgDO org, Map<Long, List<OrgDO>> byParent) {
        OrgTreeVO node = new OrgTreeVO();
        node.setId(org.getId());
        node.setParentId(org.getParentId());
        node.setOrgCode(org.getOrgCode());
        node.setOrgName(org.getOrgName());
        node.setSort(org.getSort());
        node.setStatus(org.getStatus());
        List<OrgDO> children = byParent.getOrDefault(org.getId(), Collections.emptyList());
        List<OrgTreeVO> childNodes = new ArrayList<>(children.size());
        for (OrgDO child : children) {
            childNodes.add(buildNode(child, byParent));
        }
        node.setChildren(childNodes);
        return node;
    }
}
