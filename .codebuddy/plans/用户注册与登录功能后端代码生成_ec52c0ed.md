---
name: 用户注册与登录功能后端代码生成
overview: 按《用户注册与登录功能.md》设计文档，在 backend 多模块工程中生成账号体系 + 机构树 + 角色体系 + JWT 认证鉴权的全部后端代码（M1~M4 里程碑），含 DDL 脚本、四张表 DO/Mapper、Spring Security 过滤器链、Redis 黑名单、认证与系统设置域 Controller，并追加 ResultCode 错误码与 yml 配置。
todos:
  - id: m1-data-layer
    content: 使用 [skill:java-p3c-generate] 生成数据层：02-auth-schema.sql 四表 DDL+初始化数据、4 个 DO 实体、4 个 Mapper（UserMapper 含登录失败计数原子 SQL）
    status: completed
  - id: m2-security-infra
    content: 使用 [skill:paddleocr-java-conventions] 改父 POM（jjwt 三件套收口）+ 各模块 POM 依赖，追加 ResultCode 20 个错误码，生成 PasswordSupport、JwtTokenProvider、AuthProperties、TokenBlacklistService，GlobalExceptionHandler 追加三 Handler
    status: completed
    dependencies:
      - m1-data-layer
  - id: m3-service-layer
    content: 使用 [skill:java-p3c-generate] 生成认证域与 system 域全部 DTO/VO 与 Service（UserService/AuthService/OrgService/RoleService/UserAdminService 接口+Impl，注册默认机构+默认角色、角色全量替换事务）
    status: completed
    dependencies:
      - m2-security-infra
  - id: m4-web-layer
    content: 使用 [skill:java-p3c-generate] 生成 ocr-web 安全链路（SecurityConfig、JwtAuthenticationFilter 含异常手动接管、UserDetailsServiceImpl、RestAuthEntryPoint/RestAccessDeniedHandler、AuthFailureException、LoginUser）与 4 个 Controller（@PreAuthorize + 禁止操作自己），OpenApiConfig + yml 追加 spring.redis/security.jwt 配置块
    status: completed
    dependencies:
      - m3-service-layer
  - id: final-checklist
    content: 执行 [skill:java-p3c-generate] --check 自检清单：模块归属、依赖方向、JDK 8 语法、P3C 阻断项逐项核验并修正
    status: completed
    dependencies:
      - m4-web-layer
---

## 用户需求
根据设计文档 `backend-docs/用户注册与登录功能.md`，在 `backend/` 多模块工程中生成全部后端代码，为 PaddleOCR-web 补齐账号体系 + 组织架构（机构树）+ 角色体系 + 认证鉴权。

## 产品概述
纯增量改造，不改动既有类逻辑（仅追加 ResultCode 错误码、GlobalExceptionHandler 三个 Handler、application.yml 配置块、父 POM 依赖）。范围限定后端里程碑 M1~M4，前端（M5）与单元测试不在本次范围。

## 核心功能
- 账号密码注册（用户名+邮箱+密码+可选机构，默认挂 DEFAULT 机构、绑 USER 角色）
- 账号密码登录（Spring Security AuthenticationManager 认证，签发 accessToken 2h + refreshToken 7d），登录失败 5 次锁 15 分钟（SQL 原子自增）
- JWT 请求鉴权：白名单放行 + JwtAuthenticationFilter 全局拦截；登出经 Redis jti 黑名单即时吊销；`/api/auth/refresh` 静默续期
- 机构管理：树查询（ancestors 祖先链内存组树）、增删改（删除校验子机构/在职员工）
- 角色管理：分页查询、增删改（内置角色 ADMIN/USER 保护、删除校验绑定）
- 用户管理：分页查询、机构调整、角色全量分配、启禁用（禁用即时生效，禁止操作自己）
- `@PreAuthorize("hasRole('ADMIN')")` 方法级鉴权，401/403 统一同构 ApiResponse JSON 输出


## Tech Stack（沿用文档锁定选型，已核实现有基础设施）
- JDK 8 + Spring Boot 2.7.18 + `javax.*`（禁 jakarta），多模块 Maven（ocr-common/ocr-client/ocr-dal/ocr-service/ocr-web）
- MyBatis-Plus 3.5.17 + Druid 1.2.28 + MySQL（逻辑删除 `deleted`、审计自动填充、`id-type: assign_id` 雪花 ID）
- Spring Security 5.7.11（Boot BOM 托管，声明在 ocr-web）：`SecurityFilterChain` + `DaoAuthenticationProvider` + `UserDetailsService` + `@EnableGlobalMethodSecurity`
- BCrypt（strength=10）密码散列；JJWT 0.11.5（ocr-service，HS256，claims 含 jti/type）
- Redis（spring-boot-starter-data-redis，Lettuce，声明在 ocr-service）：仅 jti 吊销黑名单，读故障 fail-open、写故障返回 50000
- springdoc-openapi-ui 1.8.0 + Bearer 安全校验；Lombok @Slf4j 日志

## Implementation Approach
严格按文档 §5 分层落地与 §12 里程碑顺序实施：数据层（DDL+DO+Mapper）→ 安全基础设施（错误码+依赖+JWT+黑名单+SecurityConfig）→ 业务 Service（认证域 + system 系统设置域）→ Controller + Filter + yml。所有代码遵循 java-p3c-generate 技能的 P3C 生成铁律（命名/常量/格式/OOP/集合/异常/日志 + JDK 8 语法红线）。

关键决策（文档已裁决，实施中不可偏离）：
1. **Filter 异常手动接管**：`JwtAuthenticationFilter` 挂在 `UsernamePasswordAuthenticationFilter` 之前，Token 异常必须 Filter 内 try-catch 后手动调 `restAuthEntryPoint.commence()` 写响应（40101/40102/40103/40023），否则穿透成 500。
2. **403 双通路**：URL 级走 `RestAccessDeniedHandler`；`@PreAuthorize` 的 `AccessDeniedException` 走 MVC 层，必须在 `GlobalExceptionHandler` 显式新增 Handler（40300），否则被 Exception 兜底吃成 500。
3. **登录失败计数 SQL 原子自增**：`UPDATE sys_user SET failed_attempts = failed_attempts + 1, locked_until = IF(...) WHERE id = ?`（UserMapper 用 @Update 注解或 XML 单条语句），禁止 read-modify-write。
4. **角色不放 JWT 每次查库**（撤权即时生效）；`security.jwt.enabled=false` 软鉴权降级开关（黑名单校验一并跳过）。
5. **跨模块红线**：Spring Security web/config 类只允许出现在 ocr-web；ocr-service 仅 spring-security-crypto（PasswordSupport）；ocr-common 零依赖（userId 由 Controller 从 Authentication 取出作参数传 Service）。
6. **n:m 角色查询**：按 user_id 查关联 + 按 role_id 集合查角色，两次单表查询内存组装，无需 mapper XML。

## Implementation Notes
- 复用现有 `ApiResponse<T>` / `ResultCode` / `BusinessException` / `PageResultVO` / `AuditMetaObjectHandler`，禁止自造返回结构；`anyRequest().permitAll()` 保证 Druid `/druid/**` 与 swagger-ui 不被新安全链拦截。
- OPTIONS 预检 `.antMatchers(HttpMethod.OPTIONS).permitAll()`（CorsConfig 是 MVC 层实现，Security 侧不启用 `http.cors()`，两套 CORS 不叠加）。
- `spring.redis.timeout: 1000ms` 必须显式配置（Lettuce 默认 60s 会拖死请求线程，fail-open 前提是快速失败）。
- 事务红线：`@Transactional(rollbackFor = Exception.class)` 只加 ServiceImpl；失败计数/登录成功各自短事务，不包 BCrypt 校验于同一大事务。
- 日志：占位符方式，WARN 级别记录 Redis 降级，禁止打印密码原文/token。
- 性能：机构树一次查全表内存组树（`Map<Long, List<OrgDO>>` 按 parentId 分组一次遍历）；每次请求角色查库 2 次单表 SQL（用户量小可接受，V2 再缓存）。

## Architecture Design
```mermaid
graph TD
    subgraph ocr-web
        AC[AuthController] --> AS[AuthService]
        SYS[system/OrgController RoleController UserAdminController] --> SYSV[system/OrgService RoleService UserAdminService]
        JF[JwtAuthenticationFilter] --> JTP[JwtTokenProvider]
        JF --> UDS[UserDetailsServiceImpl]
        UDS --> US[UserService]
        SC[SecurityConfig] --> JF
    end
    subgraph ocr-service
        AS --> US
        JTP --> AP[AuthProperties]
        TB[TokenBlacklistService] --> RD[(Redis)]
        PS[PasswordSupport]
        AS --> JTP
    end
    subgraph ocr-dal
        US --> UM[UserMapper]
        SYSV --> OM[OrgMapper RM[RoleMapper]]
        UM --> DO[UserDO OrgDO RoleDO UserRoleDO]
    end
    DO --> DB[(MySQL sys_user/sys_org/sys_role/sys_user_role)]
```

## Directory Structure
```
backend/
├── pom.xml                                        # [MODIFY] properties 加 jjwt.version=0.11.5；dependencyManagement 收口 jjwt-api/impl/jackson 三件套
├── sql/
│   └── 02-auth-schema.sql                         # [NEW] 四表 DDL + ROOT/DEFAULT 机构、ADMIN/USER 角色初始化数据（按文档 §4 原文）
├── ocr-common/src/main/java/com/paddleocr/web/common/
│   └── ResultCode.java                            # [MODIFY] 追加 20 个错误码（40100~40034，按 §6 表格）
├── ocr-dal/src/main/java/com/paddleocr/web/mapper/
│   ├── entity/UserDO.java                         # [NEW] sys_user 实体（含 @JsonProperty(WRITE_ONLY) 密码、审计填充、@TableLogic）
│   ├── entity/OrgDO.java                          # [NEW] sys_org 实体（parent_id + ancestors 祖先链）
│   ├── entity/RoleDO.java                         # [NEW] sys_role 实体（built_in 内置保护）
│   ├── entity/UserRoleDO.java                     # [NEW] sys_user_role 实体（无 deleted/updateTime，物理删除）
│   ├── UserMapper.java                            # [NEW] extends BaseMapper + 登录失败计数/清零/登录信息 @Update 原子 SQL
│   ├── OrgMapper.java / RoleMapper.java / UserRoleMapper.java  # [NEW] extends BaseMapper
├── ocr-service/pom.xml                            # [MODIFY] 声明 spring-boot-starter-data-redis + spring-security-crypto（BOM 托管不带版本）
├── ocr-service/src/main/java/com/paddleocr/web/
│   ├── PasswordSupport.java                       # [NEW] BCrypt 加密工具（仅依赖 spring-security-crypto）
│   ├── UserService.java + impl/UserServiceImpl.java   # [NEW] 注册、查询、锁定策略、角色编码查询（认证域）
│   ├── AuthService.java + impl/AuthServiceImpl.java   # [NEW] 登录编排、Token 签发、refresh、logout 吊销
│   ├── system/OrgService.java + impl/OrgServiceImpl.java       # [NEW] 机构树构建（内存组树）、增删改、三重删除校验
│   ├── system/RoleService.java + impl/RoleServiceImpl.java     # [NEW] 角色分页增删改、内置保护、绑定校验
│   ├── system/UserAdminService.java + impl/UserAdminServiceImpl.java  # [NEW] 用户分页、机构调整、角色全量替换（事务内 delete+batch insert）、启禁用
│   ├── model/dto/                                  # [NEW] RegisterDTO/LoginDTO/RefreshTokenDTO/LogoutDTO/OrgSaveDTO/OrgUpdateDTO/RoleSaveDTO/RoleUpdateDTO/UserRoleAssignDTO/UserOrgAssignDTO/UserStatusDTO/UserPageQuery（@Validated 校验注解按 §9）
│   ├── model/vo/                                  # [NEW] LoginVO/TokenVO/UserInfoVO/OrgBriefVO/OrgTreeVO/RoleVO/UserPageVO（Long id 一律 ToStringSerializer）
│   └── security/
│       ├── JwtTokenProvider.java                  # [NEW] generateAccessToken/RefreshToken/parse（jti=UUID、type 校验、过期/签名异常抛 BusinessException）
│       ├── AuthProperties.java                    # [NEW] @ConfigurationProperties("security.jwt")：secret/ttl/maxFailedAttempts/lockDurationMinutes/enabled
│       └── TokenBlacklistService.java             # [NEW] Redis jti 黑名单（revoke SET EX / isRevoked EXISTS，读故障 fail-open WARN，写故障抛 50000）
├── ocr-web/pom.xml                                # [MODIFY] 声明 spring-boot-starter-security（BOM 托管不带版本）
└── ocr-web/src/main/java/com/paddleocr/web/
    ├── controller/AuthController.java             # [NEW] 注册/登录/refresh/me/logout（登录走 authenticationManager，异常翻译 BadCredentials→40022 等）
    ├── controller/system/OrgController.java       # [NEW] /api/orgs 树/增删改 @PreAuthorize(ADMIN)
    ├── controller/system/RoleController.java      # [NEW] /api/roles 分页/all/增删改 @PreAuthorize(ADMIN)
    ├── controller/system/UserAdminController.java # [NEW] /api/users 分页/机构/角色/状态 @PreAuthorize(ADMIN)，禁止操作自己
    ├── controller/advice/GlobalExceptionHandler.java  # [MODIFY] 追加 AccessDeniedException(40300)/DuplicateKeyException(按唯一约束名映射)/AuthenticationException(40100) 三 Handler
    ├── security/
    │   ├── LoginUser.java                         # [NEW] 继承 org.springframework.security.core.userdetails.User，扩展 userId 字段
    │   ├── UserDetailsServiceImpl.java            # [NEW] 装载用户+角色为 UserDetails（ROLE_ 前缀 authorities、enabled/accountLocked 状态映射）
    │   ├── JwtAuthenticationFilter.java           # [NEW] OncePerRequestFilter：7 步逻辑（无头直通/type 校验/黑名单/禁用检查/异常手动接管 commence）
    │   ├── AuthFailureException.java              # [NEW] extends AuthenticationException，携带 ResultCode
    │   ├── RestAuthEntryPoint.java                # [NEW] AuthenticationEntryPoint：全局 ObjectMapper 写 401 JSON（识别 AuthFailureException 的 code）
    │   └── RestAccessDeniedHandler.java           # [NEW] AccessDeniedHandler：写 403 JSON（40300）
    ├── config/SecurityConfig.java                 # [NEW] SecurityFilterChain + AuthenticationManager + DaoAuthenticationProvider + BCryptPasswordEncoder + @EnableGlobalMethodSecurity + enabled 降级开关
    └── config/OpenApiConfig.java                  # [NEW] springdoc Bearer 安全校验配置
ocr-web/src/main/resources/application.yml          # [MODIFY] 追加 spring.redis.*（timeout: 1000ms）与 security.jwt.*（secret: ${JWT_SECRET}）配置块
```


## Agent Extensions
### Skill
- **java-p3c-generate**
  - Purpose: 生成全部 Java 后端代码时强制遵循 P3C 生成铁律（模块归属、JDK 8 语法红线、命名/常量/集合/异常/日志规约），加载 core.md 与对应分域规则（rules-dal / rules-service / rules-web）
  - Expected outcome: 生成代码零 P3C 阻断级违规，模块归属正确（DO/Mapper→ocr-dal，Service/DTO/VO→ocr-service，Controller/安全配置→ocr-web），交付前通过 checklist.md 自检
### Skill
- **paddleocr-java-conventions**
  - Purpose: 复用项目级铁律与代码模板（父 POM 结构、ApiResponse/MyBatis-Plus 配置、依赖方向约束），确保新代码与现有工程骨架一致
  - Expected outcome: 版本收口在父 POM、依赖方向不倒置、yml 规范一致，与既有基础设施无缝集成
