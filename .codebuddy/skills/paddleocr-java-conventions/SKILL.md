---
name: paddleocr-java-conventions
description: Use when writing, generating, or reviewing Java backend code in this project (backend multi-module Maven: ocr-common, ocr-client, ocr-dal, ocr-service, ocr-web under backend/pom.xml). Covers JDK 8 + Spring Boot 2.7.18 + MyBatis-Plus 3.5.x + Druid 1.2.x multi-module conventions, module dependency rules, unified response, global exception handling, and OCR service call rules.
---

# PaddleOCR-web Java 后端规范（JDK 8 / Boot 2.7.18 / 多模块 Maven）

## 铁律（最高优先级）

| 项 | 约束 |
|---|---|
| JDK | 8（1.8），禁止任何 JDK 9+ 语法（见下表） |
| Spring Boot | 2.7.18，禁止 Boot 3.x 配置写法与 starter |
| 命名空间 | 一律 `javax.*`（`javax.validation` / `javax.servlet`），**禁止 `jakarta.*`** |
| 工程结构 | Maven 多模块：父 `backend/pom.xml`（`com.paddleocr:paddle-ocr-backend`，packaging=pom）+ ocr-common / ocr-client / ocr-dal / ocr-service / ocr-web |
| 模块依赖方向 | `ocr-web → ocr-service → {ocr-client → ocr-common, ocr-dal}`；**禁止倒置与循环依赖** |
| 根包统一 | 所有模块一律 `com.paddleocr.web.*`，启动类组件扫描天然覆盖；**禁止改根包、禁止额外 `@ComponentScan`** |
| 版本收口 | 版本号只写在父 POM `<properties>` + `<dependencyManagement>`；**子模块依赖声明禁止带 `<version>`** |
| 持久层 | MyBatis-Plus 3.5.17，`mybatis-plus-boot-starter` + `mybatis-plus-jsqlparser`（分页必需；禁用 boot3 版 starter），只在 ocr-dal |
| 连接池 | Druid 1.2.28，`druid-spring-boot-starter`（禁用 3-starter），只在 ocr-dal |
| MySQL 驱动 | `com.mysql:mysql-connector-j`，`runtime` scope，版本交父 POM |
| API 文档 | springdoc-openapi-ui 1.8.0（禁用 springfox），只在 ocr-web |
| HTTP 客户端 | HttpClient5 5.6.4 + httpcore5 5.4.3（覆盖 BOM 的 5.1.5），双客户端 Bean（见 ocr-client 段） |
| Web 依赖 | `spring-boot-starter-web` / `-validation` 只允许 ocr-web；其他模块用 `spring-boot-starter` |
| 可执行 jar | 只有 ocr-web 配 `spring-boot-maven-plugin`，其余模块打普通 jar |
| SQL 脚本 | 建表 DDL 放 `backend/sql/` |

## JDK 8 语法红线

| 禁用（JDK 9+） | 替代 |
|---|---|
| `var` | 显式类型 |
| `List.of()` / `Map.of()` / `Set.of()` | `new ArrayList<>()` + `Collections.unmodifiableXxx`，或 `CollUtil`（Hutool） |
| `stream.toList()` | `.collect(Collectors.toList())` |
| `Optional.isEmpty()` / `ifPresentOrElse` | `!opt.isPresent()` |
| `String.isBlank()` / `strip()` / `repeat()` | `StrUtil.isBlank()`（Hutool） |
| Text Blocks `"""` | 普通字符串拼接 |
| record 类 | 普通 class + Lombok `@Data` |
| switch 表达式 / 接口私有方法 | 传统 switch / 默认方法或工具类 |

JDK 8 可用：Lambda、Stream、`Optional`、`LocalDateTime`、`CompletableFuture`。

## 模块划分（代码放错模块 = 返工）

| 模块 | 根包 | 职责 | 禁止 |
|---|---|---|---|
| ocr-common | `com.paddleocr.web.common` | ApiResponse / ResultCode / BusinessException，**零 Spring 依赖** | 引入任何 Spring / Servlet / MyBatis 依赖 |
| ocr-client | `com.paddleocr.web.client`（`config` / `constant` / `model`） | 外部服务 HTTP 集成：`ApiClient`（通用）、`OcrClient`（OCR 专用）、`OcrApi` 路径常量、双 HttpClient 配置、外部服务出参 VO | 业务逻辑；被 controller 直接调用 |
| ocr-dal | `com.paddleocr.web.mapper`（`config`）、`entity` | DO、Mapper 接口、`MybatisPlusConfig`、`AuditMetaObjectHandler`、mapper XML（`resources/mapper/`） | 依赖 ocr-client / ocr-service；`apply`/`last` 拼接用户输入 |
| ocr-service | `com.paddleocr.web.service`（`.impl`）、`model` | 业务编排、事务边界、DTO/出参 VO（如 `PageResultVO`） | HTTP 调用细节；出现 Controller 相关类 |
| ocr-web | `com.paddleocr.web.controller`（`advice`）、`config` | 启动类 `PaddleOcrWebApplication`、Controller、`GlobalExceptionHandler`、Jackson/CORS 配置、全部 `application*.yml` | 业务逻辑、直接调 mapper |

新增类先判断归属模块：DO/Mapper → ocr-dal；外部调用 → ocr-client；业务/VO → ocr-service；Controller/全局配置 → ocr-web。

## 统一响应体与异常

- 所有接口返回 `ApiResponse<T>`：`code`（int，0=成功）/ `message` / `data` / `timestamp`（定义在 ocr-common）
- 错误码枚举 `ResultCode`：0 成功；4xxxx 客户端；5xxxx 系统；503xx OCR 服务专用段
- 业务异常统一 `BusinessException(ResultCode)`，**禁止 controller 手动 try-catch 拼错误响应**
- `GlobalExceptionHandler`（`@RestControllerAdvice`）位于 `ocr-web` 的 `com.paddleocr.web.controller.advice`，必须覆盖：`BusinessException`、`MethodArgumentNotValidException`、`MaxUploadSizeExceededException`、兜底 `Exception`
- 完整代码见 `templates.md` 第 3 节

## MyBatis-Plus 规范

- 启动类（ocr-web）`@MapperScan("com.paddleocr.web.mapper")`，mapper 接口定义在 ocr-dal 同名包下
- `MybatisPlusConfig` + `AuditMetaObjectHandler` 位于 ocr-dal 的 `com.paddleocr.web.mapper.config` 包
- 必须注册分页插件：`MybatisPlusInterceptor` + `PaginationInnerInterceptor(DbType.MYSQL)`；分页参数 `Page<T>`
- 主键 `@TableId(type = IdType.ASSIGN_ID)` 雪花 ID；**Long 型 id 必须加 `@JsonSerialize(using = ToStringSerializer.class)`**（防前端 JS 精度丢失）
- 审计字段 `createTime`/`updateTime` 用 `@TableField(fill = ...)` + `AuditMetaObjectHandler` 自动填充，禁止手动赋值
- 逻辑删除字段 `deleted` + `@TableLogic`；全局配置已定 `logic-delete-field: deleted`
- 查询优先 `LambdaQueryWrapper`；禁止 `apply`/`last` 拼接用户输入（SQL 注入）
- 简单 CRUD 走 `BaseMapper`/`IService`，多表 join、统计才写 XML

## 数据源与 application.yml 约定

- yml 统一放 `ocr-web/src/main/resources/`（其余模块不放配置文件）
- `type: com.alibaba.druid.pool.DruidDataSource`；参数：initial-size 5 / min-idle 5 / max-active 20 / max-wait 60000 / validation-query SELECT 1 / test-while-idle true
- Druid 监控页 `/druid` 必须设置 `login-username`/`login-password`，生产限制访问或关闭
- 密码等敏感项用 `${DB_PASSWORD}` 环境变量占位，**禁止明文入库入 git**
- 多环境：`application.yml`（公共）+ `application-dev.yml` / `application-prod.yml`，激活用 `spring.profiles.active`
- 自定义配置统一 `@ConfigurationProperties` 类型安全绑定（ocr-client 的 `config` 包），不用 `@Value`
- 上传限制：`max-file-size: 10MB` / `max-request-size: 12MB`
- SQL 日志：dev 用 `StdOutImpl`，prod 改 `org.apache.ibatis.logging.slf4j.Slf4jImpl`
- 完整模板见 `templates.md` 第 2 节

## 外部服务调用（ocr-client）

- **双 HttpClient Bean，独立连接池隔离**（各 20 总连接/每路由 10，连接超时 5s，keep-alive 30s）：
  - `ocrHttpClient`：OCR 推理专用，响应超时**请求级**按 `ocr.service.timeout-ms`（默认 600000，本地推理慢，勿随意调小）
  - `apiHttpClient`：通用 API 调用，响应超时**客户端级**按 `api.timeout-ms`（默认 30000）
  - 两池隔离目的：长耗时 OCR 请求不挤占通用 API 连接
- 配置前缀：`ocr.service`（`base-url` / `timeout-ms`）与 `api`（`timeout-ms`）；**接口路径不进 yml**，用 `OcrApi` 枚举常量（如 `RECOGNIZE("/ocr/recognize")`），与上游契约绑定
- `OcrClient` 失败/超时抛 `BusinessException(ResultCode.OCR_SERVICE_TIMEOUT / OCR_SERVICE_UNAVAILABLE / OCR_RECOGNIZE_FAILED)`；`ApiClient` 通用调用失败抛 `SYSTEM_ERROR`；均不把上游异常直接抛给前端
- 当前无自动重试；如需新增，仅针对超时与 5xx 且最多 1 次
- 图片字节流直接转发，不落盘；日志禁止打印图片字节数据

## 通用编码规范

**事务**：`@Transactional(rollbackFor = Exception.class)` 只加在 ocr-service 的 `XxxServiceImpl` 方法；**事务方法内禁止 HTTP 调用**（OCR 推理可达 10 分钟会长期占连接）——先调 OCR，成功后短事务落库。

**日志**：Lombok `@Slf4j` + `{}` 占位符；异常 `log.error("msg", e)` 保留堆栈；禁止 `System.out.println`、字符串拼接日志、打印敏感信息。

**线程池**：禁止 `Executors.newXxx`；用 `ThreadPoolExecutor` 显式参数 + 具名 ThreadFactory，或 `@Async` 配自定义 executor。

**时间**：Java 一律 `LocalDateTime`；Jackson 全局 `yyyy-MM-dd HH:mm:ss` + GMT+8（ocr-web 的 `JacksonConfig` 统一配置，LocalDateTime 需自定义 serializer，`spring.jackson.date-format` 对其无效）；MySQL 用 `datetime`。

**参数校验**：Controller 入参 `@Validated`；DTO 用 `@NotNull`/`@NotBlank`/`@Size`/`@Pattern`；上传文件校验扩展名白名单（jpg/png/bmp/webp）+ Content-Type + Hutool `FileTypeUtil` 魔数校验，快速失败。

**Lombok**：DO/DTO 用 `@Data`、日志 `@Slf4j`、`@Builder` 可用；`@AllArgsConstructor` 慎用（字段顺序耦合）。

**API 设计**：统一 `/api` 前缀、资源小写 kebab-case；GET 查询 / POST 创建或上传 / PUT 更新 / DELETE 删除；springdoc：类上 `@Tag`、方法上 `@Operation`。

**权限注解**：生成代码**不加** `@PreAuthorize` 等方法/类级安全注解（如 `@PreAuthorize("hasRole('ADMIN')")`）；权限控制统一由 `SecurityConfig` 的 URL 规则或拦截器实现。

**Mapping 路径**：方法级 `@GetMapping` / `@PostMapping` / `@PutMapping` / `@DeleteMapping` **必须显式写明路径**（如 `@PostMapping("/create")`、`@GetMapping("/tree")`）；禁止省略路径值（如 `@PostMapping` 后不带路径），避免接口直接挂在类级前缀上导致路径不明确。

**路径变量**：带 `{id}` 的接口必须先有一个英文动作名路径段，再接 `/{id}`（如 `@PutMapping("/update/{id}")`、`@DeleteMapping("/delete/{id}")`、`@GetMapping("/detail/{id}")`）；**禁止路径仅有 `/{id}`**（如 `@PutMapping("/{id}")`、`@DeleteMapping("/{id}")`）。

**依赖管理**：新依赖先加父 POM `dependencyManagement`（版本进 `<properties>`），再在目标模块声明（不带版本）；先确认 JDK 8 + Boot 2.7 兼容（重点排除 jakarta 命名空间的 3.x 系 starter）；测试用 parent 自带 `spring-boot-starter-test`（JUnit 5 + Mockito），父 POM 公共段已引入。

## 常见错误对照表

| 错误写法 | 后果 | 正确写法 |
|---|---|---|
| `var` / `List.of()` / `toList()` | 编译失败 | JDK 8 语法（见红线表） |
| `import jakarta.*` | ClassNotFoundException | `import javax.*` |
| 子模块依赖带 `<version>` | 版本漂移 | 父 POM `dependencyManagement` 收口 |
| ocr-dal 依赖 ocr-client（等倒置） | 循环依赖/架构腐化 | 保持 `web → service → {client → common, dal}` |
| 非 web 模块引 `spring-boot-starter-web` | 打包膨胀、分层破坏 | 仅 ocr-web 可用 web starter |
| 修改模块根包脱离 `com.paddleocr.web` | Bean 扫描不到 | 统一根包，扫描天然覆盖 |
| 所有外呼共用一个 HttpClient Bean | OCR 长耗时挤占通用连接 | `ocrHttpClient` / `apiHttpClient` 双 Bean 隔离 |
| 接口路径写进 yml | 配置与契约漂移 | `OcrApi` 枚举常量 |
| `mybatis-plus-spring-boot3-starter` | 启动失败 | `mybatis-plus-boot-starter:3.5.17` + `mybatis-plus-jsqlparser:3.5.17` |
| `druid-spring-boot-3-starter` | 启动失败 | `druid-spring-boot-starter:1.2.28` |
| springfox `@EnableSwagger2` | 与 Boot 2.7 冲突 | springdoc 1.7.x |
| Long id 直接序列化 | 前端精度丢失 | `ToStringSerializer` |
| 事务内调 OCR HTTP | 连接占用/超时 | 调用与事务分离 |
| `new ObjectMapper()` | 配置不一致 | 注入全局 ObjectMapper |
| `Executors.newFixedThreadPool` | OOM 风险 | `ThreadPoolExecutor` 显式参数 |
| yml 明文密码 | 泄露风险 | `${ENV_VAR}` 占位 |

## 完整模板

`templates.md`（本技能目录）：1) 父 POM + 5 个子模块 pom 2) application.yml + 多环境 3) ocr-common（ApiResponse/ResultCode/BusinessException）4) ocr-dal（MyBatis-Plus 分页、审计填充、实体示例 OcrRecordDO）5) ocr-client（双 HttpClient、OcrServiceProperties/ApiProperties、OcrApi、ApiClient）6) ocr-web（启动类、Jackson、CORS、GlobalExceptionHandler）+ ocr-service（PageResultVO）。生成代码时按模板复制，不要凭记忆重写。
