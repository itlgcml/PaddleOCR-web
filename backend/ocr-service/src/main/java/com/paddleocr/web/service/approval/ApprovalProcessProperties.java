package com.paddleocr.web.service.approval;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 审批流程配置项
 *
 * @author paddleocr
 * @date 2026-09-30
 */
@Data
@Component
@ConfigurationProperties(prefix = "approval.process")
public class ApprovalProcessProperties {

    /** 流程设计 JSON 长度上限（字符数，默认 512KB），防超大负载拖垮接口 */
    private long designMaxLength = 524288L;

    /** 防重复提交锁持有时长（秒），leaseTime 兜底自动释放，防业务异常未 unlock 导致死锁 */
    private long lockLeaseSeconds = 3L;
}
