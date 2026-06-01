import { Briefcase, Odometer, Setting, User } from '@element-plus/icons-vue'

/**
 * 应用导航配置（单一来源）
 *
 * 设计目标：
 * - 将“路由入口 + 菜单展示 + 权限控制”收敛为同一份配置，避免双源维护；
 * - App 菜单与 router meta 共用 title/roles/keepAlive 等核心信息；
 * - icon 使用组件引用（Element Plus icons），避免字符串与组件映射的错配风险。
 *
 * 字段说明：
 * - path: 路由路径（用于 <router-link> 与 router 匹配）
 * - title: 菜单展示名称，同时可复用为 route meta.title
 * - icon: 菜单图标组件
 * - requiresAuth: 是否需要登录
 * - keepAlive: 是否需要页面缓存（与 <keep-alive> 结合使用）
 * - roles: 允许访问的角色列表（小写字符串），为空表示不做角色限制
 */
export const NAV_ITEMS = [
  {
    path: '/dashboard',
    title: '数据仪表盘',
    icon: Odometer,
    requiresAuth: true,
    keepAlive: true,
    roles: []
  },
  {
    path: '/job-analysis',
    title: '岗位分析',
    icon: Briefcase,
    requiresAuth: true,
    keepAlive: true,
    roles: []
  },
  {
    path: '/job-match',
    title: '智能助手',
    icon: User,
    requiresAuth: true,
    keepAlive: true,
    roles: []
  },
  {
    path: '/data-management',
    title: '系统设置',
    icon: Setting,
    requiresAuth: true,
    keepAlive: true,
    roles: ['admin']
  }
]

/**
 * 根据用户角色过滤可见导航项。
 *
 * @param {string} roleLower 用户角色（小写）
 * @returns {Array<typeof NAV_ITEMS[number]>}
 */
export const filterNavItemsByRole = (roleLower) => {
  const role = String(roleLower || 'user').toLowerCase()
  return NAV_ITEMS.filter((item) => {
    if (!Array.isArray(item.roles) || item.roles.length === 0) return true
    return item.roles.map((r) => String(r).toLowerCase()).includes(role)
  })
}
