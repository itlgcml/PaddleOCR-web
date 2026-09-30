package com.paddleocr.web.service.approval.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import com.paddleocr.web.mapper.ProcessMapper;
import com.paddleocr.web.mapper.ProcessVersionMapper;
import com.paddleocr.web.mapper.entity.ProcessDO;
import com.paddleocr.web.mapper.entity.ProcessVersionDO;
import com.paddleocr.web.model.PageResultVO;
import com.paddleocr.web.model.dto.ProcessDesignSaveDTO;
import com.paddleocr.web.model.dto.ProcessPageQuery;
import com.paddleocr.web.model.dto.ProcessSaveDTO;
import com.paddleocr.web.model.dto.ProcessUpdateDTO;
import com.paddleocr.web.model.vo.ProcessDesignVO;
import com.paddleocr.web.model.vo.ProcessDetailVO;
import com.paddleocr.web.model.vo.ProcessVersionVO;
import com.paddleocr.web.model.vo.ProcessVO;
import com.paddleocr.web.service.approval.ApprovalProcessProperties;
import com.paddleocr.web.service.approval.ProcessService;
import com.paddleocr.web.service.approval.ProcessStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 流程配置服务实现（审批域）：CRUD、状态机（先查后改 + 同事务）、版本编排（单调递增 + 唯一索引兜底）、
 * 发布最小结构校验、Redisson 防重复提交
 *
 * @author paddleocr
 * @date 2026-09-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessServiceImpl implements ProcessService {

    /** 防重复提交锁 key 前缀（保存设计与发布共用，串行化对同一流程的写操作） */
    private static final String LOCK_KEY_PREFIX = "approval:process:submit:";

    /** tryLock 不等待：获取失败立即返回 */
    private static final long LOCK_WAIT_SECONDS = 0L;

    /** 发起范围-指定 */
    private static final String INITIATOR_SPECIFIC = "SPECIFIC";

    /** 节点类型：发起人 */
    private static final String NODE_TYPE_START = "START";

    /** 节点类型：审批人 */
    private static final String NODE_TYPE_APPROVER = "APPROVER";

    /** 节点类型：条件分支列 */
    private static final String NODE_TYPE_CONDITION = "CONDITION";

    /** 审批人来源-指定成员/角色 */
    private static final String APPROVER_SOURCE_MEMBER = "MEMBER";

    /** 空 JSON 数组字面量（发起范围 ALL 或集合为空时的落库值） */
    private static final String EMPTY_JSON_ARRAY = "[]";

    private final ProcessMapper processMapper;

    private final ProcessVersionMapper processVersionMapper;

    private final RedissonClient redissonClient;

    private final ApprovalProcessProperties properties;

    private final ObjectMapper objectMapper;

    @Override
    public PageResultVO<ProcessVO> page(ProcessPageQuery query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 100);
        LambdaQueryWrapper<ProcessDO> wrapper = new LambdaQueryWrapper<ProcessDO>()
                .eq(StrUtil.isNotBlank(query.getStatus()), ProcessDO::getStatus, query.getStatus())
                .orderByDesc(ProcessDO::getUpdateTime);
        if (StrUtil.isNotBlank(query.getKeyword())) {
            // 页面搜索严禁左模糊/全模糊，统一前缀匹配
            wrapper.and(w -> w.likeRight(ProcessDO::getCode, query.getKeyword())
                    .or()
                    .likeRight(ProcessDO::getName, query.getKeyword()));
        }
        Page<ProcessDO> page = processMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<ProcessVO> records = page.getRecords().stream()
                .map(this::toProcessVO)
                .collect(Collectors.toList());
        return buildPageResult(records, page);
    }

    @Override
    public ProcessDetailVO detail(Long id) {
        ProcessDO process = getExistingProcess(id);
        ProcessDetailVO vo = new ProcessDetailVO();
        vo.setId(process.getId());
        vo.setCode(process.getCode());
        vo.setName(process.getName());
        vo.setDescription(process.getDescription());
        vo.setStartPath(process.getStartPath());
        vo.setDetailPath(process.getDetailPath());
        vo.setInitiatorType(process.getInitiatorType());
        vo.setInitiatorMembers(parseIdArray(process.getInitiatorMembers()));
        vo.setInitiatorRoles(parseIdArray(process.getInitiatorRoles()));
        vo.setStatus(process.getStatus());
        vo.setCurrentVersion(process.getCurrentVersion());
        return vo;
    }

    @Override
    public String create(ProcessSaveDTO dto) {
        validateInitiatorRange(dto.getInitiatorType(), dto.getInitiatorMembers(), dto.getInitiatorRoles());
        Long sameCodeCount = processMapper.selectCount(
                new LambdaQueryWrapper<ProcessDO>().eq(ProcessDO::getCode, dto.getCode()));
        if (sameCodeCount != null && sameCodeCount > 0L) {
            throw new BusinessException(ResultCode.PROCESS_CODE_EXISTS);
        }
        ProcessDO process = new ProcessDO();
        process.setCode(dto.getCode());
        process.setName(dto.getName());
        process.setDescription(dto.getDescription());
        process.setStartPath(dto.getStartPath());
        process.setDetailPath(dto.getDetailPath());
        process.setInitiatorType(dto.getInitiatorType());
        process.setInitiatorMembers(toIdArrayJson(dto.getInitiatorType(), dto.getInitiatorMembers()));
        process.setInitiatorRoles(toIdArrayJson(dto.getInitiatorType(), dto.getInitiatorRoles()));
        process.setStatus(ProcessStatus.DRAFT);
        // 新流程无版本无草稿：currentVersion/draftJson 保持 NULL
        processMapper.insert(process);
        log.info("流程新增成功, id={}, code={}", process.getId(), process.getCode());
        return String.valueOf(process.getId());
    }

    @Override
    public void update(Long id, ProcessUpdateDTO dto) {
        ProcessDO process = getExistingProcess(id);
        if (!ProcessStatus.DRAFT.equals(process.getStatus())) {
            throw new BusinessException(ResultCode.PROCESS_STATUS_ILLEGAL,
                    "仅草稿态流程可编辑基本信息，当前状态：" + process.getStatus());
        }
        validateInitiatorRange(dto.getInitiatorType(), dto.getInitiatorMembers(), dto.getInitiatorRoles());
        ProcessDO patch = new ProcessDO();
        patch.setId(id);
        patch.setName(dto.getName());
        patch.setDescription(dto.getDescription());
        patch.setStartPath(dto.getStartPath());
        patch.setDetailPath(dto.getDetailPath());
        patch.setInitiatorType(dto.getInitiatorType());
        patch.setInitiatorMembers(toIdArrayJson(dto.getInitiatorType(), dto.getInitiatorMembers()));
        patch.setInitiatorRoles(toIdArrayJson(dto.getInitiatorType(), dto.getInitiatorRoles()));
        processMapper.updateById(patch);
        log.info("流程基本信息更新成功, id={}", id);
    }

    @Override
    public void saveDesign(Long id, ProcessDesignSaveDTO dto) {
        executeWithSubmitLock(id, () -> {
            ProcessDO process = getExistingProcess(id);
            checkDesignLength(dto.getDesignJson());
            parseDesignRoot(dto.getDesignJson());
            ProcessDO patch = new ProcessDO();
            patch.setId(process.getId());
            patch.setDraftJson(dto.getDesignJson());
            // 仅覆盖草稿区：不产生新版本、不改 currentVersion 与任何已发布快照
            processMapper.updateById(patch);
            log.info("流程设计草稿保存成功, id={}, length={}", id, dto.getDesignJson().length());
            return null;
        });
    }

    @Override
    public ProcessDesignVO getDesign(Long id, String version) {
        ProcessDO process = getExistingProcess(id);
        if (StrUtil.isNotBlank(version)) {
            ProcessVersionDO snapshot = processVersionMapper.selectOne(
                    new LambdaQueryWrapper<ProcessVersionDO>()
                            .eq(ProcessVersionDO::getProcessId, id)
                            .eq(ProcessVersionDO::getVersion, version));
            if (snapshot == null) {
                throw new BusinessException(ResultCode.PROCESS_VERSION_NOT_FOUND);
            }
            return buildDesignVO(snapshot.getVersion(), ProcessStatus.PUBLISHED,
                    snapshot.getDesignJson(), snapshot.getUpdateTime());
        }
        // 缺省返回草稿：version 回显草稿所基于的已发布版本（从未发布为 null）
        return buildDesignVO(process.getCurrentVersion(), ProcessStatus.DRAFT,
                process.getDraftJson(), process.getUpdateTime());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String publish(Long id) {
        return executeWithSubmitLock(id, () -> {
            ProcessDO process = getExistingProcess(id);
            if (StrUtil.isBlank(process.getDraftJson())) {
                throw new BusinessException(ResultCode.PROCESS_VERSION_NOT_FOUND, "无可发布的流程设计草稿");
            }
            checkDesignLength(process.getDraftJson());
            JsonNode root = parseDesignRoot(process.getDraftJson());
            validateNodeForPublish(root);

            String nextVersion = nextVersion(process.getCurrentVersion());
            ProcessVersionDO snapshot = new ProcessVersionDO();
            snapshot.setProcessId(process.getId());
            snapshot.setVersion(nextVersion);
            snapshot.setDesignJson(process.getDraftJson());
            processVersionMapper.insert(snapshot);

            ProcessDO patch = new ProcessDO();
            patch.setId(process.getId());
            patch.setCurrentVersion(nextVersion);
            patch.setStatus(ProcessStatus.PUBLISHED);
            processMapper.updateById(patch);
            log.info("流程发布成功, id={}, version={}", id, nextVersion);
            return nextVersion;
        });
    }

    @Override
    public void disable(Long id) {
        ProcessDO process = getExistingProcess(id);
        if (!ProcessStatus.PUBLISHED.equals(process.getStatus())) {
            throw new BusinessException(ResultCode.PROCESS_STATUS_ILLEGAL,
                    "仅已发布流程可停用，当前状态：" + process.getStatus());
        }
        ProcessDO patch = new ProcessDO();
        patch.setId(id);
        patch.setStatus(ProcessStatus.DISABLED);
        processMapper.updateById(patch);
        log.info("流程停用成功, id={}", id);
    }

    @Override
    public List<ProcessVersionVO> versions(Long id) {
        getExistingProcess(id);
        // 版本号为 "v{n}" 字符串，字典序在 v9/v10 处失真，按插入顺序（雪花 ID）倒序即发布时间倒序
        return processVersionMapper.selectList(new LambdaQueryWrapper<ProcessVersionDO>()
                        .eq(ProcessVersionDO::getProcessId, id)
                        .orderByDesc(ProcessVersionDO::getId))
                .stream()
                .map(this::toVersionVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        ProcessDO process = getExistingProcess(id);
        if (!ProcessStatus.DRAFT.equals(process.getStatus())) {
            throw new BusinessException(ResultCode.PROCESS_STATUS_ILLEGAL,
                    "仅草稿态流程可删除，当前状态：" + process.getStatus());
        }
        processMapper.deleteById(id);
        // 连带逻辑删除版本记录（草稿态理论无版本，防御性清理）
        processVersionMapper.delete(new LambdaQueryWrapper<ProcessVersionDO>()
                .eq(ProcessVersionDO::getProcessId, id));
        log.info("流程删除成功, id={}, code={}", id, process.getCode());
    }

    /**
     * 防重复提交锁内执行写操作（保存设计/发布共用同一把锁）。
     * tryLock 失败返回 40045；leaseTime 兜底自动释放防死锁；finally 中仅释放自己持有的锁。
     */
    private <T> T executeWithSubmitLock(Long processId, Supplier<T> action) {
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + processId);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, properties.getLockLeaseSeconds(), TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(ResultCode.PROCESS_REPEAT_SUBMIT);
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "获取提交锁被中断");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 查询存在的流程（已删/不存在统一 40041，@TableLogic 自动过滤已删）
     */
    private ProcessDO getExistingProcess(Long id) {
        ProcessDO process = processMapper.selectById(id);
        if (process == null) {
            throw new BusinessException(ResultCode.PROCESS_NOT_FOUND);
        }
        return process;
    }

    /**
     * 发起范围校验：SPECIFIC 时人员与角色的并集须非空（对齐前端并集校验）
     */
    private void validateInitiatorRange(String initiatorType, List<String> members, List<String> roles) {
        if (!INITIATOR_SPECIFIC.equals(initiatorType)) {
            return;
        }
        boolean empty = (members == null || members.isEmpty()) && (roles == null || roles.isEmpty());
        if (empty) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "发起范围为指定时，请至少选择一名人员或一个角色");
        }
    }

    /**
     * 发起范围集合序列化为 JSON 数组文本；ALL 时统一落空数组
     */
    private String toIdArrayJson(String initiatorType, List<String> ids) {
        if (!INITIATOR_SPECIFIC.equals(initiatorType) || ids == null || ids.isEmpty()) {
            return EMPTY_JSON_ARRAY;
        }
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            // List<String> 序列化无自定义逻辑，理论不可达；防御性兜底
            log.error("序列化发起范围集合失败, ids={}", ids, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * JSON 数组文本反序列化为 ID 集合；空/损坏数据兜底空集合（不影响基本信息展示）
     */
    private List<String> parseIdArray(String json) {
        if (StrUtil.isBlank(json)) {
            return new ArrayList<>();
        }
        try {
            List<String> ids = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return ids == null ? new ArrayList<>() : ids;
        } catch (JsonProcessingException e) {
            log.error("反序列化发起范围集合失败, json={}", json, e);
            return new ArrayList<>();
        }
    }

    /**
     * 设计 JSON 长度校验（可配置上限，默认 512KB，防超大负载拖垮接口）
     */
    private void checkDesignLength(String designJson) {
        if (designJson.length() > properties.getDesignMaxLength()) {
            throw new BusinessException(ResultCode.PROCESS_DESIGN_INVALID, "流程设计内容超出长度上限");
        }
    }

    /**
     * 设计 JSON 解析性校验：可解析为对象、含 root、root.type 为 START（对齐前端 loadDoc 基础校验）
     *
     * @return root 节点（供发布级校验继续遍历）
     */
    private JsonNode parseDesignRoot(String designJson) {
        JsonNode doc;
        try {
            doc = objectMapper.readTree(designJson);
        } catch (JsonProcessingException e) {
            log.warn("流程设计 JSON 解析失败, length={}", designJson.length());
            throw new BusinessException(ResultCode.PROCESS_DESIGN_INVALID);
        }
        if (doc == null || !doc.isObject() || !doc.has("root")) {
            throw new BusinessException(ResultCode.PROCESS_DESIGN_INVALID);
        }
        JsonNode root = doc.get("root");
        if (!root.isObject() || !NODE_TYPE_START.equals(root.path("type").asText())) {
            throw new BusinessException(ResultCode.PROCESS_DESIGN_INVALID);
        }
        return root;
    }

    /**
     * 发布级校验（对齐前端 useWorkflowValidate）：先序遍历递归全树
     * <ul>
     *     <li>节点 name 非空</li>
     *     <li>APPROVER 且 source=MEMBER 时 memberIds/roleIds 并集非空</li>
     *     <li>CONDITION 非 isDefault 时规则数为零则报错</li>
     * </ul>
     */
    private void validateNodeForPublish(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (StrUtil.isBlank(node.path("name").asText())) {
            throw new BusinessException(ResultCode.PROCESS_DESIGN_INVALID, "存在未命名的节点");
        }
        String type = node.path("type").asText();
        JsonNode props = node.path("props");
        if (NODE_TYPE_APPROVER.equals(type) && APPROVER_SOURCE_MEMBER.equals(props.path("source").asText())) {
            if (props.path("memberIds").size() + props.path("roleIds").size() == 0) {
                throw new BusinessException(ResultCode.PROCESS_DESIGN_INVALID,
                        "审批节点「" + node.path("name").asText() + "」未选择审批人");
            }
        }
        if (NODE_TYPE_CONDITION.equals(type) && !props.path("isDefault").asBoolean(false)) {
            if (countConditionRules(props) == 0) {
                throw new BusinessException(ResultCode.PROCESS_DESIGN_INVALID,
                        "条件「" + node.path("name").asText() + "」未配置规则");
            }
        }
        for (JsonNode branch : node.path("conditionNodes")) {
            validateNodeForPublish(branch);
        }
        validateNodeForPublish(node.path("childNode"));
    }

    /**
     * 统计条件属性内所有条件组的规则总数
     */
    private int countConditionRules(JsonNode props) {
        int count = 0;
        for (JsonNode group : props.path("groups")) {
            count += group.path("rules").size();
        }
        return count;
    }

    /**
     * 生成下一版本号：v1 起步，之后在当前版本数字上单调递增
     */
    private String nextVersion(String currentVersion) {
        if (StrUtil.isBlank(currentVersion)) {
            return "v1";
        }
        try {
            return "v" + (Integer.parseInt(currentVersion.substring(1)) + 1);
        } catch (NumberFormatException e) {
            log.error("当前版本号格式非法, currentVersion={}", currentVersion);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "版本号解析失败");
        }
    }

    /**
     * DO 转列表行 VO
     */
    private ProcessVO toProcessVO(ProcessDO process) {
        ProcessVO vo = new ProcessVO();
        vo.setId(process.getId());
        vo.setCode(process.getCode());
        vo.setName(process.getName());
        vo.setStatus(process.getStatus());
        vo.setCurrentVersion(process.getCurrentVersion());
        vo.setUpdateTime(process.getUpdateTime());
        return vo;
    }

    /**
     * 版本 DO 转VO
     */
    private ProcessVersionVO toVersionVO(ProcessVersionDO snapshot) {
        ProcessVersionVO vo = new ProcessVersionVO();
        vo.setVersion(snapshot.getVersion());
        vo.setUpdateTime(snapshot.getUpdateTime());
        return vo;
    }

    /**
     * 组装设计查询 VO
     */
    private ProcessDesignVO buildDesignVO(String version, String status, String designJson, LocalDateTime updateTime) {
        ProcessDesignVO vo = new ProcessDesignVO();
        vo.setVersion(version);
        vo.setStatus(status);
        vo.setDesignJson(designJson);
        vo.setUpdateTime(updateTime);
        return vo;
    }

    /**
     * 组装分页结果
     */
    private PageResultVO<ProcessVO> buildPageResult(List<ProcessVO> records, Page<ProcessDO> page) {
        PageResultVO<ProcessVO> result = new PageResultVO<>();
        result.setRecords(records);
        result.setTotal(page.getTotal());
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        return result;
    }
}
