-- ----------------------------------------------------------------
-- 03-approval-schema.sql
-- 审批流程配置域 DDL（流程定义 + 流程版本）
-- 适用：MySQL 5.7+ / utf8mb4
-- 说明：全库不使用物理外键，关联性由 Service 层校验
-- ----------------------------------------------------------------

-- 1. 审批流程定义表
CREATE TABLE flow_process (
    id              BIGINT       NOT NULL COMMENT '雪花 ID',
    code            VARCHAR(64)  NOT NULL COMMENT '流程编码，业务唯一',
    name            VARCHAR(64)  NOT NULL COMMENT '流程名称',
    description     VARCHAR(255) DEFAULT NULL COMMENT '流程描述',
    start_path      VARCHAR(200) NOT NULL COMMENT '前端发起路径，以 / 开头',
    detail_path     VARCHAR(200) NOT NULL COMMENT '前端详情路径，以 / 开头',
    initiator_type  VARCHAR(16)  NOT NULL DEFAULT 'ALL' COMMENT '发起范围：ALL=全体 SPECIFIC=指定',
    initiator_members VARCHAR(512) NOT NULL DEFAULT '[]' COMMENT '指定人员 ID(JSON 数组，SPECIFIC 时生效)',
    initiator_roles   VARCHAR(512) NOT NULL DEFAULT '[]' COMMENT '指定角色 ID(JSON 数组，SPECIFIC 时生效)',
    status          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT=草稿 PUBLISHED=已发布 DISABLED=已停用',
    current_version VARCHAR(16)  DEFAULT NULL COMMENT '当前已发布版本号，如 v1；从未发布时为 NULL',
    draft_json      LONGTEXT     DEFAULT NULL COMMENT '草稿流程设计 JSON(WorkflowDoc)，编辑保存区，与发布快照分离',
    create_time     DATETIME     NOT NULL COMMENT '创建时间',
    update_time     DATETIME     NOT NULL COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程定义表';

-- 2. 审批流程版本表
CREATE TABLE flow_process_version (
    id          BIGINT      NOT NULL COMMENT '雪花 ID',
    process_id  BIGINT      NOT NULL COMMENT '流程定义 ID',
    version     VARCHAR(16) NOT NULL COMMENT '版本号，如 v1/v2/v3',
    design_json LONGTEXT    NOT NULL COMMENT '发布时冻结的流程设计 JSON 快照(WorkflowDoc，不可变)',
    create_time DATETIME    NOT NULL COMMENT '创建时间',
    update_time DATETIME    NOT NULL COMMENT '更新时间',
    deleted     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_process_version (process_id, version),
    KEY idx_process_id (process_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程版本表';
