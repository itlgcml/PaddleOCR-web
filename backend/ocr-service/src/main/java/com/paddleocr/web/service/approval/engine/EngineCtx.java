package com.paddleocr.web.service.approval.engine;

import com.paddleocr.web.mapper.entity.FlowInstanceDO;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * 引擎推进上下文：实例 + 设计快照游标 + 表单快照平面值（条件求值取值源）
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Getter
@RequiredArgsConstructor
public class EngineCtx {

    /** 审批实例 */
    private final FlowInstanceDO instance;

    /** 发起时版本快照游照（不可变） */
    private final FlowDesignCursor cursor;

    /** 表单快照平面值：fieldKey → 值（CONDITIONS 求值取值源） */
    private final Map<String, String> formValues;
}
