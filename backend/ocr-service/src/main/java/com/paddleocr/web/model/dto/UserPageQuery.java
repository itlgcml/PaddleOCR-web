package com.paddleocr.web.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户分页查询参数
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Data
@Schema(description = "用户分页查询参数")
public class UserPageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "页码（默认 1）")
    private Integer pageNum;

    @Schema(description = "每页条数（默认 10，1~100）")
    private Integer pageSize;

    @Schema(description = "用户名/昵称前缀匹配")
    private String username;

    @Schema(description = "按机构精确过滤（V1 仅本级）")
    private Long orgId;

    @Schema(description = "状态过滤：1=启用 0=禁用")
    private Integer status;
}
