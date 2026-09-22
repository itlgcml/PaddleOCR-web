# 生成后自检清单（交付前必须逐项过）

## P0 阻断项（任一不通过 = 必须改完再交付）

| 自检点 | 通过标准 |
|---|---|
| 层归属 | 纯 UI 在 `components/`；页面在 `views/`；有状态逻辑在 `composables/`；共享状态在 `stores/`；接口在 `api/` |
| 组件写法 | 一律 `<script setup lang="ts">`，区块顺序 `script → template → style`，无 Options API |
| 网络 | 只用 `import request from '@/api/request'`，无 `axios.create`、无 `views` 内直连 axios |
| ID 类型 | 所有 id / recordId 为 `string`，无 `number` |
| TS 红线 | 无 `any`、无 `@ts-ignore`；API 函数有显式返回类型 |
| 响应契约 | 未重复定义 `ApiResponse`/`PageResult`/`PageQuery`；返回类型为解包后的业务数据 |
| props 单向 | 无直接改 props；双向绑定用 `defineModel` |
| 列表 key | `v-for` 用 `:key="item.id"`，无 index 作 key |
| XSS | 无 `v-html` 渲染接口/用户文本 |
| 日志 | 无提交 `console.log`；无打印 base64 / 完整 OCR 大文本 |
| 路由 | 懒加载 `() => import('@/views/Xxx.vue')`，无顶层静态 import 页面组件 |
| 副作用清理 | objectURL / 定时器 / 事件监听都有 `onScopeDispose` 或对应清理 |

## P1 重要项

| 自检点 | 通过标准 |
|---|---|
| 命名 | 组件 PascalCase 多词；API `fetchXxx`/`submitXxx`；store id 小写单数 |
| 响应式选型 | 列表/大对象用 `shallowRef`；派生用 `computed` |
| store | setup store 写法；取状态用 `storeToRefs`；store 内无 `ElMessage` |
| 分页 | 改 pageSize / 搜索 均重置 `pageNum = 1`；参数符合 `PageQuery` |
| 竞态 | 连续请求有 seq 守卫，过期结果不落地 |
| 提示 | 业务错误由拦截器统一提示，业务代码未重复弹提示 |
| 上传 | `el-upload` 用 `:http-request`，无 `action` 裸 URL |
| EP 引入 | 无全量 `app.use(ElementPlus)`；图标按需组件式引入 |
| 样式 | `<style scoped>`；覆盖 EP 用 `:deep()`；无 `!important` |
| a11y | 图标按钮有 `aria-label`；交互三态（加载/空/错误）齐全 |
| composable 入参 | 带入参时用 `MaybeRef`/`MaybeRefOrGetter` + `toRef`/`toValue`；函数型入参未被误当 getter |
| 环境变量 | 仅用 `import.meta.env.VITE_XXX` |

## 领域附加项

- 页面（`views/`）：只做编排，业务逻辑已下沉；不直接 import axios
- 组件（`components/`）：不调 API、不 import store；提示由调用方处理
- composable：返回值 `readonly` 包裹状态；方法显式返回类型；资源有清理
- store：异步 action 有 loading 且 `finally` 复位；删空页回退逻辑在 store
- API：参数 > 2 个已封装 interface；上传用 FormData；JSON body 走默认
- 路由：name 唯一；重定向用 name；有 `/:pathMatch(.*)*` 兜底；守卫分支全部终止
- 样式：可复用尺寸抽 CSS 变量；暗色模式走语义 token

## 类型与构建校验（交付前必跑）

```bash
cd frontend
npx vue-tsc -b          # 类型不过 = 禁止交付
npx vite build          # 构建通过
```

## 常见返工点（本项目高频）

| 错误写法 | 后果 | 正确写法 |
|---|---|---|
| `id: number` | 雪花 ID 精度丢失 | `id: string` |
| `timestamp: string` | 与后端 long 毫秒不符 | `timestamp: number` |
| 业务码按 `200` 判断 | 与实际 `SUCCESS_CODE = 0` 不符 | 以 `src/api/request.ts` 为准 |
| `views` 里 `axios.post` | 配置散落、无统一错误处理 | `api/ocr.ts` 的 `submitRecognize()` |
| `components` 内调 API | 组件不可复用、难测 | 数据由 props 传入，行为 emit 出去 |
| `store.records` 直接解构 | 丢失响应性 | `storeToRefs(store)` |
| `v-for :key="index"` | 复用错乱 | `:key="item.id"` |
| 未释放 objectURL | 内存泄漏 | `onScopeDispose(revokeObjectUrl)` |
| 生成 UnoCSS 原子类 | 项目未装 UnoCSS，样式失效 | scoped CSS + EP 布局组件 |
| Nuxt API（`useFetch`/`server/api`） | 项目非 Nuxt，直接报错 | 用 `api/` + composable |
