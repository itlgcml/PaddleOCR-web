-- ----------------------------------------------------------------
-- 04-lawfirmselect-schema.sql
-- 律所选聘记录表 DDL
-- 适用：MySQL 5.7+ / utf8mb4
-- 说明：全库不使用物理外键，关联性由 Service 层校验
-- ----------------------------------------------------------------

-- 1. 律所选聘记录表
CREATE TABLE law_firm_select (
    id          BIGINT        NOT NULL COMMENT '雪花 ID',
    title       VARCHAR(128)  NOT NULL COMMENT '标题',
    amount      DECIMAL(12,2) NOT NULL COMMENT '选聘金额（元）',
    reason      VARCHAR(512)  NOT NULL COMMENT '选聘原因',
    create_time DATETIME      NOT NULL COMMENT '创建时间',
    update_time DATETIME      NOT NULL COMMENT '更新时间',
    deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='律所选聘记录表';
