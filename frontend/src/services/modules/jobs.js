import http, { requestApi } from '../http.js'

/**
 * 岗位/统计相关接口（Jobs Domain）
 *
 * 说明：
 * - 本模块只负责“请求定义”，不在此处做页面展示层的数据加工；
 * - 同时提供 *Data 版本：直接返回业务数据（已统一解包），页面不再关心 code/message。
 */

/**
 * 获取岗位统计概览（BOSS 侧）。
 *
 * @param {Record<string, any>} [filters={}] 查询过滤条件（由页面拼装，如 keyword/city/education/experience 等）
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise
 */
export const getOverview = (filters = {}) => http.get('/jobs/stats/overview', { params: filters, timeout: 60000 })

/**
 * 获取岗位统计概览（BOSS 侧，已解包）。
 *
 * @param {Record<string, any>} [filters={}] 查询过滤条件
 * @returns {Promise<any>} 后端 data 字段（业务数据载荷）
 */
export const getOverviewData = (filters = {}) => requestApi(getOverview(filters), 'BOSS数据获取失败')

/**
 * 获取岗位统计概览（51job 侧）。
 *
 * @param {Record<string, any>} [filters={}] 查询过滤条件
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise
 */
export const getOverview51 = (filters = {}) => http.get('/jobs51/stats/overview', { params: filters, timeout: 60000 })

/**
 * 获取岗位统计概览（51job 侧，已解包）。
 *
 * @param {Record<string, any>} [filters={}] 查询过滤条件
 * @returns {Promise<any>} 后端 data 字段（业务数据载荷）
 */
export const getOverview51Data = (filters = {}) => requestApi(getOverview51(filters), '51job数据获取失败')

/**
 * 分页查询岗位列表（BOSS 侧）。
 *
 * @param {number} current 当前页码（后端分页从 1 开始）
 * @param {number} size 每页条数
 * @param {Record<string, any>} [filters={}] 过滤条件（会被展开到 query params）
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise
 */
export const getJobPage = (current, size, filters = {}) =>
  http.get('/jobs/page', {
    params: {
      current,
      size,
      ...filters
    }
  })

/**
 * 分页查询岗位列表（BOSS 侧，已解包）。
 *
 * @param {number} current 当前页码
 * @param {number} size 每页条数
 * @param {Record<string, any>} [filters={}] 过滤条件
 * @returns {Promise<{ records?: Array<any>, total?: number }>} 分页结果
 */
export const getJobPageData = (current, size, filters = {}) => requestApi(getJobPage(current, size, filters), '加载岗位列表失败')

/**
 * 分页查询岗位列表（51job 侧）。
 *
 * @param {number} current 当前页码
 * @param {number} size 每页条数
 * @param {Record<string, any>} [filters={}] 过滤条件
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise
 */
export const getJobPage51 = (current, size, filters = {}) =>
  http.get('/jobs51/page', {
    params: {
      current,
      size,
      ...filters
    }
  })

/**
 * 分页查询岗位列表（51job 侧，已解包）。
 *
 * @param {number} current 当前页码
 * @param {number} size 每页条数
 * @param {Record<string, any>} [filters={}] 过滤条件
 * @returns {Promise<{ records?: Array<any>, total?: number }>} 分页结果
 */
export const getJobPage51Data = (current, size, filters = {}) => requestApi(getJobPage51(current, size, filters), '加载岗位列表失败')

/**
 * 热门公司统计（用于公司热度图表）。
 *
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise
 */
export const getCompanyHotStats = () => http.get('/jobs/stats/company-hot')

/**
 * 热门公司统计（已解包）。
 *
 * @returns {Promise<Array<any>>} 列表数据
 */
export const getCompanyHotStatsData = () => requestApi(getCompanyHotStats(), '加载热门公司失败')

/**
 * 公司薪资统计（用于分布/对比图表）。
 *
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise
 */
export const getCompanySalaryStats = () => http.get('/jobs/stats/company-salary')

/**
 * 公司薪资统计（已解包）。
 *
 * @returns {Promise<Array<any>>} 列表数据
 */
export const getCompanySalaryStatsData = () => requestApi(getCompanySalaryStats(), '加载公司薪资排名失败')

/**
 * 公司规模统计（用于规模分布图表）。
 *
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise
 */
export const getCompanySizeStats = () => http.get('/jobs/stats/company-size')

/**
 * 公司规模统计（已解包）。
 *
 * @returns {Promise<Array<any>>} 列表数据
 */
export const getCompanySizeStatsData = () => requestApi(getCompanySizeStats(), '加载公司规模分布失败')
