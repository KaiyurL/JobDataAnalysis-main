import http, { requestApi } from '../http.js'

/**
 * 认证相关接口（Auth Domain）
 *
 * 注意：
 * - 登录成功后的 token/userInfo 写入逻辑在 Login 页面内完成；
 * - http 客户端会在后续请求自动携带 token（见 src/services/http.js）。
 */

/**
 * 登录。
 *
 * @param {{ username: string, password: string }} data 登录表单数据
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise（通常包含 token 与 userInfo）
 */
export const login = (data) => http.post('/auth/login', data)

/**
 * 登录（已解包）。
 *
 * @param {{ username: string, password: string }} data 登录表单数据
 * @returns {Promise<{ token: string, userInfo: any }>} 登录结果
 */
export const loginData = (data) => requestApi(login(data), '登录失败')

/**
 * 注册。
 *
 * @param {{ username: string, password: string }} data 注册表单数据
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise
 */
export const register = (data) => http.post('/auth/register', data)

/**
 * 注册（已解包）。
 *
 * @param {{ username: string, password: string }} data 注册表单数据
 * @returns {Promise<any>} 注册结果（结构由后端定义）
 */
export const registerData = (data) => requestApi(register(data), '注册失败')
