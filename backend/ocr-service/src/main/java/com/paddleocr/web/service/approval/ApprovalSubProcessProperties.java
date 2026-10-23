package com.paddleocr.web.service.approval;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 子流程节点配置项
 *
 * @author paddleocr
 * @date 2026-10-22
 */
@Data
@Component
@ConfigurationProperties(prefix = "approval.sub-process")
public class ApprovalSubProcessProperties {

    /** 子流程最大嵌套层数（主流程为第 0 层；发布校验与前端常量 MAX_SUB_PROCESS_DEPTH 对齐） */
    private int maxDepth = 3;
}
