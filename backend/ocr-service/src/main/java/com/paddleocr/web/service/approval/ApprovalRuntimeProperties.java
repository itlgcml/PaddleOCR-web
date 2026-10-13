package com.paddleocr.web.service.approval;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 审批运行态配置项
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Data
@Component
@ConfigurationProperties(prefix = "approval.runtime")
public class ApprovalRuntimeProperties {

    /** 转办/委托/加签/抄送目标人数上限 */
    private int maxReceivers = 20;

    /** 表单快照 JSON 长度上限（字符数，默认 512KB），防超大负载拖垮接口 */
    private long snapshotMaxLength = 524288L;

    /** 推进锁持有时长（秒），leaseTime 兜底自动释放，防业务异常未 unlock 导致死锁 */
    private long lockLeaseSeconds = 3L;
}
