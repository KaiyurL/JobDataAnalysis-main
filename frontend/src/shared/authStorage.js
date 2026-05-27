/**
 * 鉴权信息读写（token / userInfo）
 *
 * 设计目标：
 * - 将 localStorage/sessionStorage 读写策略集中管理；
 * - 保持与现有登录逻辑一致：token/userInfo 可能存于 localStorage 或 sessionStorage；
 * - 让 router guard、菜单过滤等位置复用，避免重复实现与行为漂移。
 */

/**
 * 获取 token（兼容 localStorage 与 sessionStorage）。
 *
 * @returns {string|null}
 */
export const getToken = () => localStorage.getItem('token') || sessionStorage.getItem('token')

/**
 * 获取 userInfo（兼容 localStorage 与 sessionStorage）。
 *
 * @returns {any|null}
 */
export const getUserInfo = () => {
  const stored = localStorage.getItem('userInfo') || sessionStorage.getItem('userInfo')
  if (!stored) return null
  try {
    return JSON.parse(stored)
  } catch {
    return null
  }
}

/**
 * 获取用户角色（小写），用于权限判断。
 *
 * @returns {string}
 */
export const getUserRoleLower = () => {
  const userInfo = getUserInfo()
  return String(userInfo?.role || 'user').toLowerCase()
}

/**
 * 写入 token 与 userInfo（兼容“记住我”）。
 *
 * 约定：
 * - remember=true：写入 localStorage，并清理 sessionStorage 中同名字段
 * - remember=false：写入 sessionStorage，并清理 localStorage 中同名字段
 *
 * @param {{ token: string, userInfo: any, remember: boolean }} params
 * @returns {void}
 */
export const setAuth = ({ token, userInfo, remember }) => {
  const storage = remember ? localStorage : sessionStorage
  const other = remember ? sessionStorage : localStorage
  storage.setItem('token', token)
  storage.setItem('userInfo', JSON.stringify(userInfo))
  other.removeItem('token')
  other.removeItem('userInfo')
}

/**
 * 清理 token 与 userInfo（同时清理 localStorage 与 sessionStorage）。
 *
 * @returns {void}
 */
export const clearAuth = () => {
  localStorage.removeItem('token')
  sessionStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  sessionStorage.removeItem('userInfo')
}
