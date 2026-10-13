-- ----------------------------------------------------------------
-- 05-approval-runtime-schema.sql
-- 审批流程运行态 DDL（实例 + 任务 + 审批记录 + 抄送）
-- 适用：MySQL 5.7+ / utf8mb4
-- 说明：全库不使用物理外键，关联性由 Service 层校验
-- 设计依据：backend-docs/工作流提交与审批操作接口设计.md §4
-- ----------------------------------------------------------------

-- 1. 审批流程实例表
CREATE TABLE flow_instance (
    id                 BIGINT        NOT NULL COMMENT '雪花 ID',
    process_id         BIGINT        NOT NULL COMMENT '流程定义 ID',
    process_code       VARCHAR(64)   NOT NULL COMMENT '流程编码(冗余)',
    process_name       VARCHAR(64)   NOT NULL COMMENT '流程名称(冗余)',
    process_version    VARCHAR(16)   NOT NULL COMMENT '发起时的已发布版本号，如 v2',
    process_version_id BIGINT        NOT NULL COMMENT '流程版本 ID，指向 flow_process_version 不可变快照',
    biz_type           VARCHAR(32)   NOT NULL COMMENT '业务单据类型：LAW_FIRM_SELECT=律所选聘',
    biz_id             BIGINT        NOT NULL COMMENT '业务单据 ID',
    biz_title          VARCHAR(128)  NOT NULL COMMENT '业务标题(发起时冗余快照，列表展示)',
    form_snapshot      LONGTEXT      NOT NULL COMMENT '发起时表单数据 JSON 快照(条件求值与详情展示)',
    start_user_id      BIGINT        NOT NULL COMMENT '发起人 ID',
    start_user_name    VARCHAR(64)   NOT NULL COMMENT '发起人名称(冗余)',
    status             VARCHAR(16)   NOT NULL DEFAULT 'RUNNING' COMMENT '状态：RUNNING=进行中 COMPLETED=已完成 REJECTED=已拒绝 CANCELED=已撤销',
    finish_time        DATETIME      DEFAULT NULL COMMENT '结束时间(REJECTED/COMPLETED/CANCELED 时写入)',
    create_time        DATETIME      NOT NULL COMMENT '创建时间',
    update_time        DATETIME      NOT NULL COMMENT '更新时间',
    deleted            TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    KEY idx_biz (biz_type, biz_id),
    KEY idx_start_user (start_user_id),
    KEY idx_process (process_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程实例表';

-- 2. 审批任务表
CREATE TABLE flow_task (
    id             BIGINT       NOT NULL COMMENT '雪花 ID',
    instance_id    BIGINT       NOT NULL COMMENT '实例 ID',
    node_id        VARCHAR(64)  NOT NULL COMMENT '节点 ID(WorkflowNode.id)',
    node_name      VARCHAR(64)  NOT NULL COMMENT '节点名称(冗余)',
    assignee_id    BIGINT       NOT NULL COMMENT '审批人用户 ID',
    assignee_name  VARCHAR(64)  NOT NULL COMMENT '审批人名称(冗余)',
    multi_mode     VARCHAR(8)   NOT NULL DEFAULT 'OR' COMMENT '多实例模式快照：OR=或签 AND=会签',
    sign_type      VARCHAR(16)  NOT NULL DEFAULT 'NORMAL' COMMENT '任务派生类型：NORMAL=普通 BEFORE_SIGN/AFTER_SIGN=前/后加签 TRANSFER=转办 DELEGATE=委托',
    parent_task_id BIGINT       DEFAULT NULL COMMENT '派生源任务 ID：加签任务挂源任务之下（转办/委托新任务为 NULL，链路由日志关联），根任务为 NULL',
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING=待办 PASSED=通过 REJECTED=已拒绝 CANCELED=已取消 TRANSFERRED=已转出',
    action         VARCHAR(16)  DEFAULT NULL COMMENT '完成动作：PASS/REJECT/RETURN/TRANSFER/DELEGATE/SUBMIT，PENDING 为 NULL',
    comment        VARCHAR(512) DEFAULT NULL COMMENT '审批意见(任务完成时冗余最近一次)',
    finish_time    DATETIME     DEFAULT NULL COMMENT '完成时间',
    create_time    DATETIME     NOT NULL COMMENT '创建时间',
    update_time    DATETIME     NOT NULL COMMENT '更新时间',
    deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    KEY idx_instance_node (instance_id, node_id),
    KEY idx_assignee (assignee_id, status),
    KEY idx_parent (parent_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批任务表';

-- 3. 审批记录表
CREATE TABLE flow_task_log (
    id            BIGINT       NOT NULL COMMENT '雪花 ID',
    instance_id   BIGINT       NOT NULL COMMENT '实例 ID',
    task_id       BIGINT       DEFAULT NULL COMMENT '关联任务 ID(系统动作为 NULL)',
    node_id       VARCHAR(64)  DEFAULT NULL COMMENT '节点 ID',
    node_name     VARCHAR(64)  DEFAULT NULL COMMENT '节点名称',
    action        VARCHAR(16)  NOT NULL COMMENT '动作：START/PASS/REJECT/RETURN/TRANSFER/DELEGATE/ADD_SIGN/SUBMIT/CANCEL/COMMENT/COPY/AUTO_PASS/ENGINE',
    operator_id   BIGINT       DEFAULT NULL COMMENT '操作人 ID(引擎动作为 NULL)',
    operator_name VARCHAR(64)  DEFAULT NULL COMMENT '操作人名称(引擎动作存 系统自动)',
    comment       VARCHAR(512) DEFAULT NULL COMMENT '意见',
    detail        VARCHAR(512) DEFAULT NULL COMMENT '动作摘要：如 退回到【部门审批】；LEADER 降级为 TO_ADMIN 等',
    create_time   DATETIME     NOT NULL COMMENT '创建时间',
    update_time   DATETIME     NOT NULL COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    KEY idx_instance (instance_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批记录表';

-- 4. 抄送记录表
CREATE TABLE flow_cc (
    id             BIGINT       NOT NULL COMMENT '雪花 ID',
    instance_id    BIGINT       NOT NULL COMMENT '实例 ID',
    task_id        BIGINT       DEFAULT NULL COMMENT '触发抄送的任务 ID(发起时抄送为 NULL)',
    receiver_id    BIGINT       NOT NULL COMMENT '接收人 ID',
    receiver_name  VARCHAR(64)  NOT NULL COMMENT '接收人名称(冗余)',
    status         VARCHAR(16)  NOT NULL DEFAULT 'UNREAD' COMMENT '状态：UNREAD=待阅 READ=已阅',
    read_time      DATETIME     DEFAULT NULL COMMENT '阅读时间',
    create_time    DATETIME     NOT NULL COMMENT '创建时间',
    update_time    DATETIME     NOT NULL COMMENT '更新时间',
    deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    KEY idx_receiver (receiver_id, status),
    KEY idx_instance (instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抄送记录表';
