package com.paddleocr.web.service.lawfirmselect.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import com.paddleocr.web.mapper.FlowInstanceMapper;
import com.paddleocr.web.mapper.LawFirmSelectMapper;
import com.paddleocr.web.mapper.entity.FlowInstanceDO;
import com.paddleocr.web.mapper.entity.LawFirmSelectDO;
import com.paddleocr.web.model.PageResultVO;
import com.paddleocr.web.model.dto.InstanceStartDTO;
import com.paddleocr.web.model.dto.LawFirmSelectSaveDTO;
import com.paddleocr.web.model.dto.LawFirmSelectSubmitDTO;
import com.paddleocr.web.model.vo.LawFirmSelectVO;
import com.paddleocr.web.service.approval.ApprovalRuntimeProperties;
import com.paddleocr.web.service.approval.BizType;
import com.paddleocr.web.service.approval.FlowInstanceService;
import com.paddleocr.web.service.approval.InstanceStatus;
import com.paddleocr.web.service.lawfirmselect.LawFirmSelectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 律所选聘服务实现（业务审批域）：分页增删改
 *
 * @author paddleocr
 * @date 2026-09-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LawFirmSelectServiceImpl implements LawFirmSelectService {

    /** 新增并提交的用户级防重锁前缀（防双击重复建单） */
    private static final String LOCK_KEY_CREATE_SUBMIT_PREFIX = "approval:law_firm_select:create_submit:";

    private static final long LOCK_WAIT_SECONDS = 0L;

    private final LawFirmSelectMapper lawFirmSelectMapper;
    private final FlowInstanceMapper flowInstanceMapper;
    private final FlowInstanceService flowInstanceService;
    private final ApprovalRuntimeProperties approvalRuntimeProperties;
    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;

    @Override
    public PageResultVO<LawFirmSelectVO> pageLawFirmSelects(int pageNum, int pageSize, String title) {
        LambdaQueryWrapper<LawFirmSelectDO> wrapper = new LambdaQueryWrapper<LawFirmSelectDO>()
                .orderByDesc(LawFirmSelectDO::getCreateTime);
        if (StrUtil.isNotBlank(title)) {
            // 页面搜索严禁左模糊/全模糊，统一前缀匹配
            wrapper.likeRight(LawFirmSelectDO::getTitle, title);
        }
        Page<LawFirmSelectDO> page = lawFirmSelectMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<LawFirmSelectVO> records = page.getRecords().stream()
                .map(this::toLawFirmSelectVO)
                .collect(Collectors.toList());
        return buildPageResult(records, page);
    }

    @Override
    public LawFirmSelectVO getLawFirmSelectDetail(Long id) {
        return toLawFirmSelectVO(getExistingLawFirmSelect(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String saveLawFirmSelect(LawFirmSelectSaveDTO dto) {
        LawFirmSelectDO record = insertRecord(dto);
        log.info("律所选聘记录新增成功, id={}, title={}", record.getId(), record.getTitle());
        return String.valueOf(record.getId());
    }

    @Override
    public String createAndSubmit(LawFirmSelectSubmitDTO dto, Long userId) {
        // 用户级防重锁：双击/并发重复提交直接 40045；锁包住事务，事务提交后才释放
        RLock lock = redissonClient.getLock(LOCK_KEY_CREATE_SUBMIT_PREFIX + userId);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, approvalRuntimeProperties.getLockLeaseSeconds(),
                    TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(ResultCode.PROCESS_REPEAT_SUBMIT);
            }
            return transactionTemplate.execute(status -> {
                // 同一事务：建单 + 发起流程；发起任一校验失败抛 BusinessException 整体回滚，不残留草稿。
                // start 内部为本地调用（嵌套事务/锁均 REQUIRED 传播与可重入），加入当前事务
                LawFirmSelectDO record = insertRecord(toSaveDTO(dto));
                InstanceStartDTO startDto = buildStartDto(dto, record.getId());
                String instanceId = flowInstanceService.start(startDto, userId);
                log.info("律所选聘新增并提交成功, id={}, instanceId={}", record.getId(), instanceId);
                return instanceId;
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.PROCESS_REPEAT_SUBMIT);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLawFirmSelect(Long id, LawFirmSelectSaveDTO dto) {
        getExistingLawFirmSelect(id);
        checkNotInApproval(id);
        // 走实体更新触发 AuditMetaObjectHandler 自动填充 updateTime
        LawFirmSelectDO record = new LawFirmSelectDO();
        record.setId(id);
        record.setTitle(dto.getTitle());
        record.setAmount(dto.getAmount());
        record.setReason(dto.getReason());
        lawFirmSelectMapper.updateById(record);
        log.info("律所选聘记录修改成功, id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeLawFirmSelect(Long id) {
        LawFirmSelectDO existing = getExistingLawFirmSelect(id);
        checkNotInApproval(id);
        lawFirmSelectMapper.deleteById(id);
        log.info("律所选聘记录删除成功, id={}, title={}", id, existing.getTitle());
    }

    /**
     * 插入律所选聘记录（雪花 ID 由 ASSIGN_ID 回填）
     */
    private LawFirmSelectDO insertRecord(LawFirmSelectSaveDTO dto) {
        LawFirmSelectDO record = new LawFirmSelectDO();
        record.setTitle(dto.getTitle());
        record.setAmount(dto.getAmount());
        record.setReason(dto.getReason());
        lawFirmSelectMapper.insert(record);
        return record;
    }

    /**
     * 新增并提交请求裁剪为单据字段（复用建单逻辑）
     */
    private LawFirmSelectSaveDTO toSaveDTO(LawFirmSelectSubmitDTO dto) {
        LawFirmSelectSaveDTO saveDTO = new LawFirmSelectSaveDTO();
        saveDTO.setTitle(dto.getTitle());
        saveDTO.setAmount(dto.getAmount());
        saveDTO.setReason(dto.getReason());
        return saveDTO;
    }

    /**
     * 组装发起流程请求（单据类型由后端固定，不信任前端传值）
     */
    private InstanceStartDTO buildStartDto(LawFirmSelectSubmitDTO dto, Long bizId) {
        InstanceStartDTO startDto = new InstanceStartDTO();
        startDto.setProcessCode(dto.getProcessCode());
        startDto.setBizType(BizType.LAW_FIRM_SELECT);
        startDto.setBizId(String.valueOf(bizId));
        startDto.setCcUserIds(dto.getCcUserIds());
        startDto.setComment(dto.getComment());
        return startDto;
    }

    /**
     * 查询存在的律所选聘记录（已删/不存在统一 40050）
     */
    private LawFirmSelectDO getExistingLawFirmSelect(Long id) {
        LawFirmSelectDO record = lawFirmSelectMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.LAW_FIRM_SELECT_NOT_FOUND);
        }
        return record;
    }

    /**
     * 审批中的单据禁止编辑/删除（存在 RUNNING 实例即拦截，40060）
     */
    private void checkNotInApproval(Long id) {
        Long runningCount = flowInstanceMapper.selectCount(new LambdaQueryWrapper<FlowInstanceDO>()
                .eq(FlowInstanceDO::getBizType, BizType.LAW_FIRM_SELECT)
                .eq(FlowInstanceDO::getBizId, id)
                .eq(FlowInstanceDO::getStatus, InstanceStatus.RUNNING));
        if (runningCount != null && runningCount > 0) {
            throw new BusinessException(ResultCode.BIZ_IN_APPROVAL);
        }
    }

    /**
     * DO 转 VO
     */
    private LawFirmSelectVO toLawFirmSelectVO(LawFirmSelectDO record) {
        LawFirmSelectVO vo = new LawFirmSelectVO();
        vo.setId(record.getId());
        vo.setTitle(record.getTitle());
        vo.setAmount(record.getAmount());
        vo.setReason(record.getReason());
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }

    /**
     * 组装分页结果
     */
    private PageResultVO<LawFirmSelectVO> buildPageResult(List<LawFirmSelectVO> records, Page<LawFirmSelectDO> page) {
        PageResultVO<LawFirmSelectVO> result = new PageResultVO<>();
        result.setRecords(records);
        result.setTotal(page.getTotal());
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        return result;
    }
}
