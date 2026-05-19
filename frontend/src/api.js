import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 请求拦截器 - 添加 Authorization 头
api.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token') || sessionStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器 - 处理 401 错误
api.interceptors.response.use(
  response => {
    return response
  },
  error => {
    if (error.response && error.response.status === 401) {
      // 清除token
      localStorage.removeItem('token')
      sessionStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      sessionStorage.removeItem('userInfo')
      // 跳转到登录页
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default {
  getOverview(filters = {}) {
    return api.get('/jobs/stats/overview', { params: filters })
  },
  
  getCitySalary(filters = {}) {
    return api.get('/jobs/stats/city-salary', { params: filters })
  },
  
  getEducationSalary(filters = {}) {
    return api.get('/jobs/stats/education-salary', { params: filters })
  },
  
  getExperienceSalary(filters = {}) {
    return api.get('/jobs/stats/experience-salary', { params: filters })
  },
  
  getKeywords(filters = {}) {
    return api.get('/jobs/stats/keywords', { params: filters })
  },
  
  getIndustry(filters = {}) {
    return api.get('/jobs/stats/industry', { params: filters })
  },
  
  getJobPage(current, size, filters = {}) {
    return api.get('/jobs/page', {
      params: {
        current,
        size,
        ...filters
      }
    })
  },

  predictSalary(data) {
    return api.post('/jobs/predict/salary', data)
  },

  matchJobs(data) {
    return api.post('/jobs/match/jobs', data)
  },

  getAllSkills() {
    return api.get('/jobs/skills')
  },

  getAllSkillsSorted() {
    return api.get('/jobs/skills/all')
  },

  getCompanyHotStats() {
    return api.get('/jobs/stats/company-hot')
  },

  getCompanySalaryStats() {
    return api.get('/jobs/stats/company-salary')
  },

  getCompanySizeStats() {
    return api.get('/jobs/stats/company-size')
  },

  // 数据管理相关
  getDataOverview() {
    return api.get('/data/overview')
  },

  startDataUpdate() {
    return api.post('/data/update')
  },

  // 配置管理相关
  getConfig() {
    return api.get('/config')
  },

  updateConfig(config) {
    return api.post('/config', config)
  },

  // 认证相关
  login(data) {
    return api.post('/auth/login', data)
  },

  register(data) {
    return api.post('/auth/register', data)
  },

  checkAuth() {
    return api.get('/auth/check')
  },

  logout() {
    return api.post('/auth/logout')
  },

  getUserInfo() {
    return api.get('/user/info')
  }
}
