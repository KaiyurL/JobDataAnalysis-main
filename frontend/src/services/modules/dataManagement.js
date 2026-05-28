import http, { requestApi } from '../http.js'

/**
 * 数据管理相关接口（Data Management Domain）
 *
 * 覆盖能力：
 * - 采集/爬虫运行状态概览
 * - 触发数据更新、确认登录、清理日志
 *
 * @returns {import('axios').AxiosPromise<any>} 本模块各函数均返回 axios 响应 Promise
 */

/**
 * 获取数据概况（如总量、状态、最近运行时间等）。
 *
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise
 */
export const getDataOverview = () => http.get('/data/overview')

/**
 * 获取数据概况（已解包）。
 *
 * @returns {Promise<any>}
 */
export const getDataOverviewData = () => requestApi(getDataOverview(), '加载数据失败')

/**
 * 触发后端开始数据更新/爬取任务。
 *
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise
 */
export const startDataUpdate = () => http.post('/data/update')

/**
 * 启动数据更新（已解包）。
 *
 * @returns {Promise<any>}
 */
export const startDataUpdateData = () => requestApi(startDataUpdate(), '启动失败')

/**
 * 确认爬虫登录（用于需要人工扫码/二次确认的场景）。
 *
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise
 */
export const confirmCrawlerLogin = () => http.post('/data/confirm-login')

/**
 * 确认爬虫登录（已解包）。
 *
 * @returns {Promise<any>}
 */
export const confirmCrawlerLoginData = () => requestApi(confirmCrawlerLogin(), '确认失败')

export const stopDataUpdate = () => http.post('/data/stop')

export const stopDataUpdateData = () => requestApi(stopDataUpdate(), '停止失败')

/**
 * 清理后端采集日志（通常用于页面“清空日志”按钮）。
 *
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise
 */
export const clearCrawlerLogs = () => http.post('/data/logs/clear')

/**
 * 清理采集日志（已解包）。
 *
 * @returns {Promise<any>}
 */
export const clearCrawlerLogsData = () => requestApi(clearCrawlerLogs(), '清理日志失败')
