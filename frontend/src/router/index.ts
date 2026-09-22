import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    /** 需要登录 */
    requiresAuth?: boolean
    /** 需要 ADMIN 角色 */
    requiresAdmin?: boolean
    /** 页面标题（afterEach 设置 document.title） */
    title?: string
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/LoginView.vue'),
      meta: { title: '登录' },
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { title: '注册' },
    },
    {
      path: '/',
      component: () => import('@/layouts/BasicLayout.vue'),
      children: [
        {
          path: '',
          name: 'Home',
          component: () => import('@/views/OcrHome.vue'),
          meta: { requiresAuth: true, title: '首页' },
        },
        {
          path: 'system/orgs',
          name: 'OrgManage',
          component: () => import('@/views/system/OrgManageView.vue'),
          meta: { requiresAuth: true, requiresAdmin: true, title: '机构管理' },
        },
        {
          path: 'system/roles',
          name: 'RoleManage',
          component: () => import('@/views/system/RoleManageView.vue'),
          meta: { requiresAuth: true, requiresAdmin: true, title: '角色管理' },
        },
        {
          path: 'system/users',
          name: 'UserManage',
          component: () => import('@/views/system/UserManageView.vue'),
          meta: { requiresAuth: true, requiresAdmin: true, title: '用户管理' },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', name: 'NotFound', redirect: { name: 'Home' } },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  // 未登录：受保护页跳登录（携带回跳参数）
  if (!auth.isLoggedIn) {
    if (to.meta.requiresAuth) {
      return { name: 'Login', query: { redirect: to.fullPath } }
    }
    return true
  }

  // 已登录但用户信息未就绪（刷新页面后的首次导航）：补拉当前用户
  if (!auth.user) {
    try {
      await auth.fetchMe()
    } catch {
      // token 失效时拦截器已统一提示并跳登录；其余异常放行，布局层做空态兜底
      if (!auth.isLoggedIn) {
        return { name: 'Login', query: { redirect: to.fullPath } }
      }
    }
  }

  // 已登录访问登录/注册页：回首页
  if (to.name === 'Login' || to.name === 'Register') {
    return { name: 'Home' }
  }

  // 管理页角色校验（角色实时查库，撤权即时生效）
  if (to.meta.requiresAdmin && !auth.isAdmin) {
    ElMessage.error('无权限访问')
    return { name: 'Home' }
  }

  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · PaddleOCR` : 'PaddleOCR 在线识别'
})

export default router
