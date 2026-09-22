package com.paddleocr.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.paddleocr.web.mapper.entity.UserRoleDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户角色关联表 Mapper（关联表无逻辑删除字段，物理删除）
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRoleDO> {
}
