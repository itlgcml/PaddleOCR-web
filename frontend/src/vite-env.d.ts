/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 接口基础路径：开发经 vite proxy、生产由 Nginx 转发 */
  readonly VITE_API_BASE_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
