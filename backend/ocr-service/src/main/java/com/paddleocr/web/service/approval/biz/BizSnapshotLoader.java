package com.paddleocr.web.service.approval.biz;

/**
 * 业务单据快照加载器 SPI（发起审批时冻结标题与表单数据）
 *
 * <p>新业务类型接入审批时：实现本接口并注册为 Spring Bean，引擎按 {@link #bizType()} 路由。</p>
 *
 * @author paddleocr
 * @date 2026-10-12
 */
public interface BizSnapshotLoader {

    /**
     * 支持的业务单据类型（与 flow_instance.biz_type 一致）
     */
    String bizType();

    /**
     * 加载单据快照
     *
     * @param bizId 业务单据 ID
     * @return 快照；单据不存在/已删返回 null（由发起方转 40048）
     */
    BizSnapshot load(Long bizId);
}
