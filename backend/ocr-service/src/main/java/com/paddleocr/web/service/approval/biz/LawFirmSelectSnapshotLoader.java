package com.paddleocr.web.service.approval.biz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paddleocr.web.mapper.LawFirmSelectMapper;
import com.paddleocr.web.mapper.entity.LawFirmSelectDO;
import com.paddleocr.web.service.approval.BizType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 律所选聘快照加载器（V1 唯一业务类型）
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LawFirmSelectSnapshotLoader implements BizSnapshotLoader {

    private final LawFirmSelectMapper lawFirmSelectMapper;

    private final ObjectMapper objectMapper;

    @Override
    public String bizType() {
        return BizType.LAW_FIRM_SELECT;
    }

    @Override
    public BizSnapshot load(Long bizId) {
        LawFirmSelectDO record = lawFirmSelectMapper.selectById(bizId);
        if (record == null) {
            return null;
        }
        // fieldKey 与前端表单/条件规则英文键对齐：title/amount/reason
        Map<String, String> form = new LinkedHashMap<>(8);
        form.put("title", record.getTitle());
        form.put("amount", record.getAmount() == null ? null : String.valueOf(record.getAmount()));
        form.put("reason", record.getReason());
        try {
            return new BizSnapshot(record.getTitle(), objectMapper.writeValueAsString(form));
        } catch (Exception e) {
            log.error("律所选聘表单快照序列化失败, bizId={}", bizId, e);
            throw new IllegalStateException("表单快照序列化失败", e);
        }
    }
}
