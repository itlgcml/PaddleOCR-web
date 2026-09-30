package com.paddleocr.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.paddleocr.web.mapper.entity.ProcessDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批流程定义表 Mapper
 *
 * @author paddleocr
 * @date 2026-09-30
 */
@Mapper
public interface ProcessMapper extends BaseMapper<ProcessDO> {
}
