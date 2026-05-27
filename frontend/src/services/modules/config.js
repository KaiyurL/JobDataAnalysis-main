import http, { requestApi } from '../http.js'

/**
 * 爬虫/采集配置相关接口（Config Domain）
 *
 * 说明：
 * - 配置结构由后端定义（关键词、城市、分页参数、平台选择等）；
 * - 前端在 DataManagement 页面做表单编辑与差异提示，本模块只负责请求。
 */

/**
 * 获取当前配置。
 *
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise
 */
export const getConfig = () => http.get('/config')

/**
 * 获取当前配置（已解包）。
 *
 * @returns {Promise<any>}
 */
export const getConfigData = () => requestApi(getConfig(), '加载配置失败')

/**
 * 更新配置（整包覆盖或增量由后端决定）。
 *
 * @param {Record<string, any>} config 配置对象（与后端配置结构保持一致）
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise
 */
export const updateConfig = (config) => http.post('/config', config)

/**
 * 更新配置（已解包）。
 *
 * @param {Record<string, any>} config
 * @returns {Promise<any>}
 */
export const updateConfigData = (config) => requestApi(updateConfig(config), '保存失败')
