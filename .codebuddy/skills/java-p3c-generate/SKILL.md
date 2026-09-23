---
name: java-p3c-generate
description: "生成本项目 Java 后端代码时使用（P3C 生成侧规约，java-p3c-review 的互补技能）。当用户要求'写/新增/实现一个 Java 类、Controller、Service、Mapper、DTO、DO、枚举、DDL'，或说'按规范生成后端代码'、'新增一个 OCR 接口'、'加一张表'时触发。Do NOT trigger when: 审查/检查已存在的代码（用 java-p3c-review）、生成前端 Vue/TS 代码（用 paddleocr-frontend-conventions）、生成单元测试/测试代码（由专门的测试 skill 负责）、仅讨论技术概念、只改 yml/配置。"
version: 1.0.0
---

# Java P3C 代码生成规约（PaddleOCR-web）

本技能是 `java-p3c-review` 的**生成侧**互补技能：`java-p3c-review` 负责"写完之后查"，本技能负责"写的时候就不违规"。

## 规则来源与优先级

1. **项目铁律最高**：`../paddleocr-java-conventions/SKILL.md`（JDK 8 / Boot 2.7.18 / 多模块 / 依赖方向）
2. **P3C 条文**：`../java-p3c-review/knowledge/**`（约 195 条，编号的唯一来源）
3. 冲突裁决：项目铁律 > P3C 条文 > 通用习惯；P3C 原文示例若使用 JDK 9+ 语法，一律按项目 JDK 8 红线降级。

## 渐进式披露（按需加载，禁止一次读全部）

| 层级 | 文件 | 何时加载 |
|------|------|----------|
| 入口 | 本文件 | 每次生成 |
| 核心 | `references/core.md` | **每次生成必读** |
| 分域 | `references/rules-dal.md` | 生成 DO / Mapper / mapper XML / DDL / 索引 / SQL |
| 分域 | `references/rules-service.md` | 生成 Service / Impl / 业务 DTO / 事务 / 线程池 |
| 分域 | `references/rules-web.md` | 生成 Controller / 入参 DTO / 全局异常处理 / 上传 / Web 配置 |
| 分域 | `references/rules-client.md` | 生成 ocr-client 的 HTTP 客户端 / 配置属性 / 外部服务 VO |
| 深度 | `references/deep-reference-map.md` | 需要某条规约原文或正反例时，按图索骥读 knowledge 文件 |
| 自检 | `references/checklist.md` | 代码写完、交付用户前 |

代码模板（父 POM、application.yml、ApiResponse、MyBatis-Plus 配置、双 HttpClient、启动类等）在 `../paddleocr-java-conventions/templates.md`，**按模板复制，不要凭记忆重写**。

## 生成工作流（阻断级）

1. **定模块**：先判断归属 —— DO/Mapper → `ocr-dal`；外部服务调用 → `ocr-client`；业务编排/DTO/VO → `ocr-service`；Controller/全局配置 → `ocr-web`。放错模块 = 返工。
2. **读核心**：加载 `references/core.md`，并按第 1 步结果加载对应分域规则（最多 2 个）。
3. **取模板**：从 `../paddleocr-java-conventions/templates.md` 复制同类骨架。
4. **写实现**：边写边对齐 P3C 生成铁律（命名 / 常量 / 格式 / OOP / 集合 / 并发 / 异常 / 日志）。
5. **自检交付**：按 `references/checklist.md` 逐项自检；有疑问时调用 `java-p3c-review` 复核生成结果。

## 阻断级合规（违反即中止并修正）

1. 每次生成必须先确定模块归属，根包一律 `com.paddleocr.web.*`
2. 必须 JDK 8 语法 + Spring Boot 2.7.18 + `javax.*`（禁 `jakarta.*`）
3. 必须复用 `ApiResponse<T>` 与 `BusinessException(ResultCode)`，禁止自造返回结构
4. 禁止 SQL 字符串拼接与 `${}`（只用 `#{}` / `LambdaQueryWrapper`）
5. 禁止 `Executors` 创建线程池；禁止事务方法内做 HTTP 调用
6. 禁止魔法值、禁止吞异常、禁止 `System.out.println`
7. **禁止执行任何 `mvn` / `npm` 命令**（编译、测试、打包、安装依赖等一律由用户自行执行），本技能只负责生成与修改代码文件
8. **禁止生成任何测试代码**（`src/test/**` 下的单元测试等），测试由专门的测试 skill 负责，本技能不涉及
9. 生成后必须执行自检清单再交付

## 子命令

| 参数 | 功能 |
|------|------|
| `<需求描述>` | 按工作流生成代码 |
| `--module=dal\|service\|web\|client\|common` | 强制指定目标模块 |
| `--check` | 只做生成后自检（输出清单结果） |
| `--rule=<编号>` | 查询单条 P3C 生成要点（如 `--rule=COL-07`） |
