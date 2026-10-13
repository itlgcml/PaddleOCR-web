package com.paddleocr.web.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 实例节点进度 VO（画布状态着色）
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Data
@Schema(description = "实例节点进度")
public class NodeProgressVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "节点 ID")
    private String nodeId;

    @Schema(description = "节点名称")
    private String nodeName;

    @Schema(description = "节点类型")
    private String nodeType;

    @Schema(description = "进度状态：CURRENT=进行中 PENDING=未到达 PASSED=已通过 REJECTED=已拒绝 SKIPPED=已跳过")
    private String status;

    @Schema(description = "节点审批人姓名集合")
    private List<String> assigneeNames;

    @Schema(description = "节点完成时间")
    private LocalDateTime finishTime;
}
