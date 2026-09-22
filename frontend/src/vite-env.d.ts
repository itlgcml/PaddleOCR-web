/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 接口基础路径（开发走 vite proxy，生产走同域反代） */
  readonly VITE_API_BASE_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
