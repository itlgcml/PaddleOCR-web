-- ----------------------------------------------------------------
-- 06-sub-process-schema.sql
-- 子流程节点扩展 DDL（flow_instance 增列 + 存量回填，一次性执行）
-- 适用：MySQL 5.7+ / utf8mb4
-- 说明：全库不使用物理外键；子实例与根实例同表，parent_instance_id IS NULL 即根实例
-- 设计依据：backend-docs/工作流子流程节点设计.md §4.1
-- ----------------------------------------------------------------

-- 1. 审批流程实例表增列（子流程实例支持）
ALTER TABLE flow_instance
    ADD COLUMN parent_instance_id BIGINT      DEFAULT NULL COMMENT '父实例 ID(子流程实例指向父实例；根实例为 NULL)' AFTER biz_title,
    ADD COLUMN parent_node_id     VARCHAR(64) DEFAULT NULL COMMENT '父流程中的子流程节点 ID(SUB_PROCESS 节点 id)' AFTER parent_instance_id,
    ADD COLUMN root_instance_id   BIGINT      NOT NULL DEFAULT 0 COMMENT '流程树根实例 ID(根实例=自身；树级推进锁/级联按此)' AFTER parent_node_id,
    ADD COLUMN depth              TINYINT     NOT NULL DEFAULT 0 COMMENT '嵌套深度(根实例=0)' AFTER root_instance_id,
    ADD COLUMN design_json        LONGTEXT    DEFAULT NULL COMMENT '子流程设计快照(子实例=父版本快照内 SUB_PROCESS 节点的 subDoc；根实例为 NULL 读版本表)' AFTER depth,
    ADD COLUMN advance_mode       VARCHAR(16) DEFAULT NULL COMMENT '推进模式(子实例冗余创建时的节点 advanceMode：WAIT=父等待完成/IMMEDIATE=父启动即继续；根实例为 NULL)' AFTER design_json,
    ADD KEY idx_parent (parent_instance_id),
    ADD KEY idx_root (root_instance_id, status);

-- 2. 存量数据回填：既有实例均为根实例
UPDATE flow_instance SET root_instance_id = id WHERE root_instance_id = 0;
