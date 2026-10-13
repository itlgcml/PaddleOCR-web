package com.paddleocr.web.service.approval.biz;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 业务单据快照（发起审批时冻结：标题 + 表单 JSON）
 *
 * @author paddleocr
 * @date 2026-10-12
 */
@Data
@AllArgsConstructor
public class BizSnapshot {

    /** 业务标题（实例 biz_title 冗余，列表展示） */
    private final String title;

    /** 表单数据 JSON（fieldKey → 值，条件求值与详情展示；如 {"title":"..","amount":"5000.00","reason":".."}） */
    private final String formJson;
}
