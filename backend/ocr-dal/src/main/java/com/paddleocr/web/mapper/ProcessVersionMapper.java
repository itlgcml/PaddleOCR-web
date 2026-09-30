package com.paddleocr.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.paddleocr.web.mapper.entity.ProcessVersionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批流程版本表 Mapper
 *
 * @author paddleocr
 * @date 2026-09-30
 */
@Mapper
public interface ProcessVersionMapper extends BaseMapper<ProcessVersionDO> {
}
