package com.paddleocr.web.service.approval.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import com.paddleocr.web.mapper.FlowCcMapper;
import com.paddleocr.web.mapper.FlowInstanceMapper;
import com.paddleocr.web.mapper.entity.FlowCcDO;
import com.paddleocr.web.mapper.entity.FlowInstanceDO;
import com.paddleocr.web.model.PageResultVO;
import com.paddleocr.web.model.dto.RuntimePageQuery;
import com.paddleocr.web.model.vo.CcVO;
import com.paddleocr.web.service.approval.FlowCcService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 抄送服务实现
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Service
@RequiredArgsConstructor
public class FlowCcServiceImpl implements FlowCcService {

    private static final String CC_UNREAD = "UNREAD";
    private static final String CC_READ = "READ";

    private final FlowCcMapper flowCcMapper;
    private final FlowInstanceMapper flowInstanceMapper;

    @Override
    public PageResultVO<CcVO> pageMine(RuntimePageQuery query, Long userId, boolean read) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10
                : Math.min(query.getPageSize(), 100);
        List<Long> keywordInstanceIds = keywordInstanceIds(query.getKeyword());
        if (keywordInstanceIds != null && keywordInstanceIds.isEmpty()) {
            PageResultVO<CcVO> empty = new PageResultVO<>();
            empty.setRecords(java.util.Collections.emptyList());
            empty.setTotal(0L);
            empty.setPageNum(pageNum);
            empty.setPageSize(pageSize);
            return empty;
        }
        Page<FlowCcDO> page = flowCcMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<FlowCcDO>()
                        .eq(FlowCcDO::getReceiverId, userId)
                        .eq(FlowCcDO::getStatus, read ? CC_READ : CC_UNREAD)
                        .in(keywordInstanceIds != null, FlowCcDO::getInstanceId, keywordInstanceIds)
                        .orderByDesc(FlowCcDO::getCreateTime));
        List<FlowCcDO> records = page.getRecords();
        Map<Long, FlowInstanceDO> instanceMap = loadInstances(records);
        List<CcVO> vos = records.stream()
                .map(cc -> toCcVO(cc, instanceMap.get(cc.getInstanceId())))
                .collect(Collectors.toList());
        PageResultVO<CcVO> result = new PageResultVO<>();
        result.setRecords(vos);
        result.setTotal(page.getTotal());
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long ccId, Long userId) {
        FlowCcDO cc = flowCcMapper.selectById(ccId);
        if (cc == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "抄送记录不存在");
        }
        if (!cc.getReceiverId().equals(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅接收人本人可标记已阅");
        }
        if (CC_READ.equals(cc.getStatus())) {
            return;
        }
        FlowCcDO update = new FlowCcDO();
        update.setId(cc.getId());
        update.setStatus(CC_READ);
        update.setReadTime(LocalDateTime.now());
        flowCcMapper.updateById(update);
    }

    private Map<Long, FlowInstanceDO> loadInstances(List<FlowCcDO> records) {
        if (records.isEmpty()) {
            return new HashMap<>(0);
        }
        List<Long> instanceIds = records.stream().map(FlowCcDO::getInstanceId).distinct()
                .collect(Collectors.toList());
        return flowInstanceMapper.selectBatchIds(instanceIds).stream()
                .collect(Collectors.toMap(FlowInstanceDO::getId, instance -> instance, (a, b) -> a));
    }

    /**
     * keyword 命中的实例 ID（业务标题/流程名称前缀匹配）；null=不过滤，空列表=无命中
     */
    private List<Long> keywordInstanceIds(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        String trimmed = keyword.trim();
        return flowInstanceMapper.selectList(new LambdaQueryWrapper<FlowInstanceDO>()
                        .and(wrapper -> wrapper
                                .likeRight(FlowInstanceDO::getBizTitle, trimmed)
                                .or()
                                .likeRight(FlowInstanceDO::getProcessName, trimmed)))
                .stream()
                .map(FlowInstanceDO::getId)
                .distinct()
                .collect(Collectors.toList());
    }

    private CcVO toCcVO(FlowCcDO cc, FlowInstanceDO instance) {
        CcVO vo = new CcVO();
        vo.setId(String.valueOf(cc.getId()));
        vo.setInstanceId(String.valueOf(cc.getInstanceId()));
        vo.setCreateTime(cc.getCreateTime());
        vo.setReadTime(cc.getReadTime());
        if (instance != null) {
            vo.setProcessName(instance.getProcessName());
            vo.setBizType(instance.getBizType());
            vo.setBizId(String.valueOf(instance.getBizId()));
            vo.setBizTitle(instance.getBizTitle());
            vo.setFromUserName(instance.getStartUserName());
        }
        return vo;
    }
}
