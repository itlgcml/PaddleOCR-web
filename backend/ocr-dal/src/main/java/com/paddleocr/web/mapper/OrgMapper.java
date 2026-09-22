package com.paddleocr.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.paddleocr.web.mapper.entity.OrgDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 机构表 Mapper
 *
 * @author paddleocr
 * @date 2026-09-22
 */
@Mapper
public interface OrgMapper extends BaseMapper<OrgDO> {
}
