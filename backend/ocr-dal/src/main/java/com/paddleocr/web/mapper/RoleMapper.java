package com.paddleocr.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.paddleocr.web.mapper.entity.RoleDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色表 Mapper
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Mapper
public interface RoleMapper extends BaseMapper<RoleDO> {
}
