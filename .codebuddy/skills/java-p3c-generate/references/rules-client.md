# 生成外部调用层规则（ocr-client：HTTP 客户端 / 配置属性 / 外部 VO）

加载时机：生成 `ApiClient`、`OcrClient`、`OcrApi` 常量、HTTP 配置、外部服务出参 VO、`ocr-common` 的模型。

## 1. 模块与包

- 模块 `ocr-client`；包 `com.paddleocr.web.client`（`config` / `constant` / `model`）
- `ocr-common` 只放 `ApiResponse` / `ResultCode` / `BusinessException`，**零 Spring 依赖**
- `ocr-client` 不被 Controller 直接调用，只被 `ocr-service` 编排使用

## 2. HTTP 客户端

- **双 HttpClient Bean，连接池隔离**：
  - `ocrHttpClient`：OCR 推理专用，响应超时**请求级**，按 `ocr.service.timeout-ms`（默认 600000，本地推理慢，勿调小）
  - `apiHttpClient`：通用 API，响应超时**客户端级**，按 `api.timeout-ms`（默认 30000）
- 各池 20 总连接 / 每路由 10，连接超时 5s，keep-alive 30s
- 技术栈：HttpClient5 5.6.4 + httpcore5 5.4.3，JDK 8 写法，禁用 JDK 9+ 语法

## 3. 契约与配置

- 接口路径**不进 yml**，用 `OcrApi` 枚举常量（如 `RECOGNIZE("/ocr/recognize")`）
- 自定义配置用 `@ConfigurationProperties`（`ocr.service` / `api` 前缀），不用 `@Value`
- 外部服务出参用独立 VO（放 `model` 包），属性包装类型、不设默认值
- 当前无自动重试；如新增，仅针对超时与 5xx 且最多 1 次

## 4. 异常与日志

- `OcrClient` 失败/超时抛 `BusinessException(ResultCode.OCR_SERVICE_TIMEOUT / OCR_SERVICE_UNAVAILABLE / OCR_RECOGNIZE_FAILED)`
- `ApiClient` 通用调用失败抛 `SYSTEM_ERROR`
- **禁止把上游异常直接抛给前端**，必须转换（EX-04）
- 图片字节流直接转发、不落盘；**日志禁止打印图片字节、base64、完整 OCR 大文本**（LOG-07）
- `log.error("ocr call failed, url={}", url, e)` 保留堆栈

## 5. 资源与并发

- 响应/实体必须关闭，用 try-with-resources（JDK 7+，EX-06）
- 客户端 Bean 本身线程安全，禁止每次请求 new 一个连接池
- 正则（如校验 URL/文件名）预编译为 `static final Pattern`（OTH-01）

## 6. 自检

- 是否复用了既有双 HttpClient Bean，有没有新建 `Executors`
- 接口路径是否写进了 yml（应放 `OcrApi` 枚举）
- 异常是否转换为 `BusinessException(ResultCode)`
- 是否打印了图片字节或 base64
