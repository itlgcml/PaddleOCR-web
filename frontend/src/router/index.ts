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
    // 未匹配路由兜底回首页，避免 404 白屏
    { path: '/:pathMatch(.*)*', redirect: { name: 'OcrHome' } },
  ],
})

export default router
