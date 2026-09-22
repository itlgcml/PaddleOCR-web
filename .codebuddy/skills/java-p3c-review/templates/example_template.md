# P3C 规约正例/反例

> 适用于 `--example=<规约编号>` 子命令输出

## {{RULE_ID}} - {{RULE_TITLE}}

| 属性 | 值 |
|------|-----|
| **规约编号** | {{RULE_ID}} |
| **规约维度** | {{DIMENSION}} |
| **约束等级** | {{LEVEL}} |
| **问题严重度** | {{SEVERITY}} |

### 规约原文

{{RULE_DESCRIPTION}}

### 正例 ✅

```java
{{POSITIVE_EXAMPLE}}
```

**说明**：{{POSITIVE_EXPLANATION}}

### 反例 ❌

```java
{{NEGATIVE_EXAMPLE}}
```

**问题**：{{NEGATIVE_EXPLANATION}}

### 常见错误场景

| 场景 | 错误代码 | 说明 |
|------|----------|------|
| {{SCENARIO_1}} | ```java\n{{BAD_CODE_1}}\n``` | {{EXPLANATION_1}} |
| {{SCENARIO_2}} | ```java\n{{BAD_CODE_2}}\n``` | {{EXPLANATION_2}} |

### 修复建议

1. **第一步**：{{STEP_1}}
2. **第二步**：{{STEP_2}}
3. **验证**：{{VERIFICATION}}

### 相关规约

- 相关条目：{{RELATED_RULES}}
- 参考文档：[quick_index.md](../knowledge/quick_index.md)

### 工具检测

- 自动检测：{{AUTO_DETECT}}
- 检测脚本：`p3c_checker.py --rule={{RULE_ID}}`

---

*正例/反例模板 v1.0 | P3C Skill*
