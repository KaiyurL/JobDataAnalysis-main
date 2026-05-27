import { createRouter, createWebHistory } from 'vue-router'
import { routes } from '@/app/routes.js'
import { getToken, getUserRoleLower } from '@/shared/authStorage.js'

/**
 * 应用路由实例（app/router）
 *
 * 设计目标：
 * - 将路由装配迁移到 src/app 层，形成“应用级入口”；
 * - 守卫逻辑保持与原实现一致（未登录跳登录、登录页重定向、角色拦截）；
 * - routes 来源于 src/app/routes.js，且与 NAV_ITEMS 共享核心 meta 信息。
 */

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 全局路由守卫：鉴权与角色控制。
 *
 * 关键分支：
 * - requiresAuth 且无 token：跳转登录页
 * - 访问 /login 且已登录：跳转首页（dashboard）
 * - meta.roles 存在且不包含当前角色：回退到 dashboard
 */
router.beforeEach((to, from, next) => {
  const token = getToken()
  const role = getUserRoleLower()

  if (to.meta.requiresAuth && !token) {
    next('/login')
    return
  }
  if (to.path === '/login' && token) {
    next('/dashboard')
    return
  }
  if (Array.isArray(to.meta.roles) && to.meta.roles.length > 0) {
    const allowed = to.meta.roles.map((r) => String(r).toLowerCase())
    if (!allowed.includes(role)) {
      next('/dashboard')
      return
    }
  }
  next()
})

export default router
