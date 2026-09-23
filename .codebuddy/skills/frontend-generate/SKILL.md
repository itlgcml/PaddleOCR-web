---
name: frontend-generate
description: "生成本项目前端代码时使用（Vue 3 + TS 生成侧编排技能，paddleocr-frontend-conventions 的下游执行体）。当用户要求'写/新增/实现一个页面、组件、composable、store、路由、接口层、类型'，或说'按规范生成前端代码'、'新增一个 OCR 结果展示页'时触发。Do NOT trigger when: 审查/检查已存在的代码（先走 checklist，复核走前端审查）、生成 Java 后端代码（用 java-p3c-generate）、仅改 yml/env/依赖版本、Nuxt 相关（本项目非 Nuxt）。"
version: 1.0.0
---

# 前端代码生成规约（PaddleOCR-web）

本技能是 `paddleocr-frontend-conventions` 的**生成侧执行体**：
`paddleocr-frontend-conventions` 定义"项目铁律与契约"，本技能定义"生成时怎么落地、按什么顺序、交付前查什么"。

## 规则来源与优先级

1. **项目铁律最高**：`../paddleocr-frontend-conventions/SKILL.md`（版本锁定 / 分层 / TS 红线 / EP 用法 / 前后端契约）
2. **上游 8 个技能**：`references/upstream-skills.md`（Vue 官方与社区最佳实践，本技能的知识来源）
3. 冲突裁决：**项目铁律 > 上游技能 > 通用习惯**；上游示例若用 Nuxt API、UnoCSS、自动导入等本项目不具备的能力，一律按本项目红线降级或替换。

## 适用范围（8 个直接参与写代码的上游技能）

| # | 上游技能 | 本项目是否生效 |
|---|----------|----------------|
| 1 | `vue` | ✅ 生效 |
| 2 | `vue-best-practices` | ✅ 生效 |
| 3 | `vueuse-functions` | ✅ 生效（需引入 `@vueuse/core` 时） |
| 4 | `pinia` | ✅ 生效 |
| 5 | `vue-router-best-practices` | ✅ 生效 |
| 6 | `unocss` | ⚠️ 条件生效（本项目用 Element Plus + scoped CSS，未装 UnoCSS） |
| 7 | `antfu-design` | ⚠️ 条件生效（UnoCSS 设计体系，同上） |
| 8 | `web-design-guidelines` | ✅ 生效（界面/可访问性，与样式方案无关） |

已剔除：`nuxt`（本项目是 Vite + Vue 3 SPA，非 Nuxt）、`vite`/`vitest`/`pnpm`/`tsdown`/`turborepo`（工程配置类，不参与日常代码生成）、`vitepress`/`slidev`（与代码生成无关）。

**已安装本体**：`vue`（`.codebuddy/skills/vue/`）、`vueuse-functions`（`.codebuddy/skills/vueuse-functions/`）；其余 6 个仅登记转述。使用约束见 `references/upstream-skills.md`。

## 渐进式披露（按需加载，禁止一次读全部）

| 层级 | 文件 | 何时加载 |
|------|------|----------|
| 入口 | 本文件 | 每次生成 |
| 核心 | `references/core.md` | **每次生成必读** |
| 登记 | `references/upstream-skills.md` | 需要某技能原文/安装方式时 |
| 分域 | `references/rules-component.md` | 生成 `.vue` 组件（SFC / props / 事件 / 内置组件） |
| 分域 | `references/rules-composable.md` | 生成 `src/composables/useXxx.ts` |
| 分域 | `references/rules-store.md` | 生成 `src/stores/*.ts`（Pinia setup store） |
| 分域 | `references/rules-api.md` | 生成 `src/api/*.ts` 与 `src/types/*.ts` |
| 分域 | `references/rules-router.md` | 生成 `src/router/index.ts` 路由项 |
| 分域 | `references/rules-ui-style.md` | 生成样式、布局、视觉与可访问性 |
| 自检 | `references/checklist.md` | 代码写完、交付用户前 |

## 生成工作流（阻断级）

1. **定层**：先判断归属 —— 纯 UI → `components/`；页面编排 → `views/`；有状态逻辑 → `composables/`；跨页共享 → `stores/`；接口 → `api/`；类型 → `types/`。放错层 = 返工。
2. **读核心**：加载 `references/core.md`，并按第 1 步结果加载对应分域规则（最多 2 个）。
3. **取模板**：从 `src/` 现有同类文件复制骨架（`src/composables/useOcrRecognize.ts`、`src/api/request.ts` 等），**按现文件复制，不要凭记忆重写**。
4. **写实现**：`<script setup lang="ts">`，区块顺序 `script → template → style`；边写边对齐核心铁律。
5. **自检交付**：按 `references/checklist.md` 逐项自检后再交付。

## 阻断级合规（违反即中止并修正）

1. 每次生成必须先确定层归属，禁把 API 调用写进 `components/`，禁在 `views/` 里直接 import axios
2. 一律 `<script setup lang="ts">`，禁 Options API
3. 必须复用 `src/api/request.ts` 单一 axios 实例，禁二次 `axios.create`
4. 响应与类型必须对齐 `ApiResponse<T>`（见 `src/types/api.ts`），禁自造返回结构
5. **ID 类型一律 `string`**（后端雪花 Long 已序列化为字符串），禁 `number`
6. 禁 `any`、禁 `@ts-ignore`、禁 `v-html` 渲染接口文本、禁提交 `console.log`
7. **禁止执行任何 `npm` / `pnpm` 命令**（安装依赖、构建、启动、lint 等一律由用户自行执行），本技能只负责生成与修改代码文件
8. **禁止生成任何测试代码**（`*.spec.ts` / `*.test.ts` / vitest 用例等），测试由专门的测试 skill 负责，本技能不涉及
9. 生成后必须执行自检清单再交付

## 子命令

| 参数 | 功能 |
|------|------|
| `<需求描述>` | 按工作流生成代码 |
| `--layer=component\|view\|composable\|store\|api\|router\|style` | 强制指定目标层 |
| `--check` | 只做生成后自检（输出清单结果） |
| `--upstream` | 列出 8 个上游技能及其安装命令 |
