# 生成 Web 层规则（ocr-web：Controller / 入参 DTO / 全局异常 / 上传 / 配置）

加载时机：生成 Controller、请求/响应 DTO、全局异常处理、拦截器、Jackson/CORS 配置、application yml。

## 1. 模块与分层红线

- 模块 `ocr-web`；包 `com.paddleocr.web.controller`（`.advice`）、`com.paddleocr.web.config`
- 启动类 `PaddleOcrWebApplication` 在此模块；`@MapperScan("com.paddleocr.web.mapper")` 加在启动类
- **ENG-01：Controller 禁止直接操作数据库 / Mapper**，必须经 Service
- Controller 不做业务逻辑、不做复杂校验以外的处理

## 2. 接口设计

- 统一 `/api` 前缀；资源路径小写 kebab-case；`GET` 查询 / `POST` 创建或上传 / `PUT` 更新 / `DELETE` 删除
- 返回一律 `ApiResponse<T>`，禁止自造返回结构
- springdoc：类上 `@Tag`，方法上 `@Operation`
- 入参加 `@Validated` / `@Valid`；DTO 用 `javax.validation` 注解（`@NotNull` / `@NotBlank` / `@Size` / `@Pattern`）
- Long id 出参必须已是字符串（`ToStringSerializer`），避免前端精度丢失

## 3. 入参 DTO

- 命名 `XxxRequest` / `XxxDTO`；属性**包装类型**、**不设默认值**（OOP-08/09）
- 布尔属性不加 `is` 前缀
- 超过 2 个查询参数封装为 `XxxQuery`，不用 `Map` 接收
- 分页参数 `pageNum` / `pageSize`，并对 `pageSize` 做上限保护（SEC-04）

## 4. 异常处理

- `GlobalExceptionHandler`（`@RestControllerAdvice`）在 `com.paddleocr.web.controller.advice`，必须覆盖：`BusinessException`、`MethodArgumentNotValidException`、`MaxUploadSizeExceededException`、兜底 `Exception`
- **Controller 内禁止手动 try-catch 拼错误响应**（EX-04 由 advice 统一转成用户可理解信息）
- Web 层不再向上抛异常；开放接口转成错误码 + 错误信息

## 5. 文件上传

- 扩展名白名单（jpg/png/bmp/webp）+ Content-Type + Hutool `FileTypeUtil` 魔数校验，快速失败
- `max-file-size: 10MB` / `max-request-size: 12MB`
- 图片字节不落盘，直接转发给 OCR 服务；日志禁打印字节与 base64

## 6. 配置与安全

- yml 只在 `ocr-web/src/main/resources/`；多环境 `application.yml` + `application-{dev,prod}.yml`
- 密码等敏感项用 `${DB_PASSWORD}` 占位，禁止明文入库
- Druid 监控页 `/druid` 必须设置账号密码，生产限制访问
- 自定义配置用 `@ConfigurationProperties` 类型安全绑定，不用 `@Value`
- Jackson 全局 `yyyy-MM-dd HH:mm:ss` + GMT+8（`JacksonConfig`）；时间类型 Java 侧用 `LocalDateTime`
- 越权校验、敏感数据脱敏按需实现（SEC-01/02）；上传等高成本接口做限流/防重放（SEC-07）

## 7. 自检

- Controller 是否直连 Mapper（ENG-01）
- 是否统一返回 `ApiResponse`、是否有 try-catch 拼响应
- 入参 DTO 是否有校验注解、是否用了基本类型或默认值
- 上传是否有白名单与魔数校验、大小限制是否生效
- yml 是否出现明文密码
