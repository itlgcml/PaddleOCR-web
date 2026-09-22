package com.paddleocr.web.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 通用分页结果
 */
@Data
@Schema(description = "分页结果")
public class PageResultVO<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "当前页数据")
    private List<T> records;

    @Schema(description = "总条数")
    private long total;

    @Schema(description = "当前页码")
    private long pageNum;

    @Schema(description = "每页条数")
    private long pageSize;
}
