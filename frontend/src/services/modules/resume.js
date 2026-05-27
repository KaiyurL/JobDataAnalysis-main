import http, { requestApi } from '../http.js'

/**
 * 简历解析相关接口（Resume Domain）
 *
 * 说明：
 * - 该接口通常需要上传文件，因此必须使用 multipart/form-data；
 * - 解析时间可能较长，单独设置较高的 timeout，避免被全局默认超时中断。
 */

/**
 * 上传简历文件并触发后端解析。
 *
 * @param {File} file 浏览器 File 对象（由 input/file 或拖拽上传获得）
 * @returns {import('axios').AxiosPromise<any>} axios 响应 Promise（解析结果结构由后端返回）
 */
export const parseResume = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/resume/parse', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    timeout: 60000
  })
}

/**
 * 上传简历并解析（已解包）。
 *
 * @param {File} file
 * @returns {Promise<any>} 解析结果
 */
export const parseResumeData = (file) => requestApi(parseResume(file), '解析失败')
