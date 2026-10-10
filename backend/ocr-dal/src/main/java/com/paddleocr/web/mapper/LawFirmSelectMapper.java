package com.paddleocr.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.paddleocr.web.mapper.entity.LawFirmSelectDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 律所选聘记录表 Mapper
 *
 * @author paddleocr
 * @date 2026-09-30
 */
@Mapper
public interface LawFirmSelectMapper extends BaseMapper<LawFirmSelectDO> {
}
