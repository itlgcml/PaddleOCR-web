# 分域规则：API 层与类型（src/api/*.ts、src/types/*.ts）

> 无上游技能覆盖，规则来自本项目前后端契约。
> 参考实现：`src/api/request.ts`、`src/types/api.ts`

## 1. 唯一 axios 实例

```ts
import request from '@/api/request'   // ✅
import axios from 'axios'             // ❌ 禁二次 axios.create
```

- `baseURL: import.meta.env.VITE_API_BASE_URL`，超时 60s
- 拦截器已：业务失败 `ElMessage.error` + reject；HTTP/超时/网络错误统一提示
- **业务代码只写成功分支**，禁在调用处重复 try-catch 弹提示（除非要做额外回滚/清理）

## 2. 接口函数

```ts
import request from '@/api/request'
import type { OcrResult, OcrRecord, PageResult, PageQuery } from '@/types/ocr'

export function submitRecognize(file: File): Promise<OcrResult> {
  const form = new FormData()
  form.append('file', file)
  return request.post<unknown, OcrResult>('/ocr/recognize', form)
}

export interface RecordPageQuery extends PageQuery {
  keyword?: string
}

export function fetchRecordPage(params: RecordPageQuery): Promise<PageResult<OcrRecord>> {
  return request.get<unknown, PageResult<OcrRecord>>('/records', { params })
}
```

- **必须显式返回类型**；拦截器已解包，返回业务数据本身（不是 `ApiResponse<T>`）
- 命名：`fetchXxx`（查）/ `submitXxx`（提交）
- 参数 > 2 个封装为 interface；分页参数用 `pageNum` / `pageSize`
- `POST` 用 JSON body，上传用 `FormData`
- 按业务域拆文件：`ocr.ts` / `record.ts`

## 3. 类型镜像规则

- `src/types/api.ts` 已定义 `ApiResponse<T>` / `PageResult<T>` / `PageQuery`，**禁止重复定义**
- 业务类型按域放 `src/types/ocr.ts` 等，与后端 VO/DTO 逐字段镜像
- 后端字段 camelCase；**ID 一律 `string`**
- 时间字段以 `src/types/api.ts` 为准：`timestamp: number`（后端 long 毫秒）；前端格式化交给展示层
- 类型文件禁放运行时代码

## 4. 上传

- `el-upload` 用 `:http-request` 调用 `submitRecognize`，**禁 `action` 直连裸 URL**（绕过拦截器、无统一错误处理）
- 前端做扩展名/大小预校验；后端仍做白名单与魔数校验

## 5. 常见错误对照

| 错误写法 | 后果 | 正确写法 |
|---|---|---|
| `axios.create()` | 配置散落、无统一拦截 | `import request from '@/api/request'` |
| `action="http://.../recognize"` | 绕拦截器、无错误提示 | `:http-request` 走实例 |
| 返回类型 `Promise<ApiResponse<T>>` | 与解包后的实际值不符 | `Promise<T>` |
| `id: number` | 雪花 ID 精度丢失（>2^53） | `id: string` |
| `timestamp: string` | 与实际 long 毫秒不符 | `timestamp: number` |
