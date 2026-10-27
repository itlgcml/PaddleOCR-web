package com.paddleocr.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.paddleocr.web.mapper.entity.FlowReturnRecordDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批退回记录表 Mapper
 *
 * @author paddleocr
 * @date 2026-10-26
 */
@Mapper
public interface FlowReturnRecordMapper extends BaseMapper<FlowReturnRecordDO> {
}
