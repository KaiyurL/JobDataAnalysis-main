import axios from 'axios'
import { clearAuth, getToken } from '@/shared/authStorage.js'

/**
 * HTTP 客户端（统一出口）
 *
 * 设计目标：
 * - 将 baseURL、超时、鉴权头注入、401 处理等横切逻辑统一收口；
 * - 上层 services/pages 只关心“调用哪个接口”，不重复实现拦截与跳转逻辑；
 * - 保持与后端约定的 /api 前缀一致（由 Vite dev proxy 与生产环境反向代理接入）。
 */
const http = axios.create({
  baseURL: '/api',
  timeout: 10000
})

/**
 * getToken/clearAuth 抽离到 shared/authStorage：
 * - 复用同一套 localStorage/sessionStorage 读写策略
 * - 避免 router、http、登录页出现行为漂移
 */

/**
 * 跳转到登录页。
 *
 * 设计选择：
 * - 使用 window.location.href 进行硬跳转，确保能清空所有运行时状态（路由缓存/页面状态）；
 * - 已在 /login 时不重复跳转，避免死循环刷新。
 */
const redirectToLogin = () => {
  if (typeof window === 'undefined') return
  if (window.location.pathname === '/login') return
  window.location.href = '/login'
}

/**
 * 请求拦截器：为所有请求注入 Authorization 头。
 *
 * 约定：
 * - 后端采用 Bearer token；
 * - 若无 token，则不注入该头，保持匿名请求可用（如登录/注册）。
 */
http.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers = config.headers || {}
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

/**
 * 响应拦截器：统一处理 401（未授权）。
 *
 * 关键分支：
 * - 仅在 status === 401 时清理鉴权信息并跳转登录页；
 * - 其它错误原样抛出，交由上层页面决定提示文案或重试策略。
 */
http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error?.response?.status === 401) {
      clearAuth()
      redirectToLogin()
    }
    return Promise.reject(error)
  }
)

/**
 * 统一解包后端 JSON 响应。
 *
 * 后端约定（当前项目）：
 * - res.data.code === 200 表示成功
 * - res.data.message 为失败原因（或提示信息）
 * - res.data.data 为业务数据载荷
 *
 * 注意：
 * - 本函数仅适用于 JSON 响应；对于 blob/arraybuffer 等二进制响应，不应使用该解包逻辑。
 *
 * @template T
 * @param {{ data?: { code?: number, message?: string, data?: T } }} res axios 响应对象
 * @param {string} [fallbackMessage='请求失败'] 兜底错误提示
 * @returns {T} 业务数据载荷
 * @throws {Error} 当 code 非 200 时抛出（message 优先）
 */
export const unwrapApiData = (res, fallbackMessage = '请求失败') => {
  const payload = res?.data || {}
  if (payload?.code === 200) return payload.data
  const msg = payload?.message || fallbackMessage
  throw new Error(String(msg))
}

/**
 * 统一请求 + 解包（JSON）。
 *
 * 使用方式：
 * - const data = await requestApi(api.getConfig())
 * - 页面/业务逻辑只拿 data，不再重复判断 code/message
 *
 * @template T
 * @param {Promise<any>} promise axios 请求 Promise
 * @param {string} [fallbackMessage] 兜底错误提示
 * @returns {Promise<T>}
 */
export const requestApi = async (promise, fallbackMessage) => {
  const res = await promise
  return unwrapApiData(res, fallbackMessage)
}

export default http
