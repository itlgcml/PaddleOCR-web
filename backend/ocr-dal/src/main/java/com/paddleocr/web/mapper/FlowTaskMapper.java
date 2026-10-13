package com.paddleocr.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.paddleocr.web.mapper.entity.FlowTaskDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批任务表 Mapper
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Mapper
public interface FlowTaskMapper extends BaseMapper<FlowTaskDO> {
}
