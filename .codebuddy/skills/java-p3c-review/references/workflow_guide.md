# 详细工作流程执行指南

> 本文件为 SKILL.md 的补充，包含完整的工作流程执行细节。

## Phase 1: 目标文件读取与校验（强制阻断）

任一校验失败立即中止审查流程。

### Step 1.1 - 目标文件路径确认
优先级：用户提供路径 → `./`、`src/` 自动推断 → 单文件自动选择。

### Step 1.2 - 文件存在性校验
执行 `test -f <file_path>`，失败输出「❌ 目标文件不存在」并中止。

### Step 1.3 - 读取目标代码内容
使用 read 工具读取，失败中止。

## Phase 2: 前置规则加载（容错模式）

外部文件读取失败时使用内嵌备用数据，不中止流程。

- 加载 `references/severity_guide.md` 建立评估基准
- 加载 `references/critical_rules.md` 获取强制规则清单

## Phase 3: 分层架构预检（Controller 类强制规则）

检测 `class XXXController`，命中时标记 ENG-01（Controller 禁止直接操作数据库）为必检项。

## Phase 4: 分阶段检查循环（强制原子步骤）

**初始化**：`current_phase = 1`，`max_phase = 6`（单阶段模式时两者相等）。

### Step 4.1 - 加载阶段知识文件

| Phase | 知识文件 |
|-------|---------|
| 1 | naming.md, constants.md, format.md |
| 2 | oop.md, collections.md |
| 3 | concurrency.md, control.md, comments.md |
| 4 | exception_log.md |
| 5 | mysql.md |
| 6 | engineering.md, unit_test.md, security.md |

任一文件读取失败 → 警告但继续（使用内嵌规则）。

### Step 4.2 - 运行 P3C 静态检查脚本（强制步骤）

命令：`python scripts/p3c_checker.py <文件路径> <current_phase>`
- 超时 30 秒，支持环境变量 `P3C_RESULTS_FILE` 自定义路径

**失败场景与退出码**：

| 场景 | 退出码 | 处理 |
|------|--------|------|
| 脚本不存在 | 1 | 中止 |
| 执行超时 | 1 | 中止 |
| 返回非零 | 1 | 中止 |
| 输出文件缺失 | 1 | 中止 |
| IO/权限错误 | 2 | 中止 |

### Step 4.3 - 按优先级逐条检查

顺序：【强制】→【推荐】→【参考】，部分失败不阻塞其他类别。

### Step 4.4 - 阶段结果持久化

命令：`python scripts/state_manager.py record <phase> '<json>'`
- 支持环境变量 `P3C_STATE_FILE` 自定义路径
- 失败时警告并降级为内存模式，不阻塞流程

### Step 4.5 - 阶段完成判断

`current_phase == max_phase` → break 进入 Phase 5；否则 advance 继续。

## Phase 5: 报告生成与输出（强制原子操作）

### Step 5.1 - 从检查结果提取数据
读取 `.p3c_checker_results.json`，提取字段缺失时使用默认值。

### Step 5.2 - 填充报告各小节

| 报告字段 | 数据源 | 默认值 |
|----------|--------|--------|
| 审查文件 | 目标文件名 | Unknown.java |
| 审查阶段 | phase 参数 | Phase 1-6 |
| P0/P1/P2 问题数 | by_severity | 0 |
| 问题列表 | findings | [] |

### Step 5.3 - 写入审查报告文件（原子写入）

**必须执行**：write 工具写入 `p3c_review_report.md`，采用「临时文件 + 原子重命名」策略确保完整性。失败中止。

### Step 5.4 - 读取并输出报告给用户

read 工具读取后 stdout 输出。失败时保留文件并提示手动读取 `cat p3c_review_report.md`。

## 备用检查逻辑（脚本不可用时）

1. 读取目标代码
2. 正则匹配关键违规模式（NM-01~NM-06、OOP-04、CCY-01 等）
3. 输出简化结果到 `.p3c_checker_results.json`
4. 标记为「简化模式」并在报告中声明

## 状态管理容错机制

脚本失败不阻断主要审查流程，内存回退机制维护 `current_phase` 和 `findings` 缓存。

| 时机 | 命令 | 失败处理 |
|------|------|----------|
| 开始审查 | `state_manager.py init <文件>` | 警告，继续（无断点续审） |
| 阶段完成 | `state_manager.py record <phase> '<json>'` | 警告，内存模式 |
| `--continue` | `state_manager.py advance` | 内存缓存，不持久化 |
| `--status` | `state_manager.py status` | 输出「状态查询失败」 |
| 中断/回滚 | `state_manager.py cleanup` | 告警 |

### 环境变量配置

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `P3C_RESULTS_FILE` | 检查结果输出路径 | `.p3c_checker_results.json` |
| `P3C_STATE_FILE` | 审查状态输出路径 | `.p3c_review_state.json` |

### 临时文件清理

```bash
python scripts/state_manager.py cleanup
```

清理：`.p3c_*_tmp_*.json`、检查结果、状态文件。
