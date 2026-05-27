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

  getUserProfile() {
    return api.get('/user/profile')
  },

  saveUserProfile(data) {
    return api.put('/user/profile', data)
  },

  listFavorites() {
    return api.get('/user/favorites')
  },

  addFavorite(data) {
    return api.post('/user/favorites', data)
  },

  removeFavorite(params) {
    return api.delete('/user/favorites', { params })
  },

  listMatchHistory(params = {}) {
    return api.get('/user/match-history', { params })
  },

  getMatchHistoryDetail(id) {
    return api.get(`/user/match-history/${id}`)
  },

  listJobHistory(params = {}) {
    return api.get('/user/job-history', { params })
  },

  recordJobHistory(data) {
    return api.post('/user/job-history', data)
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
  },

  reindexJobs(params = {}) {
    return api.post('/rag/reindex/jobs', null, { params, timeout: 120000 })
  }
}
