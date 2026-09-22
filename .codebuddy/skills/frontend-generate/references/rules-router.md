# 分域规则：路由（src/router/index.ts）

> 覆盖上游：`vue-router-best-practices`
> 参考实现：`src/router/index.ts`

## 1. 现有形态（新增路由照此追加）

```ts
import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: { name: 'OcrHome' } },
    { path: '/ocr', name: 'OcrHome', component: () => import('@/views/OcrHome.vue') },
    { path: '/history', name: 'RecordHistory', component: () => import('@/views/RecordHistory.vue') },
    { path: '/:pathMatch(.*)*', redirect: { name: 'OcrHome' } },
  ],
})

export default router
```

- **路由组件一律懒加载** `() => import('@/views/Xxx.vue')`，禁顶层 `import Xxx from`
- path kebab-case（`/record-detail`），name PascalCase（`RecordDetail`）
- 重定向用 **name** 不用硬编码 path，避免路径变更失效
- 兜底路由用 `/:pathMatch(.*)*`，**不要留 404 白屏**

## 2. 参数与传参

- 路径参数：`path: '/record/:id'`，组件用 `useRoute().params.id`（类型 `string`，需收窄）
- 查询参数：`useRoute().query`，取值统一 `String(x ?? '')`
- 跳转：`router.push({ name: 'RecordDetail', params: { id } })`，用 name 不用裸字符串路径
- 参数变化但组件复用时（同路由仅 id 变），必须 `watch(() => route.params.id, ...)` 重新取数，否则页面不刷新

## 3. 守卫

- 守卫只写在 `router/index.ts`（`router.beforeEach` / `afterEach`），禁散落组件内
- 必须保证**每条分支都终止**（`return true` / `return { name: ... }` / `next()`），否则导航挂起
- 守卫内禁做重型同步计算；需异步取数判断时加 loading 与超时兜底
- 页面标题在 `afterEach` 里设 `document.title`

## 4. 与视图层协作

- 数据获取在 `views/` 或 composable 的 `onMounted` / `watch(immediate)`，不在守卫里取业务数据
- 路由级组件放 `views/`，被复用 UI 放 `components/`
- 需要缓存页面用 `<KeepAlive :include="[...]">`，并显式处理 `onActivated` 的刷新时机
