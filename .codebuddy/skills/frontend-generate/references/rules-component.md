# 分域规则：组件（.vue / SFC）

> 覆盖上游：`vue`、`vue-best-practices`

## 1. SFC 骨架（固定形态）

```vue
<script setup lang="ts">
// 1) imports  2) props/emits  3) 状态  4) 派生  5) 方法  6) 生命周期/监听
</script>

<template>
  <!-- 结构 -->
</template>

<style scoped>
/* 局部样式 */
</style>
```

- 区块顺序严格 `script → template → style`，禁调换
- `<script setup lang="ts">` 必带 `lang="ts"`；禁 Options API（`data()` / `methods`）

## 2. 响应式 API 选择（Vue 3.5，来自 `vue` 技能）

| 场景 | 用 |
|---|---|
| 基本类型 / 整体替换的对象 | `ref` / `shallowRef` |
| 只读派生 | `computed` |
| 深层嵌套表单对象 | `reactive`（尽量少用，优先拆 ref） |
| 大对象、列表、不需深层响应 | `shallowRef`（**本项目默认倾向**） |
| 取值不关心响应性 | `toValue()` |
| 从 props/store 派生 ref | `toRef` / `storeToRefs` |

关键 import：
```ts
import { ref, shallowRef, computed, reactive, readonly, toRef, toValue, watch, watchEffect, onMounted, onScopeDispose, nextTick } from 'vue'
```

## 3. Props / 事件 / 双向绑定

```ts
// ✅ 泛型声明
const props = defineProps<{ recordId: string; disabled?: boolean }>()
const emit = defineEmits<{ (e: 'change', v: string): void; (e: 'close'): void }>()
const keyword = defineModel<string>({ required: true })

// ❌ 运行时声明（无类型推导）
defineProps({ recordId: String })
```

- **禁直接改 props**（`props.list.push(...)`）；改数据走 emit 或 `defineModel`
- 对外暴露只读状态时用 `readonly(ref)` 包裹（参考 `useOcrRecognize` 返回值）

## 4. 内置组件与异步组件

- `Transition` / `Teleport` / `Suspense` / `KeepAlive` 用内置标签，不需 import
- 组件懒加载：`defineAsyncComponent(() => import('./Heavy.vue'))`
- 路由级懒加载在 `router/index.ts` 用 `() => import('@/views/Xxx.vue')`

## 5. 列表与条件渲染

- `v-for` 必须 `:key="item.id"`，**禁 `:key="index"`**（可变列表会复用错乱）
- `v-if` 与 `v-for` 不同时写在一个元素上；用 `<template v-for>` 包裹或直接派生列表
- 空态、加载态、错误态都要有可见反馈（Element Plus `v-loading` / `el-empty` / `el-skeleton`）

## 6. 性能与踩坑（来自 `vue-best-practices`）

- 大列表/图片结果用 `shallowRef` + 整体替换，避免深层 Proxy 开销
- 长列表渲染控制条数或虚拟滚动；`v-memo` 仅在明确收益时使用
- 副作用必须清理：`onScopeDispose` 释放 objectURL / 定时器 / 事件监听（参考 `useOcrRecognize` 的 `onScopeDispose(revokeObjectUrl)`）
- 连续请求做**竞态守卫**（递增 seq，仅最后一次生效），见 `useOcrRecognize` 的 `requestSeq`
- 禁 `v-html` 渲染 OCR 文本 / 文件名等外部内容（XSS）

## 7. 组件分层纪律

| 位置 | 允许 | 禁止 |
|---|---|---|
| `components/` | 纯 UI + props/emits/插槽 | 调 API、import store、`ElMessage` 提示由调用方处理 |
| `views/` | 组装组件、调用 composable | 直接 `import axios`；堆业务逻辑 |

## 8. 命名

- 组件文件 PascalCase 且多词：`OcrUploader.vue`、`RecordTable.vue`、`ResultPanel.vue`
- 事件名 kebab 语义：`change` / `remove` / `update:keyword`
