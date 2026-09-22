# PaddleOCR-web 前端代码模板

配合 `SKILL.md` 使用。新建文件时以此为基准裁剪，禁止改动版本号。

## 1. package.json（关键片段）

```json
{
  "name": "paddleocr-web-frontend",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc -b && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "axios": "1.20.0",
    "element-plus": "2.14.6",
    "@element-plus/icons-vue": "2.3.2",
    "pinia": "4.0.3",
    "vue": "3.5.43",
    "vue-router": "4.5.1"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "6.0.1",
    "typescript": "5.9.3",
    "vue-tsc": "3.3.11",
    "vite": "8.3.0",
    "unplugin-auto-import": "20.1.0",
    "unplugin-vue-components": "29.0.0"
  }
}
```

## 2. vite.config.ts

```ts
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({ resolvers: [ElementPlusResolver()] }),
    Components({ resolvers: [ElementPlusResolver()] }),
  ],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})
```

## 3. tsconfig.json

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ESNext",
    "moduleResolution": "bundler",
    "strict": true,
    "jsx": "preserve",
    "sourceMap": true,
    "resolveJsonModule": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "types": ["vite/client"],
    "baseUrl": ".",
    "paths": { "@/*": ["src/*"] },
    "noUnusedLocals": true,
    "noUnusedParameters": true
  },
  "include": ["src/**/*.ts", "src/**/*.vue", "auto-imports.d.ts", "components.d.ts"]
}
```

## 4. src/types/api.ts（镜像后端 ApiResponse）

```ts
/** 与后端 com.paddleocr.web.common.ApiResponse 逐字段镜像，字段变更需双侧同步 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T | null
  timestamp: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}

export interface PageQuery {
  pageNum: number
  pageSize: number
}
```

## 5. src/types/ocr.ts（业务模型示例）

```ts
/** 检测框坐标（归一化 0-1） */
export interface TextBox {
  text: string
  confidence: number
  points: Array<{ x: number; y: number }>
}

export interface OcrResult {
  fullText: string
  textBoxes: TextBox[]
  costMs: number
}

export interface OcrRecord {
  id: string            // 雪花 ID，后端 Long 已序列化为字符串，禁止 number
  fileName: string
  resultText: string
  costMs: number
  createTime: string    // ISO-8601
}
```

## 6. src/api/request.ts（唯一 axios 实例）

```ts
import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types/api'

export const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 60000, // OCR 识别含推理耗时，后端同步调用上游 30s，前端放宽至 60s
})

request.interceptors.response.use(
  (resp) => {
    const body = resp.data as ApiResponse<unknown>
    if (body.code !== 200) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message))
    }
    return body.data // 业务代码直接拿到 data，无需再解包
  },
  (error) => {
    const msg = error.code === 'ECONNABORTED' ? '请求超时，请重试' : '网络异常，请稍后重试'
    ElMessage.error(msg)
    return Promise.reject(error)
  },
)
```

注意：拦截器返回 `body.data` 后，接口函数的返回类型用 `Promise<T>`（data 已解包），不再包一层 `ApiResponse<T>`。

## 7. src/api/ocr.ts（接口模块示例）

```ts
import { request } from './request'
import type { OcrResult, OcrRecord } from '@/types/ocr'
import type { PageResult, PageQuery } from '@/types/api'

/** 图片识别（multipart 上传） */
export function submitRecognize(file: File): Promise<OcrResult> {
  const form = new FormData()
  form.append('file', file)
  return request.post('/ocr/recognize', form)
}

export interface RecordQuery extends PageQuery {
  keyword?: string
}

/** 分页查询识别历史 */
export function fetchRecords(query: RecordQuery): Promise<PageResult<OcrRecord>> {
  return request.get('/records', { params: query })
}
```

## 8. src/stores/record.ts（setup store）

```ts
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fetchRecords } from '@/api/ocr'
import type { OcrRecord } from '@/types/ocr'

export const useRecordStore = defineStore('record', () => {
  const records = ref<OcrRecord[]>([])
  const total = ref(0)
  const loading = ref(false)

  async function loadRecords(pageNum: number, pageSize = 10) {
    loading.value = true
    try {
      const page = await fetchRecords({ pageNum, pageSize })
      records.value = page.records
      total.value = page.total
    } finally {
      loading.value = false
    }
  }

  return { records, total, loading, loadRecords }
})
```

## 9. src/router/index.ts

```ts
import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: { name: 'OcrHome' } },
    {
      path: '/ocr',
      name: 'OcrHome',
      component: () => import('@/views/OcrHome.vue'),
    },
    {
      path: '/history',
      name: 'RecordHistory',
      component: () => import('@/views/RecordHistory.vue'),
    },
  ],
})

export default router
```

## 10. 组件示例：src/components/OcrUploader.vue

```vue
<script setup lang="ts">
import type { UploadRequestOptions } from 'element-plus'
import { submitRecognize } from '@/api/ocr'
import type { OcrResult } from '@/types/ocr'

const props = defineProps<{
  /** 允许的扩展名白名单 */
  accept: string[]
  maxSizeMb: number
}>()

const emit = defineEmits<{
  (e: 'success', result: OcrResult): void
  (e: 'error', message: string): void
}>()

const loading = defineModel<boolean>('loading', { default: false })

/** el-upload 自定义上传：走统一 axios 实例 */
async function handleUpload(options: UploadRequestOptions): Promise<void> {
  const file = options.file as File
  const ext = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (!props.accept.includes(ext)) {
    emit('error', `仅支持 ${props.accept.join(' / ')} 格式`)
    return
  }
  if (file.size > props.maxSizeMb * 1024 * 1024) {
    emit('error', `图片不能超过 ${props.maxSizeMb}MB`)
    return
  }
  loading.value = true
  try {
    const result = await submitRecognize(file)
    emit('success', result)
  } catch {
    // 拦截器已统一 ElMessage 提示，这里只通知父组件复位状态
    emit('error', '识别失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-upload
    drag
    :show-file-list="false"
    :http-request="handleUpload"
    :accept="accept.map((e) => '.' + e).join(',')"
  >
    <el-icon :size="48"><Upload /></el-icon>
    <div class="el-upload__text">拖拽图片到此处，或 <em>点击上传</em></div>
  </el-upload>
</template>

<style scoped></style>
```

## 11. .env 文件

```bash
# .env.development
VITE_API_BASE_URL=/api

# .env.production
VITE_API_BASE_URL=/api
```
