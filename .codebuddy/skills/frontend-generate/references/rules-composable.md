# 分域规则：composable（src/composables/useXxx.ts）

> 覆盖上游：`vue`、`vueuse-functions`
> 参考实现：`src/composables/useOcrRecognize.ts`、`src/composables/useRecordHistory.ts`

## 1. 职责边界

- 承担**有状态业务逻辑**（加载态、结果、分页交互、竞态、资源生命周期）
- 禁操作 DOM；**弹窗/提示等 UI 交互由调用方组件负责**——但 `ElMessageBox.confirm` 这类"用户确认"属于流程编排，可保留在 composable（见 `useRecordHistory.confirmRemove`）
- 组件保持纯 UI，副作用集中于此

## 2. 入参的响应式契约（ adaptable composable，来自 `create-adaptable-composable`）

无参 composable（现有两个）不涉及；**带入参时必须按此设计**：

| 入参性质 | 类型 | 归一化 |
|---|---|---|
| 只读（值/ref/computed/getter 均可） | `MaybeRefOrGetter<T>` | `watch(toRef(x), ...)` / `toValue(x)` |
| 需要可写 / 双向 | `MaybeRef<T>` | `toRef(x)` |
| 入参本身是函数（回调、比较器） | 原样 `T` | **禁**用 `MaybeRefOrGetter`（会被 `toValue` 当 getter 误调用） |

```ts
import { watch, toRef, toValue } from 'vue'
import type { MaybeRef, MaybeRefOrGetter } from 'vue'

export function useRecordFilter(keyword: MaybeRefOrGetter<string>) {
  watch(toRef(keyword), k => { /* ... */ }, { immediate: true })
}
```

- `toRef()` 必须放在 `watch`/`watchEffect` 内部或作为 watch 源，才能追踪 getter 依赖
- **ID 相关入参一律 `string`**（`MaybeRef<string>`），禁 `number`

## 3. 返回值约定

- 状态用 `shallowRef`，对外暴露时 `readonly(xxx)`，避免外部直接改
- 返回对象（非数组），命名与状态同名；方法用动词：`recognize` / `reset` / `refresh` / `confirmRemove`
- 方法显式返回类型：`Promise<void>` / `Promise<boolean>` / `void`

```ts
return {
  loading: readonly(loading),
  result: readonly(result),
  recognize,
  reset,
}
```

## 4. 生命周期与资源

- `onScopeDispose` 释放：objectURL（`URL.revokeObjectURL`）、定时器、事件监听、未完成的订阅
- 竞态：递增 seq 守卫，仅最后一次请求结果生效（连续上传/搜索场景必做）
- `try/catch/finally` 中：`finally` 只在"仍是最新请求"时复位 loading

## 5. 优先用 VueUse（来自 `vueuse-functions`）

手写前先查 `@vueuse/core` 是否已有，避免重复造轮子与 API 幻觉。高频替换：

| 手写 | VueUse |
|---|---|
| 本地持久化 | `useLocalStorage` |
| 剪贴板 | `useClipboard` |
| 防抖/节流 | `useDebounceFn` / `useThrottleFn` |
| 元素可见性 | `useIntersectionObserver` |
| 窗口尺寸 | `useWindowSize` |
| 事件绑定 | `useEventListener` |
| 暗色模式 | `useColorMode` |
| 文件拖放 | `useDropZone` / `useFileDialog` |

- 本项目已装 `@vueuse/core` **15.0.0**（依赖已锁定，禁升降级）
- 不确定 API 是否存在时，**按 `vueuse-functions` 技能的渐进式披露流程先查概览再查细节**，禁凭印象编造函数名

## 6. 与 store 的关系

- 单页内临时状态 → composable 自己持有 ref
- 跨页共享 / 需缓存的数据 → 交给 `stores/`，composable 用 `storeToRefs` 取（见 `useRecordHistory`）
- 禁在 composable 里复制一份 store 数据（双份真相）
