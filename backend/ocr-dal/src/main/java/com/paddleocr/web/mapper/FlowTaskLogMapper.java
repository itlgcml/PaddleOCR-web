package com.paddleocr.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.paddleocr.web.mapper.entity.FlowTaskLogDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批记录表 Mapper
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Mapper
public interface FlowTaskLogMapper extends BaseMapper<FlowTaskLogDO> {
}
