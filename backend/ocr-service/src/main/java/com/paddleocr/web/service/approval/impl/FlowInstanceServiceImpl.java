package com.paddleocr.web.service.approval.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import com.paddleocr.web.mapper.FlowInstanceMapper;
import com.paddleocr.web.mapper.ProcessMapper;
import com.paddleocr.web.mapper.UserMapper;
import com.paddleocr.web.mapper.entity.FlowInstanceDO;
import com.paddleocr.web.mapper.entity.ProcessDO;
import com.paddleocr.web.mapper.entity.ProcessVersionDO;
import com.paddleocr.web.mapper.entity.UserDO;
import com.paddleocr.web.model.PageResultVO;
import com.paddleocr.web.model.dto.InstanceCancelDTO;
import com.paddleocr.web.model.dto.InstanceStartDTO;
import com.paddleocr.web.model.dto.NextApproverQueryDTO;
import com.paddleocr.web.model.dto.NodeButtonsQueryDTO;
import com.paddleocr.web.model.dto.RuntimePageQuery;
import com.paddleocr.web.model.vo.BizApprovalStatusVO;
import com.paddleocr.web.model.vo.InstanceDetailVO;
import com.paddleocr.web.model.vo.InstanceItemVO;
import com.paddleocr.web.model.vo.NextApproverVO;
import com.paddleocr.web.model.vo.NodeButtonsVO;
import com.paddleocr.web.model.vo.StartableProcessVO;
import com.paddleocr.web.model.vo.UserBriefVO;
import com.paddleocr.web.service.approval.ApprovalRuntimeProperties;
import com.paddleocr.web.service.approval.FlowInstanceService;
import com.paddleocr.web.service.approval.InstanceStatus;
import com.paddleocr.web.service.approval.ProcessStatus;
import com.paddleocr.web.service.approval.biz.BizSnapshot;
import com.paddleocr.web.service.approval.biz.BizSnapshotLoader;
import com.paddleocr.web.service.approval.engine.FlowApproverResolver;
import com.paddleocr.web.service.approval.engine.FlowDesignCursor;
import com.paddleocr.web.service.approval.engine.FlowEngine;
import com.paddleocr.web.service.approval.engine.FlowEngineSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.idOf;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.nameOf;
import static com.paddleocr.web.service.approval.engine.FlowDesignCursor.typeOf;

/**
 * 审批实例服务实现
 *
 * <p>发起采用单据级发起锁（§7.6）：锁内完成 流程发布/发起范围/单据存在/RUNNING 唯一性校验后委托引擎，
 * 与实例级推进锁配合规避并发重复发起与推进竞态。</p>
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowInstanceServiceImpl implements FlowInstanceService {

    private static final String LOCK_KEY_START_PREFIX = "approval:instance:start:";
    private static final long LOCK_WAIT_SECONDS = 0L;

    private static final String INITIATOR_ALL = "ALL";
    private static final String INITIATOR_SPECIFIC = "SPECIFIC";

    /** conditions Map 序列化后长度上限（§9.12） */
    private static final int CONDITIONS_MAX_LENGTH = 4096;

    /** bizStatus 批量上限（§9.6） */
    private static final int MAX_BIZ_IDS = 20;

    private final FlowInstanceMapper flowInstanceMapper;
    private final ProcessMapper processMapper;
    private final UserMapper userMapper;
    private final FlowEngine engine;
    private final FlowEngineSupport support;
    private final FlowApproverResolver resolver;
    private final InstanceDetailAssembler assembler;
    private final ApprovalRuntimeProperties properties;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final List<BizSnapshotLoader> snapshotLoaders;

    // ==================== 可发起预检 ====================

    @Override
    public List<StartableProcessVO> startable(Long userId) {
        List<ProcessDO> published = processMapper.selectList(new LambdaQueryWrapper<ProcessDO>()
                .eq(ProcessDO::getStatus, ProcessStatus.PUBLISHED)
                .orderByAsc(ProcessDO::getCreateTime));
        List<StartableProcessVO> result = new ArrayList<>();
        for (ProcessDO process : published) {
            if (!canInitiate(process, userId)) {
                continue;
            }
            StartableProcessVO vo = new StartableProcessVO();
            vo.setId(String.valueOf(process.getId()));
            vo.setCode(process.getCode());
            vo.setName(process.getName());
            vo.setDescription(process.getDescription());
            vo.setDetailPath(process.getDetailPath());
            result.add(vo);
        }
        return result;
    }

    /**
     * 发起范围校验：ALL 直过；SPECIFIC = 指定人员 ∪ 指定角色展开（均要求启用）
     */
    private boolean canInitiate(ProcessDO process, Long userId) {
        if (INITIATOR_ALL.equals(process.getInitiatorType())) {
            return true;
        }
        if (!INITIATOR_SPECIFIC.equals(process.getInitiatorType())) {
            return false;
        }
        JsonNode members = parseJsonArray(process.getInitiatorMembers());
        JsonNode roles = parseJsonArray(process.getInitiatorRoles());
        return resolver.expandUsers(members, roles).stream()
                .anyMatch(user -> userId.equals(user.getId()));
    }

    private JsonNode parseJsonArray(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("发起范围配置解析失败，按无权限处理, processId={}", json, e);
            return null;
        }
    }

    // ==================== 发起 ====================

    @Override
    public String start(InstanceStartDTO dto, Long userId) {
        Long bizId = parseLongId(dto.getBizId(), "单据 ID");
        RLock lock = redissonClient.getLock(LOCK_KEY_START_PREFIX + dto.getBizType() + ":" + bizId);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, properties.getLockLeaseSeconds(), TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(ResultCode.PROCESS_REPEAT_SUBMIT);
            }
            return doStart(dto, userId, bizId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.PROCESS_REPEAT_SUBMIT);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private String doStart(InstanceStartDTO dto, Long userId, Long bizId) {
        ProcessDO process = processMapper.selectOne(new LambdaQueryWrapper<ProcessDO>()
                .eq(ProcessDO::getCode, dto.getProcessCode())
                .last("LIMIT 1"));
        if (process == null || !ProcessStatus.PUBLISHED.equals(process.getStatus())) {
            throw new BusinessException(ResultCode.PROCESS_NOT_PUBLISHED);
        }
        if (!canInitiate(process, userId)) {
            throw new BusinessException(ResultCode.INSTANCE_START_FORBIDDEN);
        }
        UserDO startUser = support.requireUserById(userId);
        BizSnapshotLoader loader = routeLoader(dto.getBizType());
        if (loader == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的单据类型");
        }
        BizSnapshot snapshot = loader.load(bizId);
        if (snapshot == null) {
            throw new BusinessException(ResultCode.INSTANCE_BIZ_NOT_FOUND);
        }
        // 根实例口径（§4.4.5）：子实例 RUNNING 不阻塞单据重新发起（父树必已非 RUNNING 才可能重新发起）
        Long running = flowInstanceMapper.selectCount(new LambdaQueryWrapper<FlowInstanceDO>()
                .eq(FlowInstanceDO::getBizType, dto.getBizType())
                .eq(FlowInstanceDO::getBizId, bizId)
                .eq(FlowInstanceDO::getStatus, InstanceStatus.RUNNING)
                .isNull(FlowInstanceDO::getParentInstanceId));
        if (running != null && running > 0) {
            throw new BusinessException(ResultCode.INSTANCE_ALREADY_RUNNING);
        }
        List<UserDO> ccUsers = resolveCcUsers(dto.getCcUserIds());
        ProcessVersionDO version = support.requirePublishedVersion(process.getId());
        Long instanceId = engine.startInstance(process, version, dto.getBizType(), bizId, startUser,
                snapshot.getTitle(), snapshot.getFormJson(), ccUsers, dto.getComment());
        return String.valueOf(instanceId);
    }

    private BizSnapshotLoader routeLoader(String bizType) {
        for (BizSnapshotLoader loader : snapshotLoaders) {
            if (loader.bizType().equals(bizType)) {
                return loader;
            }
        }
        return null;
    }

    private List<UserDO> resolveCcUsers(List<String> ccUserIds) {
        if (ccUserIds == null || ccUserIds.isEmpty()) {
            return Collections.emptyList();
        }
        if (ccUserIds.size() > properties.getMaxReceivers()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "发起抄送人数量超过上限");
        }
        Map<Long, UserDO> distinct = new LinkedHashMap<>();
        for (String ccUserId : ccUserIds) {
            UserDO user = support.requireUser(ccUserId);
            distinct.putIfAbsent(user.getId(), user);
        }
        return new ArrayList<>(distinct.values());
    }

    // ==================== 我发起的 / 详情 / 撤销 ====================

    @Override
    public PageResultVO<InstanceItemVO> pageMine(RuntimePageQuery query, Long userId) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10
                : Math.min(query.getPageSize(), 100);
        List<Long> keywordInstanceIds = keywordInstanceIds(query.getKeyword());
        if (keywordInstanceIds != null && keywordInstanceIds.isEmpty()) {
            return emptyPage(pageNum, pageSize);
        }
        boolean hasStatus = query.getStatus() != null && !query.getStatus().trim().isEmpty();
        Page<FlowInstanceDO> page = flowInstanceMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<FlowInstanceDO>()
                        .eq(FlowInstanceDO::getStartUserId, userId)
                        // 仅列根实例：子流程实例归并于父实例详情，不单列
                        .isNull(FlowInstanceDO::getParentInstanceId)
                        .eq(hasStatus, FlowInstanceDO::getStatus,
                                hasStatus ? query.getStatus().trim() : null)
                        .in(keywordInstanceIds != null, FlowInstanceDO::getId, keywordInstanceIds)
                        .orderByDesc(FlowInstanceDO::getCreateTime));
        List<FlowInstanceDO> records = page.getRecords();
        List<Long> runningIds = records.stream()
                .filter(instance -> InstanceStatus.RUNNING.equals(instance.getStatus()))
                .map(FlowInstanceDO::getId)
                .collect(java.util.stream.Collectors.toList());
        Map<Long, List<String>> approverNames = assembler.currentApproverNames(runningIds);
        List<InstanceItemVO> vos = new ArrayList<>(records.size());
        for (FlowInstanceDO instance : records) {
            InstanceItemVO vo = new InstanceItemVO();
            vo.setId(String.valueOf(instance.getId()));
            vo.setProcessName(instance.getProcessName());
            vo.setBizType(instance.getBizType());
            vo.setBizId(String.valueOf(instance.getBizId()));
            vo.setBizTitle(instance.getBizTitle());
            vo.setStatus(instance.getStatus());
            vo.setStartTime(instance.getCreateTime());
            vo.setUpdateTime(instance.getUpdateTime());
            vo.setFinishTime(instance.getFinishTime());
            vo.setCurrentApproverNames(InstanceStatus.RUNNING.equals(instance.getStatus())
                    ? approverNames.getOrDefault(instance.getId(), Collections.emptyList())
                    : Collections.emptyList());
            vos.add(vo);
        }
        PageResultVO<InstanceItemVO> result = new PageResultVO<>();
        result.setRecords(vos);
        result.setTotal(page.getTotal());
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        return result;
    }

    @Override
    public InstanceDetailVO detail(Long instanceId, Long userId) {
        FlowInstanceDO instance = support.requireInstance(instanceId);
        return assembler.assemble(instance, userId);
    }

    @Override
    public void cancel(Long instanceId, InstanceCancelDTO dto, Long userId) {
        FlowInstanceDO instance = support.requireInstance(instanceId);
        if (!userId.equals(instance.getStartUserId())) {
            throw new BusinessException(ResultCode.INSTANCE_VIEW_FORBIDDEN, "仅发起人可撤销实例");
        }
        if (instance.getParentInstanceId() != null) {
            // 子流程实例不支持独立撤销：须从根实例发起整树级联，避免"撤孩子留父亲"的半终止状态（§8）
            throw new BusinessException(ResultCode.SUB_INSTANCE_OPERATION_FORBIDDEN);
        }
        if (!InstanceStatus.RUNNING.equals(instance.getStatus())) {
            throw new BusinessException(ResultCode.INSTANCE_STATUS_ILLEGAL);
        }
        UserDO operator = support.requireUserById(userId);
        engine.cancelInstance(instanceId, operator, dto == null ? null : dto.getComment());
    }

    // ==================== 预览接口 ====================

    @Override
    public NextApproverVO nextApprovers(NextApproverQueryDTO dto, Long userId) {
        Long processId = parseLongId(dto.getProcessId(), "流程 ID");
        checkConditionsSize(dto.getConditions());
        String nodeId = emptyToNull(dto.getNodeId());
        return engine.previewNextApprovers(processId, nodeId, userId, dto.getConditions());
    }

    @Override
    public NodeButtonsVO nodeButtons(NodeButtonsQueryDTO dto, Long userId) {
        Long processId = parseLongId(dto.getProcessId(), "流程 ID");
        checkConditionsSize(dto.getConditions());
        ProcessVersionDO version = support.requirePublishedVersion(processId);
        FlowDesignCursor cursor = support.parseCursor(version.getDesignJson());
        String nodeId = emptyToNull(dto.getNodeId());
        if (nodeId == null) {
            // nodeId 缺省：按 conditions（未传走默认列）模拟推进定位发起后第一个审批节点
            NextApproverVO preview = engine.previewNextApprovers(processId, null, userId, dto.getConditions());
            if (Boolean.TRUE.equals(preview.getEnded()) || preview.getNodes().isEmpty()) {
                throw new BusinessException(ResultCode.PROCESS_NODE_NOT_FOUND, "发起后第一个审批节点不存在");
            }
            nodeId = preview.getNodes().get(0).getNodeId();
        }
        JsonNode node = cursor.findNode(nodeId);
        if (node == null) {
            throw new BusinessException(ResultCode.PROCESS_NODE_NOT_FOUND);
        }
        NodeButtonsVO vo = new NodeButtonsVO();
        vo.setNodeId(idOf(node));
        vo.setNodeName(nameOf(node));
        vo.setNodeType(typeOf(node));
        vo.setButtons(support.readButtons(node));
        return vo;
    }

    // ==================== 单据审批状态 ====================

    @Override
    public List<BizApprovalStatusVO> bizStatus(String bizType, List<Long> bizIds) {
        if (bizType == null || bizType.trim().isEmpty() || bizIds == null || bizIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> distinctIds = bizIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        if (distinctIds.isEmpty()) {
            return Collections.emptyList();
        }
        if (distinctIds.size() > MAX_BIZ_IDS) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "bizIds 不能超过 " + MAX_BIZ_IDS + " 个");
        }
        // 单据审批状态只统计根实例：子实例状态不外露为单据状态（§9）
        List<FlowInstanceDO> instances = flowInstanceMapper.selectList(
                new LambdaQueryWrapper<FlowInstanceDO>()
                        .eq(FlowInstanceDO::getBizType, bizType)
                        .in(FlowInstanceDO::getBizId, distinctIds)
                        .isNull(FlowInstanceDO::getParentInstanceId)
                        .orderByDesc(FlowInstanceDO::getCreateTime));
        Map<Long, FlowInstanceDO> latest = new LinkedHashMap<>();
        for (FlowInstanceDO instance : instances) {
            latest.putIfAbsent(instance.getBizId(), instance);
        }
        List<Long> runningInstanceIds = latest.values().stream()
                .filter(instance -> InstanceStatus.RUNNING.equals(instance.getStatus()))
                .map(FlowInstanceDO::getId)
                .collect(java.util.stream.Collectors.toList());
        Map<Long, List<String>> approverNames = assembler.currentApproverNames(runningInstanceIds);
        List<BizApprovalStatusVO> result = new ArrayList<>(latest.size());
        for (Map.Entry<Long, FlowInstanceDO> entry : latest.entrySet()) {
            FlowInstanceDO instance = entry.getValue();
            BizApprovalStatusVO vo = new BizApprovalStatusVO();
            vo.setBizId(String.valueOf(entry.getKey()));
            vo.setInstanceId(String.valueOf(instance.getId()));
            vo.setStatus(instance.getStatus());
            vo.setCurrentApproverNames(InstanceStatus.RUNNING.equals(instance.getStatus())
                    ? approverNames.getOrDefault(instance.getId(), Collections.emptyList())
                    : Collections.emptyList());
            result.add(vo);
        }
        return result;
    }

    // ==================== 内部工具 ====================

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
                .collect(java.util.stream.Collectors.toList());
    }

    private void checkConditionsSize(Map<String, String> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return;
        }
        String serialized;
        try {
            serialized = objectMapper.writeValueAsString(conditions);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "conditions 结构非法");
        }
        if (serialized.length() > CONDITIONS_MAX_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "conditions 序列化后不能超过 4KB");
        }
    }

    private Long parseLongId(String value, String field) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, field + "非法");
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private PageResultVO<InstanceItemVO> emptyPage(int pageNum, int pageSize) {
        PageResultVO<InstanceItemVO> result = new PageResultVO<>();
        result.setRecords(Collections.emptyList());
        result.setTotal(0L);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        return result;
    }

    // ==================== 运行态用户选项 ====================

    @Override
    public List<UserBriefVO> principalUsers(String keyword, int limit) {
        int clamped = Math.max(1, Math.min(limit, 50));
        String trimmed = emptyToNull(keyword);
        Page<UserDO> page = userMapper.selectPage(new Page<>(1, clamped, false),
                new LambdaQueryWrapper<UserDO>()
                        .eq(UserDO::getStatus, 1)
                        .and(trimmed != null, w -> w.likeRight(UserDO::getUsername, trimmed)
                                .or()
                                .likeRight(UserDO::getNickname, trimmed))
                        .orderByAsc(UserDO::getId));
        List<UserBriefVO> result = new ArrayList<>(page.getRecords().size());
        for (UserDO user : page.getRecords()) {
            UserBriefVO vo = new UserBriefVO();
            vo.setUserId(String.valueOf(user.getId()));
            vo.setName(FlowApproverResolver.displayName(user));
            result.add(vo);
        }
        return result;
    }
}
