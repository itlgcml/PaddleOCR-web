-- ----------------------------------------------------------------
-- 02-auth-schema.sql
-- 账号体系 + 机构树 + 角色体系 DDL 与初始化数据
-- 适用：MySQL 5.7+ / utf8mb4
-- 说明：全库不使用物理外键，关联性由 Service 层校验
-- ----------------------------------------------------------------

-- 1. 系统用户表(员工)
CREATE TABLE sys_user (
    id            BIGINT       NOT NULL COMMENT '雪花 ID',
    org_id        BIGINT       DEFAULT NULL COMMENT '所属机构 ID（1:n 机构方持有），NULL=未分配',
    username      VARCHAR(32)  NOT NULL COMMENT '用户名，登录用',
    nickname      VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    email         VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    password_hash CHAR(60)     NOT NULL COMMENT 'BCrypt 密文，定长 60',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    failed_attempts INT        NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    locked_until  DATETIME     DEFAULT NULL COMMENT '锁定到期时间',
    last_login_time DATETIME   DEFAULT NULL COMMENT '最近登录时间',
    last_login_ip VARCHAR(64)  DEFAULT NULL COMMENT '最近登录 IP',
    create_time   DATETIME     NOT NULL COMMENT '创建时间',
    update_time   DATETIME     NOT NULL COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_org_id (org_id),
    KEY idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表(员工)';

-- 2. 机构表(树形)
CREATE TABLE sys_org (
    id          BIGINT       NOT NULL COMMENT '雪花 ID',
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父机构 ID，0=根节点',
    ancestors   VARCHAR(512) NOT NULL DEFAULT '0' COMMENT '祖先链，逗号分隔，如 0,1001,1002',
    org_code    VARCHAR(32)  NOT NULL COMMENT '机构编码，业务唯一',
    org_name    VARCHAR(64)  NOT NULL COMMENT '机构名称',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '同级排序，越小越靠前',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
    create_time DATETIME     NOT NULL COMMENT '创建时间',
    update_time DATETIME     NOT NULL COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_org_code (org_code),
    KEY idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机构表(树形)';

-- 3. 角色表
CREATE TABLE sys_role (
    id          BIGINT       NOT NULL COMMENT '雪花 ID',
    role_code   VARCHAR(64)  NOT NULL COMMENT '角色编码，程序鉴权用，如 ADMIN / USER',
    role_name   VARCHAR(64)  NOT NULL COMMENT '角色名称，展示用',
    description VARCHAR(255) DEFAULT NULL COMMENT '角色描述',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
    built_in    TINYINT      NOT NULL DEFAULT 0 COMMENT '1=内置角色禁止修改删除',
    create_time DATETIME     NOT NULL COMMENT '创建时间',
    update_time DATETIME     NOT NULL COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 4. 用户角色关联表(n:m)
CREATE TABLE sys_user_role (
    id          BIGINT   NOT NULL COMMENT '雪花 ID',
    user_id     BIGINT   NOT NULL COMMENT '用户 ID',
    role_id     BIGINT   NOT NULL COMMENT '角色 ID',
    create_time DATETIME NOT NULL COMMENT '绑定时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表(n:m)';

-- ----------------------------------------------------------------
-- 初始化数据：内置机构与内置角色
-- ----------------------------------------------------------------
INSERT INTO sys_org (id, parent_id, ancestors, org_code, org_name, sort, status, create_time, update_time, deleted)
VALUES (1, 0, '0', 'ROOT', '根机构', 0, 1, NOW(), NOW(), 0),
       (2, 1, '0,1', 'DEFAULT', '默认机构', 0, 1, NOW(), NOW(), 0);

INSERT INTO sys_role (id, role_code, role_name, description, status, built_in, create_time, update_time, deleted)
VALUES (1, 'ADMIN', '系统管理员', '管理机构/角色/用户分配', 1, 1, NOW(), NOW(), 0),
       (2, 'USER',  '普通用户',   '注册默认角色',           1, 1, NOW(), NOW(), 0);
