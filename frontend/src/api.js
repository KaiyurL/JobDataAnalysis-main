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
    return api.get('/jobs/stats/overview', { params: filters, timeout: 60000 })
  },

  getOverview51(filters = {}) {
    return api.get('/jobs51/stats/overview', { params: filters, timeout: 60000 })
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

  getJobPage51(current, size, filters = {}) {
    return api.get('/jobs51/page', {
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
    return api.post('/jobs/match/jobs', data, { timeout: 60000 })
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

  confirmCrawlerLogin() {
    return api.post('/data/confirm-login')
  },

  clearCrawlerLogs() {
    return api.post('/data/logs/clear')
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
  },

  careerChat(data) {
    return api.post('/ai/career-chat', data, { timeout: 60000 })
  },

  parseResume(file) {
    const formData = new FormData()
    formData.append('file', file)
    return api.post('/resume/parse', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      },
      timeout: 60000
    })
  },

  runDashboardPipeline() {
    return api.post('/pipeline/dashboard/run', {}, { timeout: 60000 })
  },

  runDashboardPipelineForce() {
    return api.post('/pipeline/dashboard/run', {}, { params: { force: true }, timeout: 60000 })
  },

  runStatsPipeline() {
    return api.post('/pipeline/stats/run', {}, { timeout: 60000 })
  },

  runStatsPipelineForce() {
    return api.post('/pipeline/stats/run', {}, { params: { force: true }, timeout: 60000 })
  },

  getPipelineStatus() {
    return api.get('/pipeline/status', { timeout: 60000 })
  },

  getPipelineArtifacts() {
    return api.get('/pipeline/artifacts', { timeout: 60000 })
  },

  getPipelineFile(key) {
    return api.get('/pipeline/file', { params: { key }, responseType: 'blob', timeout: 60000 })
  }
}
