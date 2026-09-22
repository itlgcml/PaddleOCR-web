# 核心生成铁律（每次生成必读）

## 0. 项目铁律速览（完整版见 `../../paddleocr-frontend-conventions/SKILL.md`）

| 项 | 约束 |
|---|---|
| 框架 | Vite 8.3.0 + Vue 3.5.43 SPA（**非 Nuxt**），Node ≥ 20.19 |
| 语言 | TypeScript 5.9.3，`strict: true`，`vue-tsc -b` 类型不过禁构建 |
| UI | Element Plus 2.14.6，**按需引入**（unplugin-vue-components + auto-import），禁 `app.use(ElementPlus)` |
| 状态 | Pinia 4.0.3，**setup store** 写法 |
| 网络 | Axios 1.20.0，**唯一实例** `src/api/request.ts` |
| 分层 | `views → composables/stores → api → utils/types`，`components` 为纯 UI |
| ID | 一律 `string`（后端雪花 Long 已序列化为字符串） |
| 样式 | 默认 `<style scoped>`；全局样式仅 `src/styles/` |
| 构建 | `vue-tsc -b && vite build` |

### 三处易错契约（已与 `paddleocr-frontend-conventions` 对齐，仍以源码为最终依据）

| 项 | 值 | 出处 |
|---|---|---|
| 成功码 | `code === 0`（`SUCCESS_CODE`，**非** HTTP 语义码 200） | `src/api/request.ts` |
| `timestamp` 类型 | `number`（后端 long 毫秒，**非** ISO 字符串） | `src/types/api.ts` |
| 响应解包 | 拦截器内已解包，API 函数返回业务数据本身（`Promise<T>`） | `src/api/request.ts` |

> 历史不一致已修正：`paddleocr-frontend-conventions` 原写为 `code === 200` / `timestamp: string` / `Promise<ApiResponse<T>>`，
> 现已同步为源码口径。若两者再次冲突，一律以 `src/types/api.ts` 与 `src/api/request.ts` 为准并回改规范文档。

---

## 1. 分层归属（生成第一步，定错层 = 返工）

| 层 | 生成什么 | 禁止 |
|---|---|---|
| `views/` | 路由级页面编排 | 直接 import axios；写复杂业务逻辑（下沉 composables） |
| `components/` | 纯 UI（上传框、结果面板） | 内部调 API；耦合 store |
| `composables/` | 有状态业务逻辑 `useXxx()` | 操作 DOM；弹窗等 UI 行为（归组件层） |
| `stores/` | 跨页面共享状态 | 存仅单页用的临时状态；内部调 ElMessage |
| `api/` | 接口定义（按域拆 `ocr.ts` / `record.ts`） | 定义响应之外的处理逻辑 |
| `types/` | 类型、镜像后端模型 | 重复定义同结构类型 |
| `utils/` | 无状态纯函数 | 引入 Vue 响应式 |
| `router/` | 路由表与守卫 | 直接 `import Xxx from`（须懒加载） |

## 2. TypeScript 红线

- 入参/变量禁 `any`；第三方无类型处用 `unknown` + 类型收窄，或局部 `as` + 注释原因
- 组件用 `defineProps<{ ... }>()` 泛型、`defineEmits<{ (e: 'change', v: string): void }>()`、双向绑定用 `defineModel<T>()`
- API 函数必须显式返回类型：`Promise<OcrResult>`（拦截器已解包，接口函数返回业务数据本身）
- **ID 一律 `string`**
- 禁 `@ts-ignore`；`@ts-expect-error` 仅限第三方类型缺陷且须注释

## 3. 组件与 SFC

- 一律 `<script setup lang="ts">`，区块顺序 `script → template → style`
- 组件名 PascalCase 且多词（`OcrUploader.vue`，非 `Upload.vue`）
- props 单向流动，改数据走 emit / `defineModel`，**禁直接改 props**
- `v-for` 必须 `:key="item.id"`，禁 index 作 key（可变列表）
- 禁 `v-html` 渲染用户/接口文本，OCR 结果用 `{{ }}` 插值

## 4. Element Plus

- 按需引入，图标用 `@element-plus/icons-vue` 组件式引入
- 提示用 `ElMessage` / `ElMessageBox`（auto-import 覆盖），禁 `this.$message`
- `el-upload` 用 `:http-request` 走统一 axios 实例，禁 `action` 直连裸 URL
- 分页：`el-pagination` + `v-model:current-page`，页大小变化重置到第 1 页

## 5. 网络与 API 层

- 唯一实例 `src/api/request.ts`，`baseURL: import.meta.env.VITE_API_BASE_URL`
- 拦截器已统一：业务失败 `ElMessage.error` 并 reject；HTTP 层错误统一提示
- **业务代码只写成功分支**，禁重复 try-catch 弹提示
- 函数命名 `fetchXxx` / `submitXxx`；参数 > 2 个封装为 interface
- `POST` 走 JSON body（上传除外）；分页参数 `pageNum/pageSize`

## 6. 状态与路由

- Pinia setup store：`defineStore('record', () => { ... return { ... } })`，禁 Options store
- 组件取 store 状态用 `storeToRefs`，禁解构丢失响应性
- 路由一律懒加载 `() => import('@/views/Xxx.vue')`
- path kebab-case、name PascalCase；守卫只在 `router/index.ts`

## 7. 工程约束

- 环境变量必须 `VITE_` 前缀，仅经 `import.meta.env.VITE_XXX` 访问
- 开发期允许 `console.warn/error`，**禁提交 `console.log`**；禁打印图片 base64 / 完整 OCR 大文本
- 生成完必须过 `checklist.md` 再交付
