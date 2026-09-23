---
name: PaddleOCR v1.6 识别接口后端代码生成
overview: 严格按已复核的设计文档 backend-docs/paddleOCR-v1.6识别文件与预览功能.md 生成后端代码：修改 ocr-client 的 OcrClient（multipart 流式转发 + 请求级超时 + 异常三分法）、OcrResultVO（移除 recordId）、OcrApi（注释更新），新增 ocr-service 的 OcrRecognizeService/Impl（三重文件校验）与 ocr-web 的 OcrController（POST /api/ocr/recognize），并按文档 §11.1 生成单元测试，最后编译 + 测试验证。全程 JDK 8 语法（Set.of/Map.of 需降级）、遵循 java-p3c-generate 规约。
todos:
  - id: client-model-fix
    content: 使用 [skill:java-p3c-generate] 修改 ocr-client：OcrResultVO 移除 recordId、OcrApi 注释更新
    status: completed
  - id: client-ocr-impl
    content: 填充 OcrClient.recognize：multipart 流式转发、请求级超时、异常三分法转换
    status: completed
    dependencies:
      - client-model-fix
  - id: service-recognize
    content: 新增 OcrRecognizeService 接口与 Impl：三重文件校验（JDK8 集合语法）
    status: completed
    dependencies:
      - client-ocr-impl
  - id: web-controller
    content: 新增 OcrController：POST /api/ocr/recognize 与 Swagger 元数据
    status: completed
    dependencies:
      - service-recognize
  - id: unit-tests
    content: 编写单测：校验矩阵用例1~9与OcrClient假上游用例10~14
    status: completed
    dependencies:
      - client-ocr-impl
      - service-recognize
  - id: build-verify
    content: mvn 编译全模块并运行单测，按 checklist 自检交付
    status: completed
    dependencies:
      - web-controller
      - unit-tests
---

## 用户需求
按已通过事实复核的后端设计文档 `backend-docs/paddleOCR-v1.6识别文件与预览功能.md`（834 行，含完整代码设计）生成后端 Java 代码：实现 `POST /api/ocr/recognize` 单文件上传识别接口——用户上传图片（jpg/jpeg/png/bmp/webp）或 PDF，后端流式转发至上游 PaddleOCR-VL 1.6 服务识别，结果 JSON 直接返回，全程不落库、不存文件。

## 产品概述
- 文件三重校验（空内容 / 扩展名+ContentType+魔数白名单 / 10MB 配置兜底），伪装文件被拦截
- JWT 登录即可调用，无角色区分，安全配置零改动
- 识别结果（全文 + 归一化文本框坐标 + 置信度 + 耗时）经统一 `ApiResponse` 包装同步返回
- 上游连接失败/超时/识别失败统一转为业务错误码，不向前端泄漏协议细节

## 核心功能
- 上传识别接口：`POST /api/ocr/recognize`（multipart，参数 `file`）
- multipart 流式转发上游（不落盘、不全量驻内存，内存峰值受 10MB 上限约束）
- 请求级响应超时修正（`ocr.service.timeout-ms`，默认 10 分钟）
- 文件校验矩阵（防扩展名伪装绕过）
- 单元测试：校验矩阵用例（1~9）+ 客户端异常转换/契约解析用例（10~14）


## 技术栈
全部复用既有栈，零新增依赖、零配置变更：
- JDK 8 + Spring Boot 2.7.18（`javax.*`）+ 多模块 Maven（依赖方向 `ocr-web → ocr-service → ocr-client → ocr-common`）
- Apache HttpClient5 5.6.4 `MultipartEntityBuilder` + `InputStreamBody` 流式转发（走既有 `ocrHttpClient` 专用池）
- Hutool `FileTypeUtil` 魔数校验 + `JSONUtil` 响应解析（`ocr-client`/`ocr-service` 已依赖 hutool-all 5.8.47）
- 既有 `ResultCode` 错误码段（40000/40010/40011/50301~50303）+ `ApiResponse<T>` + `BusinessException`，零追加
- 单测：父 POM 公共依赖已含 `spring-boot-starter-test`（JUnit5 + Mockito + MockMultipartFile），仅需创建 `src/test/java` 目录

## 实现方案
以设计文档 §4/§5/§7 为唯一实现基线（文档代码已经过与代码库的事实复核并修正）：
- **修改 `OcrClient.java`**（填充空壳）：`recognize(MultipartFile)` 按文档 §7.1——请求级 `RequestConfig.responseTimeout`（修正 `ocrHttpClient` 无响应超时缺口）；`InputStreamBody` 包装输入流直传（part 名 `file`，透传原始文件名）；异常三分法 `ConnectException→50301` / `HttpTimeoutException|SocketTimeoutException→50302` / 其余 IOException 或非200/解析失败→50303；日志只记文件名/大小/状态码/响应体长度，禁止打印字节与大文本
- **修改 `OcrResultVO.java`**：移除 `recordId` 字段（全库无引用，零破坏）
- **修改 `OcrApi.java`**：RECOGNIZE 注释更新为“图片/PDF 识别接口（多页 PDF 由上游聚合返回单结果）”
- **新增 `OcrRecognizeService` + `Impl`**（ocr-service）：三重校验 + 委托 `OcrClient`，无 `@Transactional`（事务内禁止 HTTP 调用红线）
- **新增 `OcrController`**（ocr-web）：`@RequestParam("file") MultipartFile`（缺 part 复用既有 Handler 转 40000"缺少参数: file"），`consumes = MULTIPART_FORM_DATA_VALUE` + Swagger 元数据
- **JDK 8 强制适配**（文档 §7.2 明示）：`Set.of/Map.of` → `Collections.unmodifiableSet/Map` 包裹 `HashSet/HashMap` 静态初始化；其余文档代码即基线（import 用 `client5.http.config.RequestConfig`）
- **性能**：流式转发避免双份全量内存；复用 ocrHttpClient 连接池（总 20/路由 10，天然限流 10 并发识别）

## 目录结构
```
backend/
├── ocr-client/src/main/java/com/paddleocr/web/client/
│   ├── OcrClient.java                    # [MODIFY] 填充空壳：recognize(MultipartFile) multipart 流式转发 + 请求级超时 + 异常三分法转换 + JSON 解析（文档 §7.1 完整代码）
│   ├── constant/OcrApi.java              # [MODIFY] RECOGNIZE 注释更新（文档 §7.4）
│   └── model/OcrResultVO.java            # [MODIFY] 移除 recordId 字段（文档 §4.1）
├── ocr-client/src/test/java/com/paddleocr/web/client/
│   └── OcrClientTest.java                # [NEW] 用例 10~14：连接拒绝/响应超时/上游非200/非法JSON/正常契约（com.sun.net.httpserver 本地假上游，timeoutMs 调小验证超时）
├── ocr-service/src/main/java/com/paddleocr/web/service/
│   ├── OcrRecognizeService.java          # [NEW] 识别编排接口（文档 §7.2）
│   └── impl/OcrRecognizeServiceImpl.java # [NEW] 三重文件校验（空内容/扩展名/ContentType/魔数，webp 走 RIFF????WEBP 手工前缀校验）+ 编排，JDK8 集合语法
├── ocr-service/src/test/java/com/paddleocr/web/service/impl/
│   └── OcrRecognizeServiceImplTest.java  # [NEW] 校验矩阵用例 1~9（MockMultipartFile；合法样本魔数用真实文件头字节数组构造）
└── ocr-web/src/main/java/com/paddleocr/web/controller/
    └── OcrController.java                # [NEW] POST /api/ocr/recognize（与 AuthController 平级；文档 §7.3）
```

## 执行要点
- 生成时加载 java-p3c-generate 分域规则（rules-client/rules-service/rules-web）与 checklist 自检：类 Javadoc 含 `@author paddleocr / @date 2026-09-23`、`@Slf4j` 占位符日志、无魔法值、Service 接口+Impl
- `OcrRecognizeServiceImplTest` 合法样本需构造真实魔数头（jpg `FF D8 FF`、png `89 50 4E 47`、bmp `42 4D`、pdf `25 50 44 46`、webp `RIFF????WEBP`）
- `OcrClientTest` 超时用例构造 `OcrServiceProperties`（timeoutMs=1000）+ 假上游 sleep 2s，避免长等待
- 验证命令（Windows cmd）：`cd backend && mvn -pl ocr-client,ocr-service,ocr-web -am compile`，随后 `mvn -pl ocr-client,ocr-service -am test`
- 爆炸半径控制：不动 `ocr-common`/`ocr-dal`/SecurityConfig/GlobalExceptionHandler/application.yml/各 pom


## Agent Extensions
### Skill
- **java-p3c-generate**
  - Purpose：生成期间对齐 P3C 规约与项目铁律（按其工作流读取 rules-client.md / rules-service.md / rules-web.md 分域规则，交付前按 checklist.md 自检）
  - Expected outcome：6 个主代码文件与 2 个测试文件全部符合 JDK 8 红线、命名/常量/异常/日志规约与模块分层，无返工项
