# P3C 规约查询结果

> 适用于 `--query=<关键词>` 子命令输出

## 查询条件

| 项目 | 值 |
|------|-----|
| 查询关键词 | {{QUERY_KEYWORD}} |
| 匹配维度 | {{MATCHED_DIMENSIONS}} |
| 匹配条目数 | {{MATCHED_COUNT}} |

---

## 匹配结果

### 结果 #1

| 属性 | 值 |
|------|-----|
| **规约编号** | {{RULE_ID}} |
| **规约维度** | {{DIMENSION}} |
| **约束等级** | {{LEVEL}} |
| **问题严重度** | {{SEVERITY}} |

**规约原文**：
> {{RULE_CONTENT}}

**详细说明**：
{{RULE_EXPLANATION}}

**正例**：
```java
{{POSITIVE_EXAMPLE}}
```

**反例**：
```java
{{NEGATIVE_EXAMPLE}}
```

**参考文档**：{{REFERENCE_FILE}}

---

## 快速索引

| 编号前缀 | 维度名称 | 条目数 | 参考文档 |
|----------|----------|--------|----------|
| NM | 命名风格 | 16 | programming/naming.md |
| CST | 常量定义 | 5 | programming/constants.md |
| FMT | 代码格式 | 11 | programming/format.md |
| OOP | OOP 规范 | 20 | programming/oop.md |
| COL | 集合处理 | 13 | programming/collections.md |
| CON | 并发处理 | 15 | programming/concurrency.md |
| CTL | 控制语句 | 10 | programming/control.md |
| CMT | 注释规约 | 11 | programming/comments.md |
| EX | 异常处理 | 13 | exception_log.md |
| LOG | 日志规约 | 8 | exception_log.md |
| TBL | 建表规约 | 15 | mysql.md |
| IDX | 索引规约 | 11 | mysql.md |
| SQL | SQL 语句 | 11 | mysql.md |
| ORM | ORM 映射 | 10 | mysql.md |
| UT | 单元测试 | 16 | unit_test.md |
| SEC | 安全规约 | 8 | security.md |
| ENG | 工程结构 | 5 | engineering.md |

---

## 使用示例

```bash
# 查询具体规约
/p3c --query=NM-01

# 查询某个维度的所有规约
/p3c --query=并发处理

# 查询关键词
/p3c --query=线程池
```

*查询结果模板 v1.0 | P3C Skill*
