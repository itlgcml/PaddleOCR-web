# 分域规则：Pinia store（src/stores/*.ts）

> 覆盖上游：`pinia`

## 1. 固定写法：setup store

```ts
import { defineStore } from 'pinia'
import { ref, shallowRef, computed } from 'vue'
import { fetchRecordPage } from '@/api/record'
import type { OcrRecord } from '@/types/ocr'

export const useRecordStore = defineStore('record', () => {
  const records = shallowRef<OcrRecord[]>([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(10)
  const keyword = ref('')
  const loading = ref(false)

  async function loadRecords(): Promise<void> {
    loading.value = true
    try {
      const page = await fetchRecordPage({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value })
      records.value = page.records
      total.value = page.total
    } finally {
      loading.value = false
    }
  }

  return { records, total, pageNum, pageSize, keyword, loading, loadRecords }
})
```

- **禁 Options store**（`state/getters/actions` 对象式）
- store id 用小写单数：`'record'` / `'ocr'`
- 导出 `useXxxStore`，文件与 store 同名

## 2. 状态类型选择

| 数据 | 选择 |
|---|---|
| 列表 / 大对象（整体替换） | `shallowRef` |
| 基本类型 | `ref` |
| 派生值 | `computed`（**用 computed 不用 getter 函数**） |

## 3. 副作用与错误

- 异步 action 内改 `loading`，**`finally` 复位**（`catch` 不吞：拦截器已统一提示，除非需额外处理）
- **store 内禁调 `ElMessage` / `ElMessageBox`**——UI 反馈归调用方组件/composable
- 删空当前页自动回退一页等**数据一致性逻辑放 store**，不在组件里算

## 4. 组件/composable 取用

```ts
const store = useRecordStore()
const { records, total, pageNum } = storeToRefs(store)  // ✅ 保持响应性
const { records } = store                                // ❌ 丢失响应性
```

- 批量重置用 `store.$reset()`（setup store 需自行保证可重置，或提供 `reset()` action）
- 跨页共享才放 store；单页临时状态放 composable 或组件 ref

## 5. 分页交互（本项目高频）

- `changePage(n)`：直接改 `pageNum` 后 `loadRecords()`
- `changePageSize(n)`：改 `pageSize` 并**重置 `pageNum = 1`** 再查
- `search(kw)`：改 `keyword` 并**重置 `pageNum = 1`** 再查
- 参数校验对齐后端：`pageNum ≥ 1`，`pageSize` 1–100（见 `src/types/api.ts` 的 `PageQuery`）
