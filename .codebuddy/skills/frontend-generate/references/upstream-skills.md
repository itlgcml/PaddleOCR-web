# 上游技能登记（8 个：直接参与写代码）

本文件登记本技能的知识来源。生成代码时**按分域规则加载**，需要上游原文/完整规则集时才安装本体。

## 登记表

| # | 技能 | 上游来源 | 触发时机 | 本项目适配 |
|---|------|----------|----------|------------|
| 1 | `vue` | `antfu/skills`（源 `vuejs/docs`） | 写任何 SFC / composable：Composition API、`script setup` 宏、响应式 API、watcher、生命周期、`Transition`/`Teleport`/`Suspense`/`KeepAlive`、异步组件 | ✅ 直接生效 |
| 2 | `vue-best-practices` | `vuejs-ai/skills`（antfu vendored） | 组件拆分粒度、响应式陷阱、SSR 注意、性能（`shallowRef`、`v-memo`） | ✅ 直接生效 |
| 3 | `vueuse-functions` | `vueuse/skills` | 需要通用能力时优先用 `@vueuse/core` 替代手写；防 API 幻觉（渐进式披露：先概览后细节） | ✅ 生效，需先装 `@vueuse/core` |
| 4 | `pinia` | `antfu/skills`（源 `vuejs/pinia`） | 写 store：state / getters / actions、类型安全写法 | ✅ 直接生效 |
| 5 | `vue-router-best-practices` | `vuejs-ai/skills` | 写路由配置、导航守卫、路由参数、路由组件生命周期 | ✅ 直接生效 |
| 6 | `unocss` | `antfu/skills`（源 `unocss/unocss`） | 原子类、shortcuts、presets、transformers | ⚠️ **条件生效**：本项目用 Element Plus + `<style scoped>`，未装 UnoCSS，仅引入 UnoCSS 后生效 |
| 7 | `antfu-design` | `antfu/skills`（手写） | 设计 token、视觉层级、暗色模式（UnoCSS 中心） | ⚠️ **条件生效**：同 `unocss`；其"UnoCSS 原子类"部分不适用，"层级/间距/暗色"原则可参考 |
| 8 | `web-design-guidelines` | `vercel-labs/agent-skills` | 布局、可访问性（a11y）、视觉规范 | ✅ 生效（与样式方案无关） |

## 已剔除（不登记、不安装）

| 技能 | 剔除原因 |
|------|----------|
| `nuxt` | 本项目是 Vite + Vue 3 SPA，非 Nuxt（无 `nuxt.config.ts`、无 `server/api`） |
| `vite` / `vitest` / `pnpm` / `tsdown` / `turborepo` | 工程配置类，不参与日常代码生成；改 `vite.config.ts` 时按需临时加载 |
| `vitepress` / `slidev` | 文档站 / PPT，与代码生成无关 |
| `antfu`（元技能） | 与本项目红线冲突（"默认不用路径别名""显式 import 反对自动导入" vs 本项目 `@/` 别名 + unplugin 自动导入），**不引入** |

## 安装命令（需要上游原文时）

```bash
# 单个安装（推荐，按需）
npx skills add antfu/skills --skill=vue
npx skills add antfu/skills --skill=pinia
npx skills add antfu/skills --skill=unocss
npx skills add antfu/skills --skill=antfu-design
npx skills add vuejs-ai/skills --skill=vue-best-practices
npx skills add vuejs-ai/skills --skill=vue-router-best-practices
npx skills add vueuse/skills --skill=vueuse-functions
npx skills add vercel-labs/agent-skills --skill=web-design-guidelines

# Claude Code 用户可用 marketplace
/plugin marketplace add vuejs-ai/skills
/plugin install vue-best-practices@vue-skills
```

## 本地状态

| 技能 | 状态 | 位置 | 规模 |
|---|---|---|---|
| `vue` | ✅ **已安装本体** | `.codebuddy/skills/vue/` | `SKILL.md` + `GENERATION.md` + `references/` 3 个（约 18 KB） |
| `vueuse-functions` | ✅ **已安装本体**，且 `@vueuse/core@15.0.0` 已装 | `.codebuddy/skills/vueuse-functions/` | `SKILL.md`（函数索引，37 KB）+ `references/` 267 个（约 0.6 MB） |
| 其余 6 个 | ⬜ 仅登记 + 转述 | 见「与分域规则的映射」 | — |

安装方式：因本机 `git://github.com` 不可达，改用 jsDelivr CDN 拉取 GitHub 原文落盘（`cdn.jsdelivr.net/gh/<repo>@main/...`），内容逐字等同于上游。

### 已安装本体的使用约束（必读）

**`vue`**
- 其 `Preferences` 与本项目 `paddleocr-frontend-conventions` 一致（`script setup lang="ts"`、`shallowRef` 优先、禁 Options API），可直接生效
- 上游未涉及本项目的 API 层契约与 EP 用法，这部分仍以 `core.md` / `rules-api.md` 为准
- 冲突时顺序：`paddleocr-frontend-conventions` > `frontend-generate/references/*` > `vue` 本体

**`vueuse-functions`**
- ✅ 依赖已装：`@vueuse/core@15.0.0`（锁定版本，禁升降级），`AUTO` 类函数可直接使用
- 仍须遵守其 `Invocation` 列：`EXTERNAL` 类函数（依赖 `@vueuse/integrations`、三方库等）**仅在对应依赖已装时使用**，否则先提示装依赖或改手写；`EXPLICIT_ONLY` 需用户明确要求
- 用任何函数前，**先读 `references/<函数名>.md`** 确认用法与类型声明，禁凭印象编造
- **注意技能版本差**：`vueuse-functions` 技能是 VueUse 的函数快照（v1.0），与 15.0.0 可能有增补/改名；以 `node_modules/@vueuse/core` 的实际导出为最终依据
- 与 `rules-composable.md` 冲突时以本地分域规则为准（本地已做项目适配）

## 与分域规则的映射

| 分域规则文件 | 覆盖的上游技能 |
|---|---|
| `rules-component.md` | 1 `vue`、2 `vue-best-practices` |
| `rules-composable.md` | 1 `vue`、3 `vueuse-functions` |
| `rules-store.md` | 4 `pinia` |
| `rules-router.md` | 5 `vue-router-best-practices` |
| `rules-ui-style.md` | 6 `unocss`、7 `antfu-design`、8 `web-design-guidelines` |
| `rules-api.md` | 项目自有契约（无上游技能覆盖） |
