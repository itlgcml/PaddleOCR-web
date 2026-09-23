---
name: PaddleOCR v1.6 识别文件与预览功能后端设计文档
overview: 编写 backend-docs/paddleOCR-v1.6识别文件与预览功能.md 完整后端设计文档：文件上传接口（multipart，图片/PDF）→ 纯转发上游 OCR 服务（multipart 直传）→ 直接返回识别 JSON，不入库。格式对齐现有《用户注册与登录功能.md》设计文档范本。
todos:
  - id: draft-doc-skeleton
    content: 使用 [skill:paddleocr-java-conventions] 起草文档骨架与现状分析、目标范围、技术选型章节
    status: completed
  - id: draft-core-design
    content: 撰写分层落地、错误码、核心类设计章节：multipart 流式转发、recordId 移除、超时修正
    status: completed
    dependencies:
      - draft-doc-skeleton
  - id: draft-api-config
    content: 撰写 API 设计、配置项、测试要点、实施顺序、风险取舍章节，含 JSON 示例
    status: completed
    dependencies:
      - draft-core-design
  - id: review-doc-consistency
    content: 复核全文与现有代码事实一致性，并校验与范本格式对齐
    status: completed
    dependencies:
      - draft-api-config
---


## 产品概述
为 PaddleOCR-web 平台编写“文件识别与预览”功能的完整后端设计文档（填充当前为空的 `backend-docs/paddleOCR-v1.6识别文件与预览功能.md`）。功能核心：提供一个文件上传识别接口，用户上传图片（jpg/jpeg/png/bmp/webp）或 PDF，后端接收文件流后以 multipart 形式直接转发至上游 OCR 服务（PaddleOCR-VL 1.6，`{ocr.service.base-url}/ocr/recognize`）识别，将识别结果 JSON 包装后直接返回调用方，全程不落库、不存文件。

## 核心功能
- 单文件上传识别接口：`POST /api/ocr/recognize`（multipart/form-data，参数 `file`），需 JWT 登录，登录用户即可用、不区分角色
- 文件校验：扩展名/ContentType 白名单（图片 + PDF）、空文件拦截，大小由现有 multipart 10MB 配置兜底
- 上游 multipart 直传：图片与 PDF 均由上游直接识别（返回多页结果），Java 端纯流式转发，不引入 PDFBox、不落盘
- 识别结果（全量文本 + 文本框归一化坐标 + 置信度 + 耗时）以统一 `ApiResponse<OcrResultVO>` 返回；`recordId` 因不入库无语义，设计决策为移除
- 上游失败/超时的异常转换与 OCR 段错误码；超文件大小的 `MaxUploadSizeExceededException` 全局处理
- 交付物**仅为设计文档**（不含代码实现），格式对齐 `backend-docs/用户注册与登录功能.md` 章节结构



## 技术栈
- 复用既有栈：JDK 8 + Spring Boot 2.7.18（`javax.*`）+ Spring Security JWT（现有 `/api/**` 默认 authenticated，SecurityConfig 零改动）+ Apache HttpClient5 + hutool（ocr-client 已依赖）
- 零新增依赖、零数据层改动：不引入 PDFBox（上游直接支持 PDF）、无新表/DO/Mapper（ocr-dal 不参与）；仅 ocr-common 追加 `ResultCode` 条目
- 模块分工（遵循依赖方向 ocr-web → ocr-service → ocr-client → ocr-common）：`ocr-web` 新增 OcrController + GlobalExceptionHandler 追加 Handler；`ocr-service` 新增识别编排 Service（文件校验）；`ocr-client` 实现空壳 `OcrClient`（multipart 转发）；`ocr-common` 追加错误码

## 实现方案（文档核心内容）
- 接口设计：`POST /api/ocr/recognize`，入参 `file`（MultipartFile），出参 `ApiResponse<OcrResultVO>`（fullText、textBoxes、costMs），附完整 JSON 示例
- 流式转发：`MultipartFile.getInputStream()` → HttpClient5 `MultipartEntityBuilder` 直传上游 `/ocr/recognize`；multipart 能力加在 `OcrClient`（复用 10 分钟超时的 `ocrHttpClient`）而非 30 秒超时的 `ApiClient`，避免误用通用池
- 修正既有缺口：`ocrHttpClient` 当前未设 response timeout，设计需补 `RequestConfig.responseTimeout = ocr.service.timeout-ms`
- `OcrApi.RECOGNIZE` 注释由“图片识别接口”更新为“图片/PDF 识别接口”
- 错误码沿用 503xx OCR 段追加（文件类型不支持、文件为空、上游识别失败/超时等，编号以 `ResultCode` 现状顺延）
- 事务红线：Service 不加 `@Transactional`（无 DB 操作，OCR 推理可达 10 分钟）
- 文档中明确标注：上游响应 JSON 结构（对齐现有 OcrResultVO/TextBoxVO 模型）为假设基线，上游契约变化时单点调整

## 目录结构
```
backend-docs/
└── paddleOCR-v1.6识别文件与预览功能.md  # [填充] 完整后端设计文档：现状分析/目标范围/技术选型/分层落地/错误码/核心类设计/API设计/配置/测试要点/实施顺序/风险取舍（无数据模型章节，说明不入库决策）
```

## 执行要点
- 文档所有代码事实（OcrClient 空壳、ApiClient 无 multipart、ocrHttpClient 缺 response timeout、multipart 10MB 配置、白名单规则）已逐一核实，写作时不得虚构
- 遵循范本风格：表格化取舍论证、错误码全量表、API 出入参表 + JSON 示例、里程碑分期
- 性能：流式转发避免双份全量内存（受 10MB 上限约束）、复用 ocrHttpClient 连接池（并发 20/路由 10）


## Agent Extensions
### Skill
- **paddleocr-java-conventions**
  - Purpose: 写设计文档时加载本项目 Java 后端约定（模块依赖规则、统一响应、全局异常处理、OCR 服务调用规则），确保设计内容与项目规约一致
  - Expected outcome: 设计文档中的分层归属、错误码分段、OCR 调用规范均符合项目既有约定，无分层倒置或规约冲突
