-- ----------------------------------------------------------------
-- 07-return-enhance-schema.sql
-- 节点退回增强 DDL（退回方式 + 退回指定审批人 + 退回记录表，一次性执行）
-- 适用：MySQL 5.7+ / utf8mb4
-- 说明：退回是「事件/记录」性质数据，按次落 flow_return_record 表——
--       DIRECT 退回记 PENDING 待目标节点完成消费直达（接力退回各自成记录，
--       嵌套直达意图互不覆盖），STEP 退回记 FINISHED 纯审计留痕；
--       拒绝退回与实例终态将未消费 PENDING 置 RESET。
--       （初版 return_context 单列方案已废弃：并行/接力场景下单值覆盖丢意图且无审计）
-- 设计依据：backend-docs/工作流提交与审批操作接口设计.md §4.6.6
-- ----------------------------------------------------------------

-- 1. 审批退回记录表
CREATE TABLE flow_return_record (
    id             BIGINT       NOT NULL COMMENT '雪花 ID',
    instance_id    BIGINT       NOT NULL COMMENT '实例 ID',
    from_task_id   BIGINT       DEFAULT NULL COMMENT '退回发起任务 ID',
    from_node_id   VARCHAR(64)  NOT NULL COMMENT '退回发起节点 ID（直达消费后在其上重建任务）',
    target_node_id VARCHAR(64)  NOT NULL COMMENT '退回目标节点 ID（START=根节点=退回发起人）',
    return_mode    VARCHAR(16)  NOT NULL COMMENT '退回方式：STEP=逐级审批 DIRECT=审批后直达本节点',
    status         VARCHAR(16)  NOT NULL COMMENT '状态：PENDING=待直达(DIRECT 专属) CONSUMED=已直达 RESET=未消费重置(拒绝退回/实例终态) FINISHED=逐级记录(无直达语义)',
    create_time    DATETIME     NOT NULL COMMENT '创建时间',
    update_time    DATETIME     NOT NULL COMMENT '更新时间',
    deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    KEY idx_instance_target (instance_id, status, target_node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批退回记录表';
