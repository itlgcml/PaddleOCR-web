package com.paddleocr.web.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

/**
 * 用户角色分配请求 DTO（全量替换；空数组=全解绑；重复元素服务端去重）
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Data
@Schema(description = "用户角色分配请求")
public class UserRoleAssignDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "角色 ID 集合（可为空数组表示全解绑）")
    @NotNull(message = "角色 ID 集合不能为 null")
    private List<Long> roleIds;
}
