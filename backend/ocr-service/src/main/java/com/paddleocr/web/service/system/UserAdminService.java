package com.paddleocr.web.service.system;

import com.paddleocr.web.model.PageResultVO;
import com.paddleocr.web.model.dto.UserPageQuery;
import com.paddleocr.web.model.vo.UserPageVO;

import java.util.List;

/**
 * 用户管理服务（系统设置域）：分页查询、机构调整、角色分配、启禁用
 *
 * @author paddleocr
 * @date 2026-09-22
 */
public interface UserAdminService {

    /**
     * 用户分页查询（用户名/昵称前缀匹配、机构精确、状态过滤）
     *
     * @param query 分页查询参数
     * @return 分页结果
     */
    PageResultVO<UserPageVO> pageUsers(UserPageQuery query);

    /**
     * 调整用户所属机构（目标机构须存在且启用）
     *
     * @param userId 目标用户 ID
     * @param orgId 目标机构 ID
     */
    void updateUserOrg(Long userId, Long orgId);

    /**
     * 全量替换用户角色（事务内 delete + batch insert；空集合=全解绑；重复元素去重）
     *
     * @param userId 目标用户 ID
     * @param roleIds 角色 ID 集合
     */
    void updateUserRoles(Long userId, List<Long> roleIds);

    /**
     * 启用/禁用用户（禁用即时生效：每次请求查库装载状态）
     *
     * @param userId 目标用户 ID
     * @param status 目标状态：1=启用 0=禁用
     */
    void updateUserStatus(Long userId, Integer status);
}
