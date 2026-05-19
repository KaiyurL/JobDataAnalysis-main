import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'
import JobAnalysis from '../views/JobAnalysis.vue'
import SkillAnalysis from '../views/SkillAnalysis.vue'
import SalaryPredict from '../views/SalaryPredict.vue'
import JobMatch from '../views/JobMatch.vue'
import CompanyInsight from '../views/CompanyInsight.vue'
import DataManagement from '../views/DataManagement.vue'
import Login from '../views/Login.vue'

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
    meta: { title: '仪表盘', requiresAuth: true }
  },
  {
    path: '/job-analysis',
    name: 'JobAnalysis',
    component: JobAnalysis,
    meta: { title: '岗位分析', requiresAuth: true }
  },
  {
    path: '/skill-analysis',
    name: 'SkillAnalysis',
    component: SkillAnalysis,
    meta: { title: '技能分析', requiresAuth: true }
  },
  {
    path: '/salary-predict',
    name: 'SalaryPredict',
    component: SalaryPredict,
    meta: { title: '薪资预测', requiresAuth: true }
  },
  {
    path: '/job-match',
    name: 'JobMatch',
    component: JobMatch,
    meta: { title: '岗位匹配', requiresAuth: true }
  },
  {
    path: '/company-insight',
    name: 'CompanyInsight',
    component: CompanyInsight,
    meta: { title: '公司洞察', requiresAuth: true }
  },
  {
    path: '/data-management',
    name: 'DataManagement',
    component: DataManagement,
    meta: { title: '数据管理', requiresAuth: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token') || sessionStorage.getItem('token')
  
  if (to.meta.requiresAuth && !token) {
    // 需要登录但没有token
    next('/login')
  } else if (to.path === '/login' && token) {
    // 已登录但访问登录页
    next('/dashboard')
  } else {
    next()
  }
})

export default router
