import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from './router'
import App from './App.vue'
import '@/styles/index.css'

const app = createApp(App)

// Pinia：状态管理（Element Plus 走 unplugin 按需引入，禁全量注册）
app.use(createPinia())
app.use(router)

app.mount('#app')
