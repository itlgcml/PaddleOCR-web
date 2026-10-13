package com.paddleocr.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.paddleocr.web.mapper.entity.FlowInstanceDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批流程实例表 Mapper
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Mapper
public interface FlowInstanceMapper extends BaseMapper<FlowInstanceDO> {
}
