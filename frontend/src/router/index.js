import { createRouter, createWebHistory } from 'vue-router'

const Dashboard = () => import('../views/Dashboard.vue')
const JobAnalysisLayout = () => import('../views/JobAnalysisLayout.vue')
const JobAnalysis = () => import('../views/JobAnalysis.vue')
const SkillAnalysis = () => import('../views/SkillAnalysis.vue')
const SalaryPredict = () => import('../views/SalaryPredict.vue')
const JobMatch = () => import('../views/JobMatch.vue')
const CompanyInsight = () => import('../views/CompanyInsight.vue')
const DataManagement = () => import('../views/DataManagement.vue')
const Login = () => import('../views/Login.vue')

const routes = [
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
    meta: { title: '数据仪表盘', requiresAuth: true, icon: 'Odometer', keepAlive: true }
  },
  {
    path: '/job-analysis',
    name: 'JobAnalysisRoot',
    component: JobAnalysisLayout,
    redirect: '/job-analysis/boss',
    meta: { title: '岗位分析', requiresAuth: true, icon: 'Briefcase' },
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
        path: 'skills',
        name: 'SkillAnalysis',
        component: SkillAnalysis,
        meta: { title: '技能分析', requiresAuth: true }
      },
      {
        path: 'salary',
        name: 'SalaryPredict',
        component: SalaryPredict,
        meta: { title: '薪资预测', requiresAuth: true }
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
    meta: { title: '智能助手', requiresAuth: true, icon: 'User' }
  },
  {
    path: '/data-management',
    name: 'DataManagement',
    component: DataManagement,
    meta: { title: '系统设置', requiresAuth: true, icon: 'Setting', roles: ['admin'] }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token') || sessionStorage.getItem('token')
  const storedUserInfo = localStorage.getItem('userInfo') || sessionStorage.getItem('userInfo')
  const userInfo = storedUserInfo ? JSON.parse(storedUserInfo) : null
  const userRole = String(userInfo?.role || 'user').toLowerCase()
  
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/dashboard')
  } else if (Array.isArray(to.meta.roles) && to.meta.roles.length > 0 && !to.meta.roles.map(r => String(r).toLowerCase()).includes(userRole)) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
