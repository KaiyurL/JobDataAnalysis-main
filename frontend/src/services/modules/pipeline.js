import http, { requestApi } from '../http.js'

/**
 * 数据流水线（pipeline）相关接口（Pipeline Domain）
 *
 * 涵盖：
 * - 触发 Dashboard / Stats 两类流水线
 * - 查询运行状态
 * - 获取产物列表与产物文件（blob）
 *
 * 关键点：
 * - 流水线运行可能耗时，统一设置较长 timeout；
 * - 产物文件以 blob 返回，上层需要自行 URL.createObjectURL 或 text()/JSON.parse() 处理。
 */

/**
 * 启动 Dashboard 流水线（默认策略：由后端判断是否需要运行）。
 *
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise
 */
export const runDashboardPipeline = () => http.post('/pipeline/dashboard/run', {}, { timeout: 60000 })

/**
 * 启动 Dashboard 流水线（已解包）。
 *
 * @returns {Promise<any>} 返回 data（例如 cached/message/runDir 等，由后端定义）
 */
export const runDashboardPipelineData = () => requestApi(runDashboardPipeline(), '扫描失败')

/**
 * 强制启动 Dashboard 流水线（忽略后端的“无需运行”判断）。
 *
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise
 */
export const runDashboardPipelineForce = () => http.post('/pipeline/dashboard/run', {}, { params: { force: true }, timeout: 60000 })

/**
 * 强制启动 Dashboard 流水线（已解包）。
 *
 * @returns {Promise<any>}
 */
export const runDashboardPipelineForceData = () => requestApi(runDashboardPipelineForce(), '强制扫描失败')

/**
 * 启动 Stats 流水线。
 *
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise
 */
export const runStatsPipeline = () => http.post('/pipeline/stats/run', {}, { timeout: 60000 })

/**
 * 启动 Stats 流水线（已解包）。
 *
 * @returns {Promise<any>}
 */
export const runStatsPipelineData = () => requestApi(runStatsPipeline(), 'Top Tokens 刷新失败')

/**
 * 强制启动 Stats 流水线。
 *
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise
 */
export const runStatsPipelineForce = () => http.post('/pipeline/stats/run', {}, { params: { force: true }, timeout: 60000 })

/**
 * 强制启动 Stats 流水线（已解包）。
 *
 * @returns {Promise<any>}
 */
export const runStatsPipelineForceData = () => requestApi(runStatsPipelineForce(), 'Top Tokens 刷新失败')

/**
 * 获取 pipeline 当前状态（是否运行中、最近一次结果等）。
 *
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise
 */
export const getPipelineStatus = () => http.get('/pipeline/status', { timeout: 60000 })

/**
 * 获取 pipeline 状态（已解包）。
 *
 * @returns {Promise<any>}
 */
export const getPipelineStatusData = () => requestApi(getPipelineStatus(), '获取分析状态失败')

/**
 * 获取 pipeline 产物索引（summary/errors/artifacts keys 等）。
 *
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise
 */
export const getPipelineArtifacts = () => http.get('/pipeline/artifacts', { timeout: 60000 })

/**
 * 获取 pipeline 产物索引（已解包）。
 *
 * @returns {Promise<any>}
 */
export const getPipelineArtifactsData = () => requestApi(getPipelineArtifacts(), '加载分析产物失败')

/**
 * 按 key 获取某个产物文件。
 *
 * @param {string} key 产物标识（由 getPipelineArtifacts 返回）
 * @returns {import('axios').AxiosPromise<Blob>} axios 响应 Promise（response.data 为 Blob）
 */
export const getPipelineFile = (key) => http.get('/pipeline/file', { params: { key }, responseType: 'blob', timeout: 60000 })
