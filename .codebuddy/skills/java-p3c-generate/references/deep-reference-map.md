# 深度规则索引（按需检索，禁止全量读取）

P3C 条文原文与正例/反例统一放在审查技能的 knowledge 目录。需要细节时**只读对应文件**，不要载入全部。

## 文件路径映射（相对本技能目录）

| 维度 | 编号 | 文件 |
|------|------|------|
| 全部条目编号速查表 | — | `../../java-p3c-review/knowledge/quick_index.md` |
| 命名 | NM-01 ~ NM-16 | `../../java-p3c-review/knowledge/programming/naming.md` |
| 常量 | CST-01 ~ CST-05 | `../../java-p3c-review/knowledge/programming/constants.md` |
| 格式 | FMT-01 ~ FMT-11 | `../../java-p3c-review/knowledge/programming/format.md` |
| OOP | OOP-01 ~ OOP-20 | `../../java-p3c-review/knowledge/programming/oop.md` |
| 集合 | COL-01 ~ COL-13 | `../../java-p3c-review/knowledge/programming/collections.md` |
| 并发 | CON-01 ~ CON-15 | `../../java-p3c-review/knowledge/programming/concurrency.md` |
| 控制语句 | CTRL-01 ~ CTRL-10 | `../../java-p3c-review/knowledge/programming/control.md` |
| 注释 | CMT-01 ~ CMT-11 | `../../java-p3c-review/knowledge/programming/comments.md` |
| 异常 / 日志 / 其他 | EX / LOG / OTH | `../../java-p3c-review/knowledge/exception_log.md` |
| 单元测试 | UT-01 ~ UT-16 | `../../java-p3c-review/knowledge/unit_test.md` |
| 安全 | SEC-01 ~ SEC-08 | `../../java-p3c-review/knowledge/security.md` |
| MySQL（建表/索引/SQL/ORM） | TBL / IDX / SQL / ORM | `../../java-p3c-review/knowledge/mysql.md` |
| 工程结构 | ENG-01 ~ ENG-05 | `../../java-p3c-review/knowledge/engineering.md` |
| 术语（DO/DTO/VO 定义） | — | `../../java-p3c-review/knowledge/glossary.md` |
| 审查视角关键规则（P0/P1/P2） | — | `../../java-p3c-review/references/critical_rules.md` |

## 编号说明（避免误引）

- 控制语句在 `control.md` 中为 `CTRL-*`，`quick_index.md` 记为 `CTL-*`，二者同义，以 `control.md` 为准。
- `critical_rules.md` 的 `EXP-05`（finally 禁 return）等价于 `EX-07`；`SQL-15`（禁 `${}`）等价于 `ORM-04`；该文件内 NM 编号为检查项重排，以 `naming.md` 为准。
- `security.md` 的示例代码含 JDK 9+ 写法（如 `Set.of`、`repeat`），只取规则意图，代码按 `core.md` 的 JDK 8 红线降级。

## 使用方式

1. 先查 `quick_index.md` 的"编号速查表"定位前缀与条目范围
2. 再打开对应 knowledge 文件，按标题（如 `## COL-07`）精准读取，禁止整文件通读
3. 生成后复核走 `checklist.md`，或调用 `java-p3c-review` 做全量审查
