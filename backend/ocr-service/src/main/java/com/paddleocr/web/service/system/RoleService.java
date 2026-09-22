package com.paddleocr.web.service.system;

import com.paddleocr.web.model.PageResultVO;
import com.paddleocr.web.model.dto.RoleSaveDTO;
import com.paddleocr.web.model.dto.RoleUpdateDTO;
import com.paddleocr.web.model.vo.RoleVO;

import java.util.List;

/**
 * 角色服务（系统设置域）：分页查询、增删改、内置角色保护
 *
 * @author paddleocr
 * @date 2026-09-22
 */
public interface RoleService {

    /**
     * 角色分页查询
     *
     * @param pageNum 页码（≥1）
     * @param pageSize 每页条数（1~100）
     * @param keyword 按角色编码/名称前缀匹配，可空
     * @return 分页结果
     */
    PageResultVO<RoleVO> pageRoles(int pageNum, int pageSize, String keyword);

    /**
     * 全量启用角色（供用户分配角色下拉框）
     *
     * @return 启用角色集合
     */
    List<RoleVO> listEnabledRoles();

    /**
     * 新增角色（校验角色编码唯一）
     *
     * @param dto 角色新增请求
     * @return 新角色 ID（字符串形式）
     */
    String saveRole(RoleSaveDTO dto);

    /**
     * 修改角色名称/描述/状态（内置角色禁止任何修改）
     *
     * @param id 角色 ID
     * @param dto 角色修改请求
     */
    void updateRole(Long id, RoleUpdateDTO dto);

    /**
     * 删除角色（内置角色保护 + 绑定校验）
     *
     * @param id 角色 ID
     */
    void removeRole(Long id);
}
