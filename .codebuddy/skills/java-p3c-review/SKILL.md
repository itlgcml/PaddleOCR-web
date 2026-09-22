---
name: java-p3c-review
description: "审查 Java 代码的阿里巴巴 P3C 规约符合性，输出结构化问题报告。当用户请求'review/check/examine Java code'、'P3C规约检查'、'代码质量检查'、'看看这段代码有没有问题'，或提供.java文件/代码片段要求分析时触发。Do NOT trigger when: 仅询问Java学习方法、索取P3C文档、讨论技术概念而非具体代码、分析非Java代码、review简历/文档等非代码内容。"
version: 1.2.0
author: windless
p3c_version: "1.5.0"
---

# Java P3C 代码审查 Skill

基于阿里巴巴Java开发手册（P3C）的代码审查专家。

## 触发条件

**必须触发**：用户提及"Java代码审查"/"P3C规范检查"/"代码质量检查"、说"review this Java code"、问"这段代码有没有问题"、提供.java文件要求分析。

**禁止触发**：仅询问Java学习方法、索取P3C文档、讨论技术概念（如"什么是线程安全"）、分析非Java代码、review简历等非代码内容。

## 子命令

| 参数 | 功能 |
|------|------|
| `<文件>` | 启动完整代码审查 |
| `--phase=<N>` | 指定阶段审查（1-6） |
| `--query=<关键词>` | 查询规约条目 |
| `--continue` | 继续下一阶段 |
| `--checklist` | 输出检查清单 |
| `--example=<编号>` | 查询正例/反例 |
| `--status` | 查看审查进度 |

子命令详细规范见 `references/subcommands_guide.md`。

## 约束等级

| 等级 | 标识 | 严重度 |
|------|------|--------|
| 强制 | 【强制】| P0 |
| 推荐 | 【推荐】| P1 |
| 参考 | 【参考】| P2 |

## 核心工作流程

### 1. 目标文件确认（阻断规则）
- 路径优先级：用户路径 → 自动推断（`./`、`src/`）→ 单文件自动选择
- 存在性校验：`test -f <file>`，失败立即中止

### 2. 前置规则加载（容错规则）
- 加载严重度指南与关键规则清单
- 外部文件失败时使用内嵌基准，不阻塞流程

### 3. Controller 检测（条件规则）
- **执行条件**：代码含 `class XXXController`
- **必检项**：ENG-01（Controller禁止直接操作数据库）

### 4. 分阶段检查循环

**阶段知识文件**：Phase 1~6 依次对应 naming/constants/format、oop/collections、concurrency/control/comments、exception_log、mysql、engineering/unit_test/security。

按优先级【强制】→【推荐】→【参考】顺序执行，详细策略见 `references/workflow_guide.md`。

### 5. 报告生成（阻断规则）
从检查结果提取数据，原子写入 `p3c_review_report.md` 后输出到 stdout。写入失败立即中止，stdout 失败时保留文件并提示手动读取。

## 错误处理与回滚策略

**状态写入失败**：保留内存状态继续执行，输出告警，断点续审降级为内存模式。支持通过环境变量 `P3C_RESULTS_FILE` / `P3C_STATE_FILE` 自定义路径后重试。

**审查中断清理**：执行 `python scripts/state_manager.py cleanup` 清理临时文件（`.p3c_*_tmp_*.json`、结果文件、状态文件）。

## 详细指南

完整工作流程、子命令规范、编号与命名、关键规则、严重度标准详见 `references/` 目录下对应文件。

## 静态检查脚本

`python scripts/p3c_checker.py <文件> [phase]` → 输出 `.p3c_checker_results.json`，超时 30s，失败立即中止。报告数据必须逐字引用 JSON，禁止估算。

## 阻断级合规（违反则中止）

1. 目标文件存在且可读
2. 检查脚本成功执行
3. 报告必须写入文件并输出给用户
4. 检查顺序：【强制】→【推荐】→【参考】
5. read 工具 filePath 禁止为空
