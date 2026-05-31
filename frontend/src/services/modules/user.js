import http, { requestApi } from '../http.js'

/**
 * 用户侧接口（User Domain）
 *
 * 覆盖能力：
 * - 用户资料（profile）
 * - 收藏（favorites）
 * - 历史记录（匹配历史/浏览历史）
 *
 * 说明：
 * - 该模块只定义请求；收藏/历史在 UI 层的聚合与状态管理（busyKey 等）由 App.vue 提供的 userDataStore 负责。
 */

/**
 * 获取当前用户资料。
 *
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise
 */
export const getUserProfile = () => http.get('/user/profile')

/**
 * 获取当前用户资料（已解包）。
 *
 * @returns {Promise<any>}
 */
export const getUserProfileData = () => requestApi(getUserProfile(), '加载画像失败')

/**
 * 保存用户资料。
 *
 * @param {Record<string, any>} data 用户资料字段（结构由后端定义）
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise
 */
export const saveUserProfile = (data) => http.put('/user/profile', data)

/**
 * 保存用户资料（已解包）。
 *
 * @param {Record<string, any>} data
 * @returns {Promise<any>}
 */
export const saveUserProfileData = (data) => requestApi(saveUserProfile(data), '保存失败')

/**
 * 获取收藏列表。
 *
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise
 */
export const listFavorites = () => http.get('/user/favorites')

/**
 * 获取收藏列表（已解包）。
 *
 * @returns {Promise<Array<any>>}
 */
export const listFavoritesData = () => requestApi(listFavorites(), '加载收藏失败')

/**
 * 添加收藏。
 *
 * @param {{ sourceTable: string, job: Record<string, any> }} data
 * - sourceTable：来源表（如 job_info / job_info_51job）
 * - job：岗位详情对象（由页面传入，后端负责落库字段映射）
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise
 */
export const addFavorite = (data) => http.post('/user/favorites', data)

/**
 * 添加收藏（已解包）。
 *
 * @param {{ sourceTable: string, job: Record<string, any> }} data
 * @returns {Promise<any>}
 */
export const addFavoriteData = (data) => requestApi(addFavorite(data), '收藏失败')

/**
 * 取消收藏。
 *
 * @param {{ sourceTable: string, jobUrl: string }} params 查询参数（由页面传入）
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise
 */
export const removeFavorite = (params) => http.delete('/user/favorites', { params })

/**
 * 取消收藏（已解包）。
 *
 * @param {{ sourceTable: string, jobUrl: string }} params
 * @returns {Promise<any>}
 */
export const removeFavoriteData = (params) => requestApi(removeFavorite(params), '取消收藏失败')

/**
 * 获取浏览历史列表（可分页/可筛选）。
 *
 * @param {Record<string, any>} [params={}] 查询参数
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise
 */
export const listJobHistory = (params = {}) => http.get('/user/job-history', { params })

/**
 * 获取浏览历史列表（已解包）。
 *
 * @param {Record<string, any>} [params={}]
 * @returns {Promise<Array<any>>}
 */
export const listJobHistoryData = (params = {}) => requestApi(listJobHistory(params), '加载历史失败')

/**
 * 记录浏览历史（用户点击“查看详情”等行为触发）。
 *
 * @param {{ sourceTable: string, job: Record<string, any> }} data 写入数据
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise
 */
export const recordJobHistory = (data) => http.post('/user/job-history', data)

/**
 * 记录浏览历史（已解包）。
 *
 * @param {{ sourceTable: string, job: Record<string, any> }} data
 * @returns {Promise<any>}
 */
export const recordJobHistoryData = (data) => requestApi(recordJobHistory(data), '记录历史失败')
