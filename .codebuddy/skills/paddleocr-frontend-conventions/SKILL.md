---
name: paddleocr-frontend-conventions
description: Use when writing, generating, or reviewing frontend code in this project (frontend module: package.json, vite.config.ts, tsconfig.json, src/api, src/components, src/views, src/composables, src/stores, src/router, src/types, src/utils). Covers Vue 3.5 + TypeScript 5.9 + Vite 8 + Element Plus 2.14 + Pinia 4 + Axios 1.20 conventions: TS strict rules, layered structure, Element Plus on-demand import, axios unified response handling, frontend-backend contract (ApiResponse mirroring, id as string).
metadata:
  version: "1.0.0"
---

# PaddleOCR-web 前端规范（Vue 3 + TypeScript）

本项目前端代码生成/审查的**最高优先级**规范。与社区技能（vuejs-ai/skills 等）冲突时，**以本技能为准**。

## 1. 技术栈锁定（禁止改动版本）

| 技术 | 固定版本 | 备注 |
|------|----------|------|
| Vue | 3.5.43 | 组合式 API，`<script setup lang="ts">` |
| TypeScript | 5.9.3 | 禁用 TS 7 语法/配置假设 |
| Vite | 8.3.0 | Node ≥ 20.19 |
| Element Plus | 2.14.6 | 按需引入（unplugin） |
| VueUse | 15.0.0 | `@vueuse/core`，按需 import 单个函数（tree-shaking），禁全量引入 |
| Pinia | 4.0.3 | setup store 写法 |
| Axios | 1.20.0 | 单例 + 拦截器 |
| vue-tsc | 3.3.11 | 构建时类型检查 `vue-tsc -b` |

版本以 `../../../项目技术栈.md` 与 `frontend/package.json` 为准，禁止生成时升级或混入其他版本号。

## 2. 分层结构与依赖方向

```
views/ ──► composables/ + stores/ ──► api/ ──► utils/ + types/
components/（纯 UI，被 views/composables 使用）
```

| 层 | 职责 | 禁止 |
|----|------|------|
| views/ | 页面编排、路由级组件 | 直接 import axios；写复杂业务逻辑（下沉 composables） |
| components/ | 纯 UI 展示（上传框、结果面板） | 内部调 API；耦合 store |
| composables/ | 有状态业务逻辑 `useXxx()` | 操作 DOM；弹窗等 UI 行为（归组件层） |
| stores/ | 跨页面共享状态 | 存仅单页用的临时状态 |
| api/ | 接口定义（按业务域拆 `ocr.ts`/`record.ts`） | 定义响应之外的处理逻辑 |
| types/ | 类型定义、镜像后端模型 | 重复定义同结构类型 |

## 3. TypeScript 红线

- `tsconfig` 必须 `strict: true`
- **禁随意 `any`**：入参/变量禁 `any`；第三方无类型处用 `unknown` + 类型收窄，或局部 `as` 断言并注释原因
- 组件：`defineProps<{ ... }>()` 泛型、`defineEmits<{ (e: 'change', v: string): void }>()`、双向绑定用 `defineModel<T>()`
- ref 显式泛型仅复杂数据需要：`ref<OcrResult | null>(null)`
- API 模块函数必须声明返回类型：拦截器已解包，声明**业务数据本身** `Promise<OcrResult>` / `Promise<PageResult<OcrRecord>>`，不声明 `Promise<ApiResponse<T>>`
- **ID 类型一律 `string`**（后端雪花 Long 已序列化为字符串）
- 禁 `@ts-ignore`（用 `@ts-expect-error` + 注释，且仅限第三方类型缺陷）

## 4. 组件规范

- 一律 `<script setup lang="ts">`，区块顺序 `script → template → style`
- 组件名 PascalCase 且多词（`OcrUploader.vue` 非 `Upload.vue`）
- props 单向流动；子组件改数据走 emit 或 `defineModel`，**禁直接改 props**
- `v-for` 必须绑定 key（用业务 id，禁 index 作 key 于可变列表）
- **禁 `v-html` 渲染用户/接口文本**（XSS）；OCR 结果用 `{{ }}` 插值
- 样式默认 `scoped`；全局样式仅 `src/styles/`

## 5. Element Plus 规范

- **按需引入**：靠 `unplugin-auto-import` + `unplugin-vue-components`（ElementPlusResolver），禁全量 `app.use(ElementPlus)`
- 消息提示用 `ElMessage` / `ElMessageBox` / `ElMessage` 的 api 导入方式（auto-import 已覆盖），禁 `this.$message`（无 Options API）
- 表格分页：`el-pagination` + `v-model:current-page` 双向，页大小变化重置到第 1 页
- 上传组件：`el-upload` 用 `:http-request` 自定义（走统一 axios 实例携带拦截逻辑），禁 `action` 直连裸 URL
- 图标：`@element-plus/icons-vue` 按需组件式引入 `<el-icon><Upload /></el-icon>`

## 6. Axios 与 API 层规范

- **唯一实例** `src/api/request.ts`：`baseURL: import.meta.env.VITE_API_BASE_URL`
- 响应拦截器统一解包：`code === 0`（`SUCCESS_CODE`）返回 `data` 字段；否则 `ElMessage.error(message)` 并 reject
- HTTP 错误（超时/5xx/网络）拦截器统一提示，业务代码只处理成功分支
- **注意**：业务状态码是 `0`（非 HTTP 语义码 200），以 `src/api/request.ts` 的 `SUCCESS_CODE` 为准
- 拦截器已解包，API 函数返回**业务数据本身**，声明类型为 `Promise<T>` 而非 `Promise<ApiResponse<T>>`
- 接口函数命名 `fetchXxx` / `submitXxx`，参数超过 2 个封装为 interface
- 后端约定：`POST` 用 JSON body（上传除外），分页参数 `pageNum/pageSize`，响应对齐 `ApiResponse<T>`

## 7. 前后端契约（镜像规则）

`src/types/api.ts` 必须与后端 `common/ApiResponse` 逐字段镜像：

```ts
interface ApiResponse<T> {
  code: number        // 0 成功；4xxxx 客户端错误；5xxxx 系统；503xx OCR 服务
  message: string
  data: T | null
  timestamp: number   // 服务器时间戳，后端 long 毫秒（非 ISO 字符串）
}
```

- 后端字段 camelCase；`timestamp` 为 `number`（后端 long 毫秒，非 ISO-8601 字符串），前端格式化交给展示层
- 契约以 `src/types/api.ts` 与 `src/api/request.ts` 源码为准；两者与本文件冲突时以源码为准并同步修正本文件
- 分页响应固定 `{ records: T[], total: number, pageNum: number, pageSize: number }`

## 8. 状态与路由

- Pinia 用 **setup store**：`defineStore('record', () => { ... return {...} })`；禁 Options store
- store 内不调 ElMessage（UI 反馈归调用方组件）
- 路由组件一律懒加载 `() => import('@/views/xxx.vue')`
- 路由 path kebab-case、name PascalCase；守卫放 `router/index.ts`

## 9. 环境与工程

- 环境变量必须 `VITE_` 前缀；代码中仅经 `import.meta.env.VITE_XXX` 访问
- `.env.development` 配 proxy 调 `/api`；`.env.production` 配 `VITE_API_BASE_URL=/api`
- `vite.config.ts` dev proxy：`'/api': { target: 'http://localhost:8080', changeOrigin: true }`
- 日志：开发期允许 `console.warn/error`，**禁提交 `console.log`**；禁打印图片 base64/完整 OCR 大文本
- 构建命令链：`vue-tsc -b && vite build`（类型不过禁构建）

## 10. 常见错误对照表

| 错误写法 | 后果 | 正确写法 |
|----------|------|----------|
| `import { ElMessage } from 'element-plus'` 且全量注册 | 打包体积暴涨 | unplugin 按需 + auto-import |
| `defineProps({ msg: String })` 运行时声明 | 无类型推导 | `defineProps<{ msg: string }>()` |
| `props.list.push(...)` | 单向数据流破坏、警告 | emit 事件或 `defineModel` |
| `id: number` | 雪花 ID 精度丢失（>2^53） | `id: string` |
| `action="http://.../recognize"` 直传 | 绕过拦截器、无统一错误处理 | `:http-request` 走 axios 实例 |
| views 里 `axios.get(...)` | 散落配置、无法统一换 baseURL | `api/ocr.ts` 导出 `fetchRecognize()` |
| `vue-router` 路由直接 `import Xxx from` | 首屏全量加载 | `() => import('@/views/Xxx.vue')` |
| `v-for :key="index"`（可变列表） | 复用错乱、状态串行 | `:key="item.id"` |
| Options API（`data()`/`methods`） | 与全项目风格冲突 | `<script setup lang="ts">` |
| 手写 `modelValue` prop + `update:modelValue` | 啰嗦易错 | `defineModel<T>()` |

## 11. 冲突裁决

同时加载 `vue-best-practices` 等社区技能时：通用 Vue 素养（响应式、组件拆分、composable 设计）遵循社区技能；**版本号、目录结构、EP 用法、API 层契约、命名**遵循本技能。
