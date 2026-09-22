# 子命令与子流程执行指南

> 本文件详细说明 `--checklist`、`--example`、`--query`、`--status` 子命令的执行条件和验收标准。

## 1. Checklist 子流程（`--checklist`）

### 触发条件
- 用户显式提供 `--checklist` 参数
- 命令解析器检测到 `--checklist` 子串

### 执行步骤

#### Step 1 - 读取模板
- 使用 read 工具读取 `templates/review_template.md`
- 提取「审查维度检查清单」小节结构
- **失败处理**：模板读取失败 → 使用内嵌清单结构（见下方）

#### Step 2 - 加载知识文件（必须完整加载全部14个）
按以下顺序依次读取，每个 filePath 必须精确填写：

```
1.  knowledge/quick_index.md
2.  knowledge/programming/naming.md
3.  knowledge/programming/constants.md
4.  knowledge/programming/format.md
5.  knowledge/programming/oop.md
6.  knowledge/programming/collections.md
7.  knowledge/programming/concurrency.md
8.  knowledge/programming/control.md
9.  knowledge/programming/comments.md
10. knowledge/exception_log.md
11. knowledge/mysql.md
12. knowledge/engineering.md
13. knowledge/unit_test.md
14. knowledge/security.md
```

**验收标准**：
- ✅ 必须完整读取全部 14 个文件，不得遗漏
- ⚠️ 单个文件读取失败 → 输出警告，使用内嵌规则填充该维度
- ❌ 超过 3 个文件读取失败 → 中止 checklist 输出，报告「知识库不完整」

#### Step 3 - 构建检查清单
基于 quick_index.md 构建完整检查清单，按 phase 1~6 分组：

| Phase | 维度 | 检查项来源 |
|-------|------|-----------|
| 1 | 命名风格、常量定义、代码格式 | naming.md, constants.md, format.md |
| 2 | OOP 规范、集合处理 | oop.md, collections.md |
| 3 | 并发处理、控制语句、注释规约 | concurrency.md, control.md, comments.md |
| 4 | 异常处理、日志规约 | exception_log.md |
| 5 | MySQL 规范 | mysql.md |
| 6 | 工程结构、单元测试、安全规约 | engineering.md, unit_test.md, security.md |

#### Step 4 - 输出清单
- 使用 write 工具将清单写入 `p3c_checklist.md`
- 通过 stdout 输出文件内容给用户

**输出格式**：
```markdown
## P3C 规约检查清单

### Phase 1: 命名风格、常量定义、代码格式
- [ ] NM-01: 类名 UpperCamelCase
- [ ] NM-02: 方法/变量名 lowerCamelCase
...

### Phase 2: OOP 规范、集合处理
- [ ] OOP-04: 包装类用 equals 比较
...
```

---

## 2. Example 子流程（`--example=<编号>`）

### 触发条件
- 用户显式提供 `--example=<编号>` 参数
- 命令解析器提取到类似 `NM-01`、`OOP-04` 的编号格式

### 执行步骤

#### Step 1 - 解析规约编号（必须成功）
**输入格式**：`<维度前缀>-<序号>`，如 `NM-01`、`OOP-04`

**解析规则**：
1. 提取维度前缀（NM, OOP, COL, CCY, EXP, LOG, UT, SEC, SQL, ENG 等）
2. 提取序号（两位数字）
3. **解析失败处理**：
   - 编号格式错误 → 输出「❌ 编号格式错误，正确格式如 NM-01、OOP-04」
   - 维度前缀无效 → 输出「❌ 未知维度前缀：<前缀>，可用前缀见维度映射表」

#### Step 2 - 根据维度前缀映射目标知识文件

| 前缀 | 维度名称 | 目标知识文件 |
|------|----------|-------------|
| NM | 命名风格 | `knowledge/programming/naming.md` |
| CST | 常量定义 | `knowledge/programming/constants.md` |
| FMT | 代码格式 | `knowledge/programming/format.md` |
| OOP | OOP规范 | `knowledge/programming/oop.md` |
| COL | 集合处理 | `knowledge/programming/collections.md` |
| CCY | 并发处理 | `knowledge/programming/concurrency.md` |
| CTL | 控制语句 | `knowledge/programming/control.md` |
| CMT | 注释规约 | `knowledge/programming/comments.md` |
| EXP | 异常处理 | `knowledge/exception_log.md` |
| LOG | 日志规约 | `knowledge/exception_log.md` |
| UT | 单元测试 | `knowledge/unit_test.md` |
| SEC | 安全规约 | `knowledge/security.md` |
| SQL | MySQL规范 | `knowledge/mysql.md` |
| ENG | 工程结构 | `knowledge/engineering.md` |
| QI | 快速索引 | `knowledge/quick_index.md` |

**映射失败处理**：前缀不在上表中 → 输出「❌ 未知维度前缀：<前缀>」并列出可用前缀

#### Step 3 - 读取对应知识文件（必须成功）
- 使用 read 工具读取映射到的知识文件
- **filePath 必须精确**，如 `knowledge/programming/naming.md`
- **读取失败处理**：
  - 文件不存在 → 输出「❌ 知识文件不存在：<路径>」
  - 读取异常 → 输出「❌ 知识文件读取失败」

#### Step 4 - 定位规约条目（必须成功）
在文件内容中定位包含对应编号的规约条目：

**定位规则**：
1. 搜索编号字符串（如 `NM-01`、`OOP-04`）
2. 匹配到条目后，提取该条目的完整内容（从编号开始到下一个同级条目或文件结束）
3. 在条目内容中提取「正例代码」和「反例代码」部分

**定位失败处理**：
- 未找到对应编号 → 输出「❌ 未找到对应规约编号：<编号>，可用编号请查看 --checklist 或 knowledge/quick_index.md」
- 找到编号但无正例/反例 → 输出条目内容，并说明「本条规约未提供代码示例」

#### Step 5 - 提取并输出正例反例代码（必须输出）

**验收标准**：
- ✅ 正例代码块必须存在且非空（除非规约本身无正例）
- ✅ 反例代码块必须存在且非空（除非规约本身无反例）
- ✅ 代码块必须使用 ```java 标识

**输出格式**：
```markdown
## 规约 <编号> 正例与反例

**规约原文**：
<从知识文件提取的规约描述>

**正例**（符合规约）：
```java
<正例代码>
```

**反例**（违反此规约）：
```java
<反例代码>
```

**约束等级**：【强制】/【推荐】/【参考】
**严重度**：P0 / P1 / P2
```

**输出失败处理**：未输出正例或反例代码块 → 输出「⚠️ 本条规约未提供完整代码示例，仅输出规约原文」

---

## 3. 规约查询子流程（`--query=<关键词>`）

### 触发条件
- 用户显式提供 `--query=<关键词>` 参数
- 命令解析器提取到查询关键词

### 执行步骤

#### Step 1 - 解析查询关键词（容错执行）
**提取规则**：
- 支持格式：`--query=关键词`、`--query "关键词"`
- 提取等号后或引号内的内容作为关键词
- **解析失败**：使用用户输入的整段内容作为关键词

#### Step 2 - 根据关键词映射维度确定目标知识文件（模糊匹配）

**关键词 → 维度映射**：

| 关键词（支持模糊匹配） | 目标维度 | 目标知识文件 |
|---------------------|----------|-------------|
| "命名", "name", "class名" | 命名风格 | `knowledge/programming/naming.md` |
| "常量", "constant", "魔法值" | 常量定义 | `knowledge/programming/constants.md` |
| "格式", "format", "缩进" | 代码格式 | `knowledge/programming/format.md` |
| "面向对象", "OOP", "equals", "hashCode" | OOP规范 | `knowledge/programming/oop.md` |
| "集合", "collection", "List", "Map" | 集合处理 | `knowledge/programming/collections.md` |
| "并发", "线程", "thread", "ThreadPool" | 并发处理 | `knowledge/programming/concurrency.md` |
| "控制", "if", "for", "switch" | 控制语句 | `knowledge/programming/control.md` |
| "注释", "javadoc", "文档" | 注释规约 | `knowledge/programming/comments.md` |
| "异常", "exception", "try-catch" | 异常处理 | `knowledge/exception_log.md` |
| "日志", "log", "logger" | 日志规约 | `knowledge/exception_log.md` |
| "测试", "单元测试", "junit" | 单元测试 | `knowledge/unit_test.md` |
| "安全", "SQL注入", "加盐" | 安全规约 | `knowledge/security.md` |
| "MySQL", "SQL", "索引", "数据库" | MySQL规范 | `knowledge/mysql.md` |
| "工程", "分层", "Controller" | 工程结构 | `knowledge/engineering.md` |

**映射策略**：
1. 精确匹配：关键词完全匹配上表第一列
2. 模糊匹配：关键词包含上表第一列的子串（如 "集合操作" 匹配 "集合"）
3. 多目标匹配：关键词匹配多个维度时，按优先级选择（代码审查相关 > 其他）
4. **无匹配处理**：未匹配到任何维度 → 提示「关键词未识别，可用关键词：命名、常量、格式、OOP、集合、并发、控制、注释、异常、日志、测试、安全、MySQL、工程」

#### Step 3 - 读取对应知识文件
- 使用 read 工具读取映射到的知识文件
- **读取失败处理**：使用输出格式中的「错误输出格式」说明失败原因

#### Step 4 - 定位包含关键词的规约条目（模糊搜索）
在文件内容中定位包含关键词的规约条目：

**搜索规则**：
1. 按行扫描文件内容
2. 匹配包含关键词的行（大小写不敏感）
3. 对于匹配到的行，向上查找到最近的规约条目起始（通常以编号开头如 `NM-01`、`【强制】` 开头）
4. 提取该条目的完整内容

**搜索结果处理**：
- 找到 1 条 → 输出该条详细信息
- 找到多条 → 输出条目列表（编号 + 简要描述），提示用户用 `--example=<编号>` 查看详情
- 未找到 → 输出「未找到匹配规约条目，请检查关键词或使用 --example=<编号> 直接查询」

#### Step 5 - 按结构化格式输出规约查询结果

**成功输出格式**：
```markdown
## 规约查询结果

| 属性 | 值 |
|------|-----|
| 规约编号 | <编号> |
| 约束等级 | 【强制】/【推荐】/【参考】 |
| 严重度 | P0 / P1 / P2 |

**问题描述**：<描述>

**规约原文**：
```
<来自知识文件的原文>
```

**正例代码**：
```java
<正例代码>
```

**反例代码**：
```java
<反例代码>
```
```

**错误输出格式**（当未找到或出错时）：
```markdown
## 规约查询结果

❌ 未找到匹配规约条目

**查询关键词**：<用户输入的关键词>
**尝试匹配维度**：<尝试过的维度列表>

**建议**：
- 尝试其他关键词：命名、常量、OOP、集合、并发、异常、MySQL 等
- 或直接查询具体编号：--example=NM-01、--example=OOP-04
```

---

## 4. 状态查询子流程（`--status`）

### 触发条件
- 用户显式提供 `--status` 参数

### 执行步骤

#### Step 1 - 调用状态管理脚本（必须执行）
- 使用 bash 工具执行：`python scripts/state_manager.py status`
- **必须传递 `status` 参数**，禁止空 args

#### Step 2 - 输出格式化状态摘要
**成功输出**：直接输出脚本的格式化文本

**失败输出**：
```
状态查询失败

可能原因：
- 状态管理脚本不存在：scripts/state_manager.py
- 未初始化审查（未执行过 init）
- 状态文件损坏

建议：重新启动审查流程
```
