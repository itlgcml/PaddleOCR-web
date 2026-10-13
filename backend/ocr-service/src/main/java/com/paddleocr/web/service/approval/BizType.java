package com.paddleocr.web.service.approval;

/**
 * 业务单据类型常量（值与 flow_instance.biz_type 列一致，V1 仅律所选聘）
 *
 * @author paddleocr
 * @date 2026-10-12
 */
public final class BizType {

    /** 律所选聘（law_firm_select） */
    public static final String LAW_FIRM_SELECT = "LAW_FIRM_SELECT";

    private BizType() {
    }
}
