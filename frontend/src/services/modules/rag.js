import http, { requestApi } from '../http.js'

/**
 * 检索增强（RAG）相关接口（RAG Domain）
 *
 * 目前仅包含：
 * - 重建岗位索引（可按来源/数量/是否重置等参数控制）
 */

/**
 * 重建岗位索引。
 *
 * @param {Record<string, any>} [params={}] 查询参数（如 source/limit/reset 等，由后端约定）
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise（可能耗时较长）
 */
export const reindexJobs = (params = {}) => http.post('/rag/reindex/jobs', null, { params, timeout: 15000 })

/**
 * 重建岗位索引（已解包）。
 *
 * @param {Record<string, any>} [params={}]
 * @returns {Promise<any>}
 */
export const reindexJobsData = (params = {}) => requestApi(reindexJobs(params), '重建失败')

export const reindexJobsAsync = (params = {}) => http.post('/rag/reindex/jobs/async', null, { params, timeout: 15000 })

export const reindexJobsAsyncData = (params = {}) => requestApi(reindexJobsAsync(params), '重建失败')

export const reindexJobsStatus = (params = {}) => http.get('/rag/reindex/jobs/status', { params, timeout: 15000 })

export const reindexJobsStatusData = (params = {}) => requestApi(reindexJobsStatus(params), '查询失败')
