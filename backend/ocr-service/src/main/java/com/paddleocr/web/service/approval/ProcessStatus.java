package com.paddleocr.web.service.approval;

/**
 * 流程状态常量（值与 flow_process.status 列及前端契约一致，避免魔法值散落）
 *
 * @author paddleocr
 * @date 2026-09-30
 */
public final class ProcessStatus {

    /** 草稿（初始态，可编辑基本信息/保存草稿/发布/删除） */
    public static final String DRAFT = "DRAFT";

    /** 已发布（可停用、可继续编辑草稿、可再发布产生新版本） */
    public static final String PUBLISHED = "PUBLISHED";

    /** 已停用（可编辑草稿、可再发布回到已发布态） */
    public static final String DISABLED = "DISABLED";

    private ProcessStatus() {
    }
}
