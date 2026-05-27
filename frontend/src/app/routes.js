import { NAV_ITEMS } from '@/app/navConfig.js'

const Dashboard = () => import('@/pages/DashboardPage.vue')
const JobAnalysisLayout = () => import('@/pages/JobAnalysisLayoutPage.vue')
const JobAnalysis = () => import('@/pages/JobAnalysisPage.vue')
const JobMatch = () => import('@/pages/JobMatchPage.vue')
const CompanyInsight = () => import('@/pages/CompanyInsightPage.vue')
const DataManagement = () => import('@/pages/DataManagementPage.vue')
const Login = () => import('@/pages/LoginPage.vue')

/**
 * 应用路由定义（优先复用 NAV_ITEMS 中的元信息）
 *
 * 设计目标：
 * - 路由结构仍以业务页面为中心，但 title/roles/keepAlive 等与导航强相关的信息从 NAV_ITEMS 统一派生；
 * - 对于非导航入口（如 /login、/job-analysis 子路由）在此处定义自己的 meta；
 * - 保持原路由结构不变，确保现有页面跳转与 query 逻辑不受影响。
 */

const navMetaByPath = Object.fromEntries(
  (NAV_ITEMS || []).map((item) => [
    item.path,
    {
      title: item.title,
      requiresAuth: item.requiresAuth,
      roles: item.roles,
      keepAlive: item.keepAlive
    }
  ])
)

export const routes = [
  {
    path: '/login',
    name: 'Login',
    component: Login,
    meta: { title: '登录', requiresAuth: false }
  },
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: Dashboard,
    meta: navMetaByPath['/dashboard']
  },
  {
    path: '/job-analysis',
    name: 'JobAnalysisRoot',
    component: JobAnalysisLayout,
    redirect: '/job-analysis/boss',
    meta: navMetaByPath['/job-analysis'],
    children: [
      {
        path: 'boss',
        name: 'JobAnalysisBoss',
        component: JobAnalysis,
        meta: { title: 'BOSS直聘', requiresAuth: true, source: 'boss' }
      },
      {
        path: '51job',
        name: 'JobAnalysis51Job',
        component: JobAnalysis,
        meta: { title: '前程无忧', requiresAuth: true, source: '51job' }
      },
      {
        path: 'insight',
        name: 'CompanyInsight',
        component: CompanyInsight,
        meta: { title: '公司洞察', requiresAuth: true }
      }
    ]
  },
  {
    path: '/job-match',
    name: 'JobMatch',
    component: JobMatch,
    meta: navMetaByPath['/job-match']
  },
  {
    path: '/data-management',
    name: 'DataManagement',
    component: DataManagement,
    meta: navMetaByPath['/data-management']
  }
]
