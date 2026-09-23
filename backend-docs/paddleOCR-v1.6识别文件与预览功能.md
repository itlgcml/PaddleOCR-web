# PaddleOCR v1.6 识别文件与预览功能 · 后端设计

> 适用工程：`backend/`（JDK 8 + Spring Boot 2.7.18 + Spring Security JWT + Apache HttpClient5，多模块 Maven）
> 编写依据：现有 `ocr-common` / `ocr-client` / `ocr-dal` / `ocr-service` / `ocr-web` 分层与命名约定；上游 OCR 服务契约经确认：`/ocr/recognize` 接收 multipart 文件，**图片与 PDF 均可直接识别**（多页结果由上游聚合返回）
> 目标：为 PaddleOCR-web 提供"上传图片/PDF → 调用 PaddleOCR-VL 1.6 引擎识别 → 直接返回识别 JSON"的文件识别与预览后端能力，**全程不落库、不存文件**

---

## 1. 现状分析

| 现状 | 说明 |
|---|---|
| `OcrClient` 空壳 | `ocr-client` 已预留 `@Component`（无字段无方法），multipart 转发能力待实现 |
| `ApiClient` 仅 JSON | 只支持 GET / POST JSON（走 `apiHttpClient`，响应超时 30s），**无 multipart 文件转发能力**，且 30s 超时远小于 OCR 推理时长，不可复用 |
| `ocrHttpClient` 缺响应超时 | 类注释声称"响应超时由 `ocr.service.timeout-ms` 决定"，但代码既未设客户端级也未设请求级 `RequestConfig.responseTimeout`（对比 `apiHttpClient` 已设客户端级）——**注释与实现不符，需在本期实现中于请求级补齐** |
| 模型与常量就绪 | `OcrResultVO`（fullText / textBoxes / costMs / **recordId**）、`TextBoxVO`（text / confidence / points 归一化四点坐标）、`OcrApi.RECOGNIZE("/ocr/recognize")` 均已定义；`recordId`（"识别记录 ID"）因本期不入库**无语义，需移除**；`OcrApi.RECOGNIZE` 注释"图片识别接口"需更新为"图片/PDF 识别接口" |
| 配置就绪 | `application.yml` 已有 `ocr.service.base-url: ${OCR_BASE_URL:http://ocr-service:8000}`、`ocr.service.timeout-ms: 600000`（10 分钟），`OcrServiceProperties` 类型安全绑定已就位 |
| 错误码零追加 | `ResultCode` 已含 `FILE_TYPE_NOT_ALLOWED(40010)` / `FILE_TOO_LARGE(40011)`（客户端段）与 `OCR_SERVICE_UNAVAILABLE(50301)` / `OCR_SERVICE_TIMEOUT(50302)` / `OCR_RECOGNIZE_FAILED(50303)`（OCR 段），**本期不需要新增任何错误码** |
| 全局异常零改动 | `GlobalExceptionHandler` 已覆盖 `MaxUploadSizeExceededException → 40011`、`BusinessException`、`MissingServletRequestParameterException`、`Exception` 兜底 |
| 上传限制就绪 | `spring.servlet.multipart.max-file-size: 10MB` / `max-request-size: 12MB` 已配置 |
| 鉴权零改动 | `SecurityConfig` 对 `/api/**` 默认 `authenticated()`，新接口天然受 JWT 保护；本项目规约不使用 `@PreAuthorize`，登录用户即可调用、不区分角色 |
| 前端待对接 | `frontend/src/views/OcrHome.vue` 已宣传"上传图片或 pdf 文件，基于 PaddleOCR-VL1.6 引擎提取内容"，功能名与本文档一致 |

**结论**：纯增量需求——零新增依赖、零数据层改动、零配置新增、零错误码追加；改动集中在 `ocr-client`（填充 `OcrClient` 实现、`OcrResultVO` 移除 `recordId`、`OcrApi` 注释更新），新增 `ocr-service` 编排 Service 与 `ocr-web` Controller 各一个。

### 1.1 上游服务契约（假设基线）

上游 OCR 服务（PaddleOCR-VL 1.6，`{ocr.service.base-url}`）契约经确认如下；**因参考文档为空，响应结构以现有 `OcrResultVO` / `TextBoxVO` 模型为假设基线**，若上游实际契约变化，仅需调整 `OcrClient` 单点（§7），不波及 service / web 层：

**请求**：

```
POST {base-url}/ocr/recognize
Content-Type: multipart/form-data

--boundary
Content-Disposition: form-data; name="file"; filename="原始文件名.含扩展名"
Content-Type: <按文件类型>
<文件二进制流>
--boundary--
```

- part 名固定 `file`；`filename` 透传原始文件名（上游依赖扩展名区分图片 / PDF）；
- 文件类型：`jpg / jpeg / png / bmp / webp / pdf`。

**响应**（HTTP 200，JSON）：

```json
{
  "fullText": "整份文件的全文文本（换行拼接）",
  "textBoxes": [
    { "text": "识别文本", "confidence": 0.98,
      "points": [ { "x": 0.10, "y": 0.12 }, { "x": 0.35, "y": 0.12 }, { "x": 0.35, "y": 0.20 }, { "x": 0.10, "y": 0.20 } ] }
  ],
  "costMs": 1234
}
```

| 假设点 | 约定 | 契约变化时的调整面 |
|---|---|---|
| 多页 PDF 聚合 | 上游将多页聚合为单结果（`fullText` 换行拼接、`textBoxes` 跨页合并） | 若上游按页分组（如 `pages[]`），仅改 `OcrClient` 解析逻辑，出参模型同步扩展 |
| 坐标归一化 | `points` 为 0-1 归一化四点坐标（左上起顺时针） | 若为像素坐标，`TextBoxVO` 注释与前端渲染同步调整 |
| 错误响应 | 非 200 视为失败，不解析上游错误体语义 | 统一映射 503xx（§6） |

---

## 2. 目标与范围

### 2.1 本期（V1）

1. **单文件上传识别接口** `POST /api/ocr/recognize`（`multipart/form-data`，参数 `file`），接收图片（jpg/jpeg/png/bmp/webp）或 PDF。
2. **JWT 登录即可调用**：遵循 `SecurityConfig` 既有规则（`/api/**` 默认 authenticated），不区分角色，白名单与安全配置零改动。
3. **文件三重校验，快速失败**：
   - 空文件（无内容）→ `40000 PARAM_ERROR`（"上传文件不能为空"）；
   - 扩展名 + ContentType + Hutool `FileTypeUtil` 魔数白名单校验 → `40010 FILE_TYPE_NOT_ALLOWED`；
   - 大小由 multipart `max-file-size: 10MB` 配置兜底 → `MaxUploadSizeExceededException` → `40011 FILE_TOO_LARGE`。
4. **流式转发**：`MultipartFile.getInputStream()` 直传上游（HttpClient5 multipart），**不落盘、不主动全量读入内存**（内存上限受 10MB 文件上限约束）；日志禁止打印文件字节数据。
5. **识别结果直接返回**：`ApiResponse<OcrResultVO>`（`fullText` / `textBoxes` / `costMs`），同步阻塞一次性返回，`recordId` 字段移除（不入库无语义）。
6. **上游异常统一转换**：连接失败/不可用 → `50301`；响应超时 → `50302`；上游非 200 / 响应解析失败 → `50303`；均不把上游异常直接抛给前端。
7. **超时修正**：`OcrClient` 每请求设置 `RequestConfig.responseTimeout = ocr.service.timeout-ms`（默认 600000ms），补齐 `ocrHttpClient` 缺失的响应超时。

### 2.2 明确不做（避免过度设计）

| 项 | 决策 | 理由 |
|---|---|---|
| 识别记录入库（`ocr_record` 表） | 不做 | 需求明确"直接返回 json，不需要存入数据库"；识别历史/检索/管理端留 V2（届时启用 `ocr-dal`，且遵循"先调 OCR、成功后短事务落库"红线） |
| 异步任务 + 轮询 | 不做 | 同步阻塞直返满足当前交互（前端上传后等待结果）；OCR 推理上限 10 分钟由响应超时兜底；异步任务表/Redis 状态机留 V2 |
| 批量文件上传 | 不做 | 单文件单接口；批量 = 前端逐个调用 |
| PDF 拆页（引入 PDFBox） | 不做 | 上游直接支持 PDF 多页识别（已确认），Java 端纯转发，避免引入重依赖与渲染开销 |
| 文件落盘 / 对象存储 | 不做 | 流式转发零临时文件（Servlet 容器层面的 multipart 临时文件由框架自动管理，不属于业务落盘） |
| 自动重试 | 不做 | 沿用项目现状"无自动重试"；OCR 推理昂贵，盲目重试放大上游压力 |
| 接口限流 / 配额 | 不做 | V2 结合已就绪的 Redis 做登录用户级限流 |
| 方法级权限注解 | 不做 | 项目规约：权限控制统一由 `SecurityConfig` URL 规则实现，生成代码不加 `@PreAuthorize` |

---

## 3. 技术选型

| 能力 | 选型 | 版本与放置 | 说明 |
|---|---|---|---|
| 文件接收 | Spring MVC `MultipartFile` | `spring-boot-starter-web`（`ocr-web` 已有） | multipart 限制 10MB/12MB 已配置，零新增 |
| 文件校验 | 扩展名白名单 + ContentType + Hutool `FileTypeUtil` 魔数 | `hutool-all`（`ocr-client` 已依赖） | 三重校验快速失败，防改扩展名绕过；白名单 `jpg/jpeg/png/bmp/webp/pdf`（pdf 魔数 `%PDF-`） |
| 上游转发 | **HttpClient5 multipart 流式转发** | `httpclient5` 5.6.4（`ocr-client` 已依赖，5.x 已内置 `entity.mime` 包，无需额外 httpmime） | `MultipartEntityBuilder` + `InputStreamBody`（part 名 `file`，透传原始文件名），流式不落盘；走 `ocrHttpClient` 专用连接池（20 总连接/每路由 10，连接 5s，keep-alive 30s），与通用 `apiHttpClient` 隔离 |
| 响应超时 | **请求级 `RequestConfig.responseTimeout`** | `OcrClient` 内 per-request 设置 | 修正 `ocrHttpClient` 未设响应超时的缺口；值取 `ocr.service.timeout-ms`（默认 600000ms）；不走 `apiHttpClient`（客户端级 30s，会误杀长推理） |
| 响应解析 | Hutool `JSONUtil` | `hutool-all`（`ocr-client` 已依赖） | `ocr-client` 为非 web 模块（无 jackson-databind 传递依赖），hutool 已在依赖内，零新增 |
| 结果模型 | 既有 `OcrResultVO` / `TextBoxVO` | `ocr-client` 的 `model` 包 | 仅移除 `recordId`（不入库无语义），其余字段与上游响应假设一一对应（§1.1） |
| 统一响应 / 异常 | `ApiResponse<T>` + `BusinessException` | `ocr-common` 已有 | 错误码零追加、`GlobalExceptionHandler` 零改动 |
| 鉴权 | `SecurityConfig` 既有 URL 规则 | `ocr-web` 已有 | `/api/**` 默认 authenticated，新接口自动纳管，白名单不动 |
| 事务 | 无 `@Transactional` | —— | 无 DB 操作；即使 V2 落库也遵循"事务方法内禁止 HTTP 调用"红线（OCR 推理可达 10 分钟） |

**零新增依赖**：以上全部能力来自既有依赖（spring-boot-starter-web / httpclient5 / hutool-all），父 POM 与各子模块 pom 均不需要改动。

---

## 4. 数据模型设计

**本期无数据模型**：识别结果直接透传调用方，不入库、不存文件——无新表、无 DO、无 Mapper，`ocr-dal` 模块不参与本期开发（模块依赖关系不动）。

### 4.1 出参模型（复用既有 `OcrResultVO` / `TextBoxVO`，微调一处）

| 类 | 字段 | 类型 | 变更 | 说明 |
|---|---|---|---|---|
| `OcrResultVO` | `fullText` | String | 保留 | 全量文本（换行拼接），透传上游 |
| | `textBoxes` | List\<TextBoxVO\> | 保留 | 文本检测框列表，透传上游 |
| | `costMs` | Long | 保留 | 识别耗时（ms），透传上游 |
| | `recordId` | String | **移除** | 原注释"识别记录 ID（字符串雪花 ID）"——其生成方是未来的落库逻辑；本期不入库，字段将永远为 `null`，属无语义字段，移除以免误导前端对接 |
| `TextBoxVO` | `text` / `confidence` / `points` | — | 零改动 | 识别文本 / 置信度 0-1 / 四点坐标（归一化 0-1） |
| `TextBoxVO.PointVO` | `x` / `y` | Double | 零改动 | 归一化坐标 |

`OcrResultVO` 变更前后：

```java
// 变更前
@Schema(description = "识别耗时（ms）")
private Long costMs;

@Schema(description = "识别记录 ID（字符串雪花 ID）")
private String recordId;

// 变更后
@Schema(description = "识别耗时（ms）")
private Long costMs;
```

**影响面**：`recordId` 当前无任何生产方与消费方（全库无引用），前端尚未对接，移除零破坏；V2 落库时随识别历史接口重新引入（§4.2）。

### 4.2 V2 预留：识别记录表 `ocr_record`（仅预留，不建表）

识别历史 / 检索 / 管理端启用时再启 `ocr-dal`，字段要点提示（届时遵循"**先调 OCR、成功后短事务落库**"红线，禁止把 HTTP 调用包进事务）：

| 字段（暂定） | 说明 |
|---|---|
| `id` | 雪花 ID（`assign_id`），出参 `recordId` 的来源 |
| `user_id` | 归属用户（识别历史按用户隔离，防水平越权） |
| `file_name` / `file_size` / `file_type` | 文件元信息（不存文件本体） |
| `full_text` | 全量文本（检索用，可加全文索引） |
| `result_snapshot` | 识别结果 JSON 快照（textBoxes 等，LONGTEXT） |
| `cost_ms` / `status` | 耗时与状态（成功 / 失败 / 超时） |
| `create_time` | 创建时间 |

---

## 5. 分层落地（严格遵循现有模块依赖）

模块依赖方向：`ocr-web → ocr-service → ocr-client → ocr-common`（`ocr-dal` 本期不参与）。

### 5.1 模块改动总览

| 模块 | 文件 | 动作 | 内容 |
|---|---|---|---|
| `ocr-common` | — | **零改动** | 错误码（40010 / 40011 / 50301~50303）、`ApiResponse`、`BusinessException`、全局异常处理均已就绪（§1） |
| `ocr-client` | `OcrClient.java` | 修改（填充空壳） | 新增 `recognize(MultipartFile)`：multipart 流式转发 + 请求级超时 + 异常转换 + 响应解析（§7.1） |
| | `OcrResultVO.java` | 修改 | 移除 `recordId` 字段（§4.1） |
| | `OcrApi.java` | 修改 | `RECOGNIZE` 注释"图片识别接口"→"图片/PDF 识别接口"（§7.4） |
| `ocr-service` | `service/OcrRecognizeService.java` | **新增** | 识别编排接口：文件校验 + 委托 `OcrClient`（§7.2） |
| | `service/impl/OcrRecognizeServiceImpl.java` | **新增** | 三重文件校验 + 编排，无 `@Transactional` |
| `ocr-web` | `controller/OcrController.java` | **新增** | `POST /api/ocr/recognize`：参数接收 + `ApiResponse` 包装（§7.3） |

净变更：**新增 3 个文件、修改 3 个文件、零删除、零依赖变更、零配置变更**。组件注册零配置——三个新类均在主应用包扫描范围内（`com.paddleocr.web.**`）。

### 5.2 调用链路

```
前端（axios，multipart/form-data + Bearer token）
  │
  ▼
JwtAuthenticationFilter ─── Security 过滤链（既有，零改动；未认证 → HTTP 401 + 40100）
  │
  ▼
OcrController.recognize(file)
  │    @RequestParam("file") MultipartFile
  │    缺 file part → MissingServletRequestParameterException
  │                 → 既有 Handler → 40000 "缺少参数: file"（零新增 Handler 的关键设计，§7.3）
  ▼
OcrRecognizeServiceImpl.recognize(file)
  ├─ validateFile(file)      空内容 → 40000；扩展名 / ContentType / 魔数任一不过 → 40010
  └─ ocrClient.recognize(file)
       │
       ▼
     上游 {ocr.service.base-url}/ocr/recognize（multipart 流式直传，请求级响应超时 600s）
       │  连不上 → 50301 │ 响应超时 → 50302 │ 非 200 / 解析失败 → 50303
  │
  ▼
ApiResponse.ok(OcrResultVO)   HTTP 200 + code=0
```

### 5.3 职责边界

| 层 | 职责 | 明确不做 |
|---|---|---|
| `OcrController` | 参数接收、响应包装、Swagger 元数据 | 不做校验、不碰 HTTP 客户端 |
| `OcrRecognizeServiceImpl` | 文件三重校验、编排、结果直传 | 不做协议细节；**不加 `@Transactional`**（无 DB；OCR 推理最长 10 分钟，"事务方法内禁止 HTTP 调用"是项目红线） |
| `OcrClient` | multipart 协议转发、请求级超时、异常转换（503xx）、JSON → VO 解析 | 不做业务校验（不感知白名单规则）——校验属编排层职责，client 只对"怎么发"负责 |

---

## 6. 错误码设计

**零追加**：本期所需错误码已全部存在于 `ResultCode`（分段规则见其类注释：`0 成功；4xxxx 客户端；5xxxx 系统；503xx OCR 服务段`）。

### 6.1 全量触发表

| 错误码 | 枚举 | message | 触发场景 | 抛出 / 处理位置 | HTTP 状态 |
|---|---|---|---|---|---|
| 0 | `SUCCESS` | 成功 | 识别成功 | `OcrController` | 200 |
| 40000 | `PARAM_ERROR` | 缺少参数: file | multipart 缺 `file` part | `GlobalExceptionHandler` 既有 `MissingServletRequestParameterException` Handler | 200 |
| 40000 | `PARAM_ERROR` | 上传文件不能为空 | `file` 存在但内容为空（`file.isEmpty()`） | `OcrRecognizeServiceImpl.validateFile` | 200 |
| 40010 | `FILE_TYPE_NOT_ALLOWED` | 不支持的文件类型 | 扩展名 / ContentType / 魔数任一不过白名单 | `OcrRecognizeServiceImpl.validateFile` | 200 |
| 40011 | `FILE_TOO_LARGE` | 文件大小超出限制 | 超过 multipart `max-file-size: 10MB`，抛 `MaxUploadSizeExceededException` | `GlobalExceptionHandler` 既有 Handler | 200 |
| 40100 | `UNAUTHORIZED` | 未登录或令牌缺失 | 未带 / 无效 token 访问 `/api/**` | Security 过滤链 `RestAuthenticationEntryPoint`（既有） | 401 |
| 50301 | `OCR_SERVICE_UNAVAILABLE` | OCR 识别服务不可用 | 上游 TCP 连接被拒 / 连接建立超时（`ConnectException` / `ConnectTimeoutException`） | `OcrClient` | 200 |
| 50302 | `OCR_SERVICE_TIMEOUT` | OCR 识别超时 | 请求级响应超时触发（`HttpTimeoutException` / `SocketTimeoutException`，600s） | `OcrClient` | 200 |
| 50303 | `OCR_RECOGNIZE_FAILED` | OCR 上游返回 \<状态码\> 等（双参 detail 直接作为 message） | 上游返回非 200；响应体 JSON 解析失败；其余 IO 异常（连接中途断开等） | `OcrClient` | 200 |
| 50000 | `SYSTEM_ERROR` | 系统内部错误 | 未预期异常兜底 | `GlobalExceptionHandler` 既有 Handler | 200 |

### 6.2 HTTP 状态码策略（沿用既有约定）

- **业务失败统一 HTTP 200 + 业务码**：`BusinessException` 由 `handleBusiness` 处理，返回 `ApiResponse.fail(code, msg)`（方法无 `@ResponseStatus`，默认 200）——与登录 / 注册等既有接口行为一致，前端以 `code !== 0` 判定失败；
- **仅鉴权失败走真实 HTTP 状态码**：未认证 → 401（EntryPoint），无权限 → 403（AccessDeniedHandler），两者是独立于 `@RestControllerAdvice` 的过滤链通路；
- **补充信息模式**：上游失败用双参构造携带上下文，如 `new BusinessException(ResultCode.OCR_RECOGNIZE_FAILED, "OCR 上游返回 502")`——注意双参构造的 detail **直接作为 message**（`super(detail)`，不与枚举 message 拼接），`ApiClient` 已有同款先例；message 面向用户可读，细节进日志。

---

## 7. 核心类设计

### 7.0 变更一览

| 类 | 模块 | 动作 | 关键点 |
|---|---|---|---|
| `OcrClient` | ocr-client | 填充空壳 | multipart 流式转发、**请求级超时修正**、异常转换 |
| `OcrRecognizeService` / `Impl` | ocr-service | 新增 | 三重文件校验 + 编排，无事务注解 |
| `OcrController` | ocr-web | 新增 | 显式路径、`@RequestParam` 绑定（缺 part 复用既有 Handler）、`ApiResponse` 包装 |
| `OcrResultVO` | ocr-client | 修改 | **移除 `recordId`**（§4.1） |
| `OcrApi` | ocr-client | 修改 | 注释更新（§7.4） |

### 7.1 `OcrClient`（ocr-client · 填充空壳）

```java
package com.paddleocr.web.client;

import cn.hutool.json.JSONUtil;
import com.paddleocr.web.client.config.OcrServiceProperties;
import com.paddleocr.web.client.constant.OcrApi;
import com.paddleocr.web.client.model.OcrResultVO;
import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.mime.InputStreamBody;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.HttpTimeoutException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * OCR 识别客户端：multipart 流式转发至上游识别服务（图片/PDF）
 *
 * <p>走专用 ocrHttpClient 连接池（总 20 / 每路由 10，连接 5s，keep-alive 30s）；
 * 响应超时按请求级设置（ocr.service.timeout-ms，默认 600 秒），补齐池级未配置
 * responseTimeout 的缺口；上游异常统一转换为 503xx 业务码，不向 service/web 层泄漏协议细节；
 * 日志禁止打印文件字节与响应原文</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrClient {

    private final CloseableHttpClient ocrHttpClient;

    private final OcrServiceProperties ocrServiceProperties;

    /**
     * 文件识别（图片/PDF）：MultipartFile 输入流直传上游，不落盘、不全量驻内存
     */
    public OcrResultVO recognize(MultipartFile file) {
        String url = ocrServiceProperties.getBaseUrl() + OcrApi.RECOGNIZE.getPath();
        HttpPost post = new HttpPost(url);
        // 请求级响应超时（见下方取舍表：不改共享 Bean，按接口独立演进）
        post.setConfig(RequestConfig.custom()
                .setResponseTimeout(Timeout.ofMilliseconds(ocrServiceProperties.getTimeoutMs()))
                .build());
        // multipart 流式：part 名固定 file，透传原始文件名（上游依赖扩展名区分图片/PDF）
        post.setEntity(MultipartEntityBuilder.create()
                .addPart("file", new InputStreamBody(openStream(file),
                        resolveContentType(file), file.getOriginalFilename()))
                .build());

        HttpClientResponseHandler<OcrResultVO> handler = (ClassicHttpResponse response) -> {
            int code = response.getCode();
            HttpEntity entity = response.getEntity();
            String body = entity == null ? "" : EntityUtils.toString(entity, StandardCharsets.UTF_8);
            if (code != 200) {
                log.warn("OCR 上游返回异常状态, status={}, url={}", code, url);
                // 上游错误体不透传（可能含内部信息），仅带状态码
                throw new BusinessException(ResultCode.OCR_RECOGNIZE_FAILED, "OCR 上游返回 " + code);
            }
            return parseResult(body);
        };
        try {
            return ocrHttpClient.execute(post, handler);
        } catch (ConnectException e) {
            log.error("OCR 服务连接失败, url={}", url, e);
            throw new BusinessException(ResultCode.OCR_SERVICE_UNAVAILABLE);
        } catch (HttpTimeoutException | SocketTimeoutException e) {
            // classic 5.x：responseTimeout 触发 HttpTimeoutException；底层 socket 读超时为 SocketTimeoutException，均归超时
            log.error("OCR 服务响应超时, url={}, timeoutMs={}", url, ocrServiceProperties.getTimeoutMs());
            throw new BusinessException(ResultCode.OCR_SERVICE_TIMEOUT);
        } catch (IOException e) {
            log.error("OCR 服务调用异常, url={}", url, e);
            throw new BusinessException(ResultCode.OCR_RECOGNIZE_FAILED, "OCR 服务 IO 异常");
        }
    }

    /**
     * 解析上游响应：JSON → OcrResultVO（结构假设见 §1.1，契约变化仅改此单点）
     */
    private OcrResultVO parseResult(String body) {
        try {
            return JSONUtil.toBean(body, OcrResultVO.class);
        } catch (Exception e) {
            // 日志红线：不打响应原文（可能为大文本），仅记长度
            log.error("OCR 响应解析失败, bodyLength={}", body == null ? 0 : body.length(), e);
            throw new BusinessException(ResultCode.OCR_RECOGNIZE_FAILED, "OCR 响应解析失败");
        }
    }

    /**
     * 打开上传文件输入流（MultipartFile 流由容器管理生命周期，无需手动 close）
     */
    private InputStream openStream(MultipartFile file) {
        try {
            return file.getInputStream();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.OCR_RECOGNIZE_FAILED, "读取上传文件失败");
        }
    }

    /**
     * 透传文件 ContentType（service 层已校验非空且在白名单内）
     */
    private ContentType resolveContentType(MultipartFile file) {
        return ContentType.parse(file.getContentType());
    }
}
```

**设计要点与取舍**：

| 要点 | 说明 |
|---|---|
| **超时修正（请求级）** | `ocrHttpClient` 未设 `responseTimeout`（§1 缺口）。选请求级 `post.setConfig` 而非改共享 Bean 客户端级默认：池是共享资源，后续若新增短超时 OCR 接口（如健康探测）不会被 600s 默认值绑架；与 `apiHttpClient` 的"客户端级 30s"策略不冲突——通用池保短默认，专用池按接口覆写 |
| **流式转发** | `InputStreamBody` 包装 `MultipartFile.getInputStream()` 直传，边读边发；业务层不落盘、不全量读入字节数组（内存峰值受 10MB 文件上限约束）。Servlet 容器层面的 multipart 临时文件由框架自动管理，不属业务落盘 |
| **异常三分法** | `ConnectException`（TCP 拒绝）→ 50301；`HttpTimeoutException` / `SocketTimeoutException`（responseTimeout 触发或底层读超时）→ 50302；其余 `IOException`（连接中途断开等）→ 50303。三类互不重叠、catch 顺序无继承冲突 |
| **中文文件名** | multipart part 的 filename 透传原始文件名；若上游（Python 侧）解析中文文件名异常，回退方案：`MultipartEntityBuilder.setCharset(StandardCharsets.UTF_8)` 或 `HttpMultipartMode.EXTENDED`（上线联调时验证） |
| **资源管理** | 响应体在 handler 内经 `EntityUtils.toString` 消费完毕（连接可归还池）；请求体流由 HttpClient 写出时消费，`MultipartFile` 流无需手动关闭 |
| **JSON 解析** | `ocr-client` 为非 web 模块（无 jackson-databind 传递依赖），用已在依赖内的 hutool `JSONUtil`；嵌套 `TextBoxVO.PointVO` 静态内部类可正常反序列化 |

### 7.2 `OcrRecognizeService`（ocr-service · 新增接口 + 实现）

```java
package com.paddleocr.web.service;

import com.paddleocr.web.client.model.OcrResultVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * OCR 识别编排：文件校验 + 委托上游识别
 *
 * @author paddleocr
 * @date 2026-09-23
 */
public interface OcrRecognizeService {

    /**
     * 识别单文件（图片/PDF）：校验通过后流式转发上游，结果直返不落库
     *
     * @param file 上传文件（jpg/jpeg/png/bmp/webp/pdf，非空）
     * @return 识别结果（全文 + 文本框 + 耗时）
     */
    OcrResultVO recognize(MultipartFile file);
}
```

```java
package com.paddleocr.web.service.impl;

import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.util.StrUtil;
import com.paddleocr.web.client.OcrClient;
import com.paddleocr.web.client.model.OcrResultVO;
import com.paddleocr.web.common.BusinessException;
import com.paddleocr.web.common.ResultCode;
import com.paddleocr.web.service.OcrRecognizeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * OCR 识别编排实现：三重文件校验 + 委托 OcrClient
 *
 * <p>无 DB 操作，不加 @Transactional（事务方法内禁止 HTTP 调用，OCR 推理最长 10 分钟）</p>
 *
 * @author paddleocr
 * @date 2026-09-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrRecognizeServiceImpl implements OcrRecognizeService {

    /** 扩展名白名单（小写） */
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "bmp", "webp", "pdf");

    /** ContentType 白名单 */
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/bmp", "image/webp", "application/pdf");

    /** 扩展名 → FileTypeUtil 魔数识别名映射（webp 为 RIFF 容器特例，走手工前缀校验） */
    private static final Map<String, String> EXTENSION_MAGIC_TYPE =
            Map.of("jpg", "jpg", "jpeg", "jpg", "png", "png", "bmp", "bmp", "pdf", "pdf");

    private final OcrClient ocrClient;

    @Override
    public OcrResultVO recognize(MultipartFile file) {
        validateFile(file);
        long start = System.currentTimeMillis();
        OcrResultVO result = ocrClient.recognize(file);
        log.info("文件识别完成, fileName={}, fileSize={}, totalCostMs={}, upstreamCostMs={}",
                file.getOriginalFilename(), file.getSize(),
                System.currentTimeMillis() - start, result.getCostMs());
        return result;
    }

    /**
     * 三重校验，快速失败：空内容 → 40000；扩展名 / ContentType / 魔数任一不过 → 40010
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "上传文件不能为空");
        }
        String filename = file.getOriginalFilename();
        if (StrUtil.isBlank(filename)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED);
        }
        String extension = StrUtil.subAfter(filename, '.', true).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED);
        }
        checkMagicNumber(file, extension);
    }

    /**
     * 魔数校验：防"改扩展名伪装"绕过（如 exe 改名 pdf）
     */
    private void checkMagicNumber(MultipartFile file, String extension) {
        // webp 特例：RIFF 容器（头 4 字节 RIFF + 第 9-12 字节 WEBP），FileTypeUtil 不内置识别
        if ("webp".equals(extension)) {
            checkWebpMagic(file);
            return;
        }
        String magicType;
        try (InputStream in = file.getInputStream()) {
            magicType = FileTypeUtil.getType(in, file.getOriginalFilename());
        } catch (IOException e) {
            throw new BusinessException(ResultCode.OCR_RECOGNIZE_FAILED, "读取上传文件失败");
        }
        if (!EXTENSION_MAGIC_TYPE.getOrDefault(extension, "").equals(magicType)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED);
        }
    }

    /**
     * webp 魔数手工前缀校验：RIFF????WEBP
     */
    private void checkWebpMagic(MultipartFile file) {
        byte[] head = new byte[12];
        try (InputStream in = file.getInputStream()) {
            if (in.read(head) < 12
                    || head[0] != 'R' || head[1] != 'I' || head[2] != 'F' || head[3] != 'F'
                    || head[8] != 'W' || head[9] != 'E' || head[10] != 'B' || head[11] != 'P') {
                throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED);
            }
        } catch (IOException e) {
            throw new BusinessException(ResultCode.OCR_RECOGNIZE_FAILED, "读取上传文件失败");
        }
    }
}
```

> JDK 8 注意：`Set.of` / `Map.of` 为 JDK 9+ API，落地时改用 `new HashSet<>(Arrays.asList(...))` / `new HashMap<>()` 静态初始化——本文档按"意图等价"示意，实现时遵循项目 JDK 8 基线。

**白名单三重对照表**：

| 扩展名 | ContentType | 魔数（头字节） | 识别方式 |
|---|---|---|---|
| jpg / jpeg | image/jpeg | `FF D8 FF` | `FileTypeUtil` → `jpg` |
| png | image/png | `89 50 4E 47` | `FileTypeUtil` → `png` |
| bmp | image/bmp | `42 4D` | `FileTypeUtil` → `bmp` |
| webp | image/webp | `52 49 46 46 .. .. .. .. 57 45 42 50`（`RIFF????WEBP`） | 手工前缀校验（`FileTypeUtil` 不内置） |
| pdf | application/pdf | `25 50 44 46`（`%PDF`） | `FileTypeUtil` → `pdf` |

### 7.3 `OcrController`（ocr-web · 新增）

```java
package com.paddleocr.web.controller;

import com.paddleocr.web.client.model.OcrResultVO;
import com.paddleocr.web.common.ApiResponse;
import com.paddleocr.web.service.OcrRecognizeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * OCR 识别域接口：文件上传识别（图片/PDF）
 *
 * <p>鉴权由 SecurityConfig 既有规则统一纳管（/api/** 默认 authenticated），
 * 登录用户即可调用、不区分角色；本类不加方法级权限注解（项目规约：权限控制统一由 URL 规则实现）</p>
 *
 * @author paddleocr
 * @date 2026-09-23
 */
@Tag(name = "OCR 识别")
@RestController
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
public class OcrController {

    private final OcrRecognizeService ocrRecognizeService;

    /**
     * 文件识别（鉴权）：multipart 流式转发上游，结果直返不落库
     */
    @Operation(summary = "文件识别（图片/PDF）")
    @PostMapping(value = "/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<OcrResultVO> recognize(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(ocrRecognizeService.recognize(file));
    }
}
```

**参数绑定取舍**（缺 `file` part 时的行为是关键差异）：

| 绑定方式 | 评估 |
|---|---|
| `@RequestParam("file")`（**选定**） | part 缺失时 Spring 抛 `MissingServletRequestParameterException` → **既有 Handler 精确转 40000 "缺少参数: file"**，零新增 Handler，与"全局异常零改动"结论闭环 |
| `@RequestPart("file")`（默认 required） | part 缺失抛 `MissingServletRequestPartException`，既有 Handler 未覆盖 → 落入 `Exception` 兜底变 50000，语义降级 |
| `@RequestParam(required = false)` + null 判断 | 可行但多余——让既有 Handler 干活更简洁 |

其他规约对齐：方法级注解显式写路径 `"/recognize"`（项目规约：禁止类级通配前缀）；`consumes` 显式声明 multipart，swagger-ui 正确渲染文件上传控件。

### 7.4 `OcrApi` 注释更新（ocr-client）

```java
// 变更前
/** 图片识别接口 */
RECOGNIZE("/ocr/recognize");

// 变更后
/** 图片/PDF 识别接口（多页 PDF 由上游聚合返回单结果） */
RECOGNIZE("/ocr/recognize");
```

路径与上游契约绑定不变（§1.1），仅语义注释扩展。`OcrResultVO` 变更见 §4.1。

---

## 8. 安全设计要点

| 要点 | 说明 |
|---|---|
| **鉴权** | `/api/ocr/recognize` 天然落在 `SecurityConfig` 的 `/api/**` 默认 `authenticated()` 规则内（零改动）；JWT 过滤链校验 accessToken，未认证 → HTTP 401 + 40100（`RestAuthenticationEntryPoint` 既有行为）。登录即可调用、不区分角色（角色区分仅存在于系统设置域接口的 `hasRole("ADMIN")` URL 规则，本接口不涉及，与既有"登录即可用"接口一致） |
| **越权面** | 本期无资源归属——不入库、无 `recordId`、无"按 ID 查询记录"类接口，**不存在水平 / 垂直越权攻击面**；文件仅在单次请求生命周期内流转，请求结束即释放 |
| **文件校验防绕过** | 三重校验缺一不可：仅校验扩展名 / ContentType 可被"可执行文件改名 `.pdf`"绕过，魔数校验（`FileTypeUtil` + webp 的 `RIFF????WEBP` 手工前缀）兜底拦截（§7.2）；校验在调上游**之前**，恶意文件不消耗上游推理资源 |
| **大小兜底** | 10MB 由 multipart 配置在 Servlet 容器层拦截（`MaxUploadSizeExceededException` → 40011），请求未进业务层即被拒绝，防止超大文件耗尽内存与上游带宽 |
| **日志红线** | 禁止打印文件字节数据与识别结果原文（可能含用户敏感文本）：成功日志仅记文件名 / 大小 / 耗时；失败日志记状态码 / 异常栈 / 响应体**长度**，不记内容（§7.1 已落地） |
| **SSRF 面** | 上游 URL 仅来自服务端配置 `ocr.service.base-url`（环境变量注入），**接口不接收任何客户端传入的 URL / 地址类参数**——无 SSRF 攻击面 |
| **Swagger 声明** | `consumes = MULTIPART_FORM_DATA_VALUE` 使 swagger-ui（springdoc 1.8.0）正确渲染文件上传控件；出参经 `@Schema` 全量文档化（§9）；面板路径（`/swagger-ui`、`/v3/api-docs`）非 `/api` 前缀，经 `anyRequest().permitAll()` 放行，`OpenApiConfig` 已配 Bearer 安全校验，可在面板内直接调试鉴权接口 |

---

## 9. API 设计（接口出入参）

### 9.1 接口定义

| 项 | 值 |
|---|---|
| 路径 | `POST /api/ocr/recognize` |
| 请求类型 | `multipart/form-data` |
| 鉴权 | `Authorization: Bearer <accessToken>`（JWT，登录用户即可） |
| 入参 | `file`：MultipartFile，必填，`jpg/jpeg/png/bmp/webp/pdf`，≤ 10MB |
| 出参 | `ApiResponse<OcrResultVO>`（HTTP 200，以 `code` 判定成败，§6.2） |

**入参表**：

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `file` | form-data（file part） | binary | 是 | 缺失 → 40000"缺少参数: file"；空内容 → 40000"上传文件不能为空"；类型不符 → 40010；超 10MB → 40011 |

**出参表**（`ApiResponse` 包装 → `OcrResultVO` → `TextBoxVO` → `PointVO`）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | int | 0=成功，非 0=失败（§6.1 全量表） |
| `message` | String | 提示信息 |
| `data` | OcrResultVO | 业务数据（失败时为 null） |
| `timestamp` | long | 服务端时间戳（ms） |
| `data.fullText` | String | 全量文本（换行拼接，多页 PDF 由上游聚合） |
| `data.textBoxes` | List\<TextBoxVO\> | 文本检测框列表 |
| `data.textBoxes[].text` | String | 识别文本 |
| `data.textBoxes[].confidence` | Double | 置信度 0-1 |
| `data.textBoxes[].points` | List\<PointVO\> | 四点坐标（左上起顺时针） |
| `data.textBoxes[].points[].x` / `.y` | Double | **归一化坐标 0-1**（前端按渲染尺寸乘回像素） |
| `data.costMs` | Long | 上游识别耗时（ms） |

### 9.2 成功响应示例（多页 PDF，节选两个文本框）

```json
{
  "code": 0,
  "message": "成功",
  "data": {
    "fullText": "技术服务合同\n合同编号：HT-2026-0917",
    "textBoxes": [
      {
        "text": "技术服务合同",
        "confidence": 0.98,
        "points": [
          { "x": 0.10, "y": 0.05 }, { "x": 0.42, "y": 0.05 },
          { "x": 0.42, "y": 0.11 }, { "x": 0.10, "y": 0.11 }
        ]
      },
      {
        "text": "合同编号：HT-2026-0917",
        "confidence": 0.95,
        "points": [
          { "x": 0.12, "y": 0.20 }, { "x": 0.58, "y": 0.20 },
          { "x": 0.58, "y": 0.26 }, { "x": 0.12, "y": 0.26 }
        ]
      }
    ],
    "costMs": 8642
  },
  "timestamp": 1790123456789
}
```

### 9.3 失败响应示例（`ApiResponse.fail` 通路，结构一致）

```json
// 40010：伪装文件被魔数拦截（HTTP 200）
{ "code": 40010, "message": "不支持的文件类型", "data": null, "timestamp": 1790123460123 }

// 40000：缺 file part（HTTP 200）
{ "code": 40000, "message": "缺少参数: file", "data": null, "timestamp": 1790123461245 }

// 50303：上游识别失败（HTTP 200；双参构造 detail 直接作为 message）
{ "code": 50303, "message": "OCR 上游返回 500", "data": null, "timestamp": 1790123470388 }
```

未认证（`HTTP 401`，走过滤链 `RestAuthenticationEntryPoint` 而非 `@RestControllerAdvice` 通路；EntryPoint 经全局 `ObjectMapper` 写出 `ApiResponse.fail`，为与业务失败同构的完整四字段体）：

```json
{ "code": 40100, "message": "未登录或令牌缺失", "data": null, "timestamp": 1790123459999 }
```

令牌无效 / 过期等场景同样由过滤链走该通路输出，与登录失效行为一致。

### 9.4 cURL 调试示例（单行，跨 shell 可用）

```bash
curl -X POST "http://localhost:8080/api/ocr/recognize" -H "Authorization: Bearer <accessToken>" -F "file=@D:/docs/contract.pdf"
```

---

## 10. 配置项

**零新增配置**：全部复用既有键，`application.yml` 不改一行。

### 10.1 复用配置一览

| 配置键 | 现值 | 本期用途 |
|---|---|---|
| `ocr.service.base-url` | `${OCR_BASE_URL:http://ocr-service:8000}` | 上游地址，`OcrClient` 拼接 `OcrApi.RECOGNIZE` 路径；生产经环境变量注入（如 k8s Service 名） |
| `ocr.service.timeout-ms` | `600000`（10 分钟） | 请求级 `responseTimeout`（§7.1 超时修正的取值来源） |
| `spring.servlet.multipart.max-file-size` | `10MB` | 单文件上限 → 超限 40011 |
| `spring.servlet.multipart.max-request-size` | `12MB` | 整请求上限（文件 + multipart 协议开销余量） |
| `api.timeout-ms` | `30000` | **与本期无关**，列出仅为防误用——这是 `ApiClient` 通用池超时，不可用于 OCR（30s 会误杀长推理） |

### 10.2 可调参数说明（按需调整，不改默认值）

| 场景 | 调整项 | 注意 |
|---|---|---|
| 上游推理耗时分布变化 | `ocr.service.timeout-ms` 与上游 P99 对齐 | 过小误杀多页长文档；过大拖长失败等待（用户挂 10 分钟才见 50302）；调整需与上游能力同步评估 |
| 需放宽文件大小（高分辨率扫描件） | `max-file-size` / `max-request-size` 同步放大 | `max-request-size` ≥ `max-file-size` + 开销；**上游 Python 侧请求体限制需同步调整**；前端预检阈值同步 |
| 测试环境验证超时路径 | 临时把 `timeout-ms` 调小（如 `5000`） | 避免 50302 用例等待 10 分钟（§11） |

---

## 11. 测试要点

### 11.1 单元测试（`ocr-service` / `ocr-client`，mock 上游）

**文件校验矩阵**（`OcrRecognizeServiceImpl`，`MockMultipartFile` 构造）：

| # | 用例 | 输入 | 期望 |
|---|---|---|---|
| 1 | file 为 null | `null` | 40000"上传文件不能为空" |
| 2 | 空内容 | 0 字节 + `.png` | 40000"上传文件不能为空" |
| 3 | 扩展名不在白名单 | `.gif` / `.txt` / 无扩展名 | 40010 |
| 4 | ContentType 不符 | `.png` 文件名 + `application/octet-stream` | 40010 |
| 5 | 伪装文件（魔数拦截） | PE 可执行内容改名 `.pdf` | 40010 |
| 6 | webp 正常 / 伪装 | 真 webp / PNG 内容改名 `.webp` | 通过 / 40010 |
| 7 | 大小写扩展名 | `.PNG` / `.Jpg` | 通过（统一小写比较） |
| 8 | 中文文件名 | `合同扫描件.pdf`（真实 PDF 头） | 通过，文件名原样透传 |
| 9 | 五类合法样本 | jpg / jpeg / png / bmp / pdf 各一 | 全部通过并返回识别结果 |

**`OcrClient` 异常转换与解析**（本地假上游或 mock `ocrHttpClient`）：

| # | 用例 | mock 行为 | 期望 |
|---|---|---|---|
| 10 | 连接拒绝 | base-url 指向未监听端口 | 50301 |
| 11 | 响应超时 | 假上游 `sleep` 超过 `timeout-ms`（测试配置调小至 1s） | 50302 |
| 12 | 上游非 200 | 返回 500 / 502 | 50303，message 含"OCR 上游返回 500" |
| 13 | 非法 JSON | 200 + `not-a-json` | 50303"OCR 响应解析失败" |
| 14 | 正常契约 | 200 + §1.1 示例 JSON | VO 全字段断言（含嵌套 `points` 四点、`costMs`） |

### 11.2 集成 / 联调用例（真实链路，按 §6.1 逐行验证）

| # | 用例 | 操作 | 期望 |
|---|---|---|---|
| 15 | 未带 token | cURL 无 Authorization | HTTP 401 + 40100 |
| 16 | 过期 / 伪造 token | 失效 accessToken | HTTP 401 + 40100 |
| 17 | 缺 file part | `-F` 只传其他字段或不传 | 40000"缺少参数: file" |
| 18 | 超大小 | 11MB 文件 | 40011 |
| 19 | 图片端到端 | 真实 `jpg`（含中文文本） | `code=0`，`fullText` 与样本一致，坐标在 0-1 |
| 20 | 多页 PDF 端到端 | 多页扫描 PDF | `code=0`，`fullText` 跨页换行拼接，`textBoxes` 合并 |
| 21 | 上游停机 | 停掉 ocr-service 容器 | 50301 |
| 22 | 前端预览联调 | `OcrHome.vue` 上传 → 结果渲染 | 文本框按归一化坐标叠加图层、全文可复制 |

> 提示：用例 11 / 21 在测试环境将 `ocr.service.timeout-ms` 临时调小（§10.2），避免用例等待 10 分钟。

---

## 12. 实施顺序（里程碑）

按模块依赖方向推进（每个里程碑独立可编译、可验证）：

| 里程碑 | 模块 | 内容 | 验收标准 |
|---|---|---|---|
| **M1** | `ocr-client` | `OcrClient.recognize` 实现（§7.1）+ `OcrResultVO` 移除 `recordId`（§4.1）+ `OcrApi` 注释更新（§7.4） | 用例 10~14 单测绿；假上游联调契约验证（**首个用真实上游验证 §1.1 假设基线的节点**） |
| **M2** | `ocr-service` | `OcrRecognizeService` / `Impl`（§7.2，三重校验） | 用例 1~9 校验矩阵单测绿 |
| **M3** | `ocr-web` | `OcrController`（§7.3） | 用例 15~18 异常路径符合 §6.1 表；swagger-ui 面板可见接口与出参文档 |
| **M4** | 前端对接（契约交付） | `OcrHome.vue` 接入上传 → 调 `/api/ocr/recognize`（本文档为后端设计，仅列契约） | 用例 19 / 20 / 22 端到端通过 |

**M4 前端对接契约要点**：

- axios 发送 `FormData`，**不要手动设置 `Content-Type`**（浏览器需自动生成含 boundary 的头）；
- token 走既有 axios 请求拦截器（与登录态接口一致），无需单独处理；
- 响应统一走既有拦截器的 `code !== 0` 分支提示 `message`；
- 上传前本地预检大小（10MB）与扩展名，减少无效请求；
- 渲染：`points` 为归一化坐标，按实际渲染宽高乘回像素叠加文本框图层；`costMs` 可直接展示耗时。

---

## 13. 风险与取舍

| 风险 / 取舍 | 影响 | 缓解与决策 |
|---|---|---|
| **同步长阻塞占用容器线程** | OCR 最长 10 分钟，N 个并发识别占用 N 个 Tomcat 工作线程（默认 200），极端情况下拖慢其他接口 | 短期：`ocrHttpClient` 每路由 10 连接先于线程池触及上限，天然限流在"10 个并发识别"；中期：V2 异步任务化（上传即返回 taskId + 轮询 / SSE），识别与请求线程解耦（§2.2"不做项"的演进路径） |
| **上游契约为假设基线**（§1.1） | 实际响应若与假设不符（按页分组 / 像素坐标 / 字段名差异）→ 50303 解析失败或前端渲染错位 | 设计上已收敛到 `OcrClient.parseResult` **单点**调整面；M1 联调首日即用真实上游验证，契约差异在最早里程碑暴露 |
| **10MB 大小限制** | 高分辨率扫描 PDF 可能超限，用户被 40011 拦截 | 参数化调整即可（§10.2），需前后端与上游三方同步；前端预检给出友好提示，避免提交后才失败 |
| **多页聚合假设** | 上游若不聚合，大 PDF 的 `textBoxes` 可达数千条，响应体偏大 | 响应为纯 JSON 文本（无图片 base64），MB 级可接受；极端场景 V2 按页分片返回 |
| **中文文件名 multipart 编码** | 上游（Python 侧）解析中文文件名可能乱码 / 报错 | 回退方案已备（§7.1：`setCharset(UTF_8)` / `HttpMultipartMode.EXTENDED`），M1 联调用例 8 验证 |
| **连接池排队** | 每路由 10 并发上限，第 11 个并发请求在池边排队等连接 | 排队受 `responseTimeout` 约束不会无限挂起；V2 可加 `connectionRequestTimeout` 显式快速失败 + 用户级限流（Redis 已就绪） |
| **无自动重试（取舍）** | 网络抖动即失败，用户需手动重新上传 | 有意为之（§2.2）：OCR 推理昂贵，盲目重试放大上游压力；前端"重新识别"按钮即人工重试出口 |
| **`recordId` 移除（取舍）** | 前端无法引用本次识别的历史 | 本期无历史功能（不入库），字段本就无值；V2 落库时重新引入且语义不变（§4.2） |
| **上游错误体不透传（取舍）** | 排查上游问题时前端只见状态码不见详情 | 有意为之：上游错误体可能含内部信息，不宜外泄；完整状态码与 URL 进服务端日志（log.warn / log.error），排查走日志 |
