package com.paddleocr.web.service.lawfirmselect.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import com.paddleocr.web.mapper.LawFirmSelectMapper;
import com.paddleocr.web.mapper.entity.LawFirmSelectDO;
import com.paddleocr.web.model.PageResultVO;
import com.paddleocr.web.model.dto.LawFirmSelectSaveDTO;
import com.paddleocr.web.model.vo.LawFirmSelectVO;
import com.paddleocr.web.service.lawfirmselect.LawFirmSelectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
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

    private final LawFirmSelectMapper lawFirmSelectMapper;

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
    public String saveLawFirmSelect(LawFirmSelectSaveDTO dto) {
        LawFirmSelectDO record = new LawFirmSelectDO();
        record.setTitle(dto.getTitle());
        record.setAmount(dto.getAmount());
        record.setReason(dto.getReason());
        lawFirmSelectMapper.insert(record);
        log.info("律所选聘记录新增成功, id={}, title={}", record.getId(), record.getTitle());
        return String.valueOf(record.getId());
    }

    @Override
    public void updateLawFirmSelect(Long id, LawFirmSelectSaveDTO dto) {
        getExistingLawFirmSelect(id);
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
    public void removeLawFirmSelect(Long id) {
        LawFirmSelectDO existing = getExistingLawFirmSelect(id);
        lawFirmSelectMapper.deleteById(id);
        log.info("律所选聘记录删除成功, id={}, title={}", id, existing.getTitle());
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
