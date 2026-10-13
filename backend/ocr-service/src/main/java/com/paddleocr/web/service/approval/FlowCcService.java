package com.paddleocr.web.service.approval;

import com.paddleocr.web.model.PageResultVO;
import com.paddleocr.web.model.dto.RuntimePageQuery;
import com.paddleocr.web.model.vo.CcVO;

/**
 * 抄送服务（待阅/已阅/标记已阅）
 *
 * @author paddleocr
 * @date 2026-10-12
 */
public interface FlowCcService {

    /**
     * 我的抄送分页（read=false 待阅 UNREAD / read=true 已阅 READ）
     */
    PageResultVO<CcVO> pageMine(RuntimePageQuery query, Long userId, boolean read);

    /**
     * 标记已阅（仅 receiver 本人；已读幂等成功）
     */
    void markRead(Long ccId, Long userId);
}
