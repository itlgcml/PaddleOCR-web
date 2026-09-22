package com.paddleocr.web.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 机构简要信息 VO
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Data
@Schema(description = "机构简要信息")
public class OrgBriefVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "机构 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "机构名称")
    private String orgName;
}
