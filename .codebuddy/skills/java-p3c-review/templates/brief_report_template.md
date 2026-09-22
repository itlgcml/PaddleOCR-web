# CI/CD 简短报告

> 适用于持续集成环境，只输出问题摘要

## 审查摘要

| 项目 | 值 |
|------|-----|
| 审查文件 | {{FILE_NAME}} |
| 审查时间 | {{TIMESTAMP}} |
| 代码行数 | {{TOTAL_LINES}} |
| **问题总数** | **{{TOTAL_ISSUES}}** |
| 🔴 Critical (P0) | {{P0_COUNT}} |
| 🟡 Major (P1) | {{P1_COUNT}} |
| 🟢 Minor (P2) | {{P2_COUNT}} |

---

## 问题列表

### 🔴 Critical (P0) - 必须立即修复

| 序号 | 规约 | 位置 | 问题描述 |
|------|------|------|----------|
{{P0_LIST}}

### 🟡 Major (P1) - 建议本次迭代修复

| 序号 | 规约 | 位置 | 问题描述 |
|------|------|------|----------|
{{P1_LIST}}

### 🟢 Minor (P2) - 酌情改进

| 序号 | 规约 | 位置 | 问题描述 |
|------|------|------|----------|
{{P2_LIST}}

---

## 审查结论

{{#IF_P0}}
❌ **审查不通过** - 存在 {{P0_COUNT}} 个 Critical 问题，必须修复后重新提交
{{/IF_P0}}

{{#IF_NO_P0}}
{{#IF_P1}}
⚠️ **有条件通过** - 存在 {{P1_COUNT}} 个 Major 问题，建议修复
{{/IF_P1}}

{{#IF_NO_ISSUES}}
✅ **审查通过** - 未发现规约违反
{{/IF_NO_ISSUES}}
{{/IF_NO_P0}}

---

## CI 集成示例

```yaml
# .github/workflows/p3c-check.yml
- name: P3C Code Review
  run: |
    python3 scripts/p3c_checker.py src/
    if [ $(jq '.by_severity.P0' .p3c_checker_results.json) -gt 0 ]; then
      echo "❌ P0 issues found!"
      exit 1
    fi
```

*CI/CD 简短报告模板 v1.0 | P3C Skill*
