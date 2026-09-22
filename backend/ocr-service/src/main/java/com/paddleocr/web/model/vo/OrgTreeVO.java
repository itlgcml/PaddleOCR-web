package com.paddleocr.web.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 机构树节点 VO（递归树）
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Data
@Schema(description = "机构树节点")
public class OrgTreeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "机构 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "父机构 ID（根为 \"0\"）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    @Schema(description = "机构编码")
    private String orgCode;

    @Schema(description = "机构名称")
    private String orgName;

    @Schema(description = "同级排序（越小越靠前）")
    private Integer sort;

    @Schema(description = "状态：1=启用 0=停用")
    private Integer status;

    @Schema(description = "子机构节点（叶子为空数组）")
    private List<OrgTreeVO> children;
}
