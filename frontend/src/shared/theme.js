/**
 * CSS 变量读取工具。
 *
 * 说明：
 * - 以 document.documentElement 为根，读取 :root 上的 CSS custom properties；
 * - 在 SSR/非浏览器环境下返回 fallback；
 * - 统一收口后可避免各页面/逻辑模块重复实现 cssVar/theme，降低主题口径漂移风险。
 *
 * @param {string} name CSS 变量名（如 --c-primary-600）
 * @param {string} fallback 变量缺失时的兜底值
 * @returns {string}
 */
export const cssVar = (name, fallback) => {
  if (typeof window === 'undefined') return fallback
  try {
    const v = getComputedStyle(document.documentElement).getPropertyValue(name)
    const t = String(v || '').trim()
    return t || fallback
  } catch {
    return fallback
  }
}

/**
 * 基础主题色 token（覆盖当前项目中最常用的一组颜色变量）。
 *
 * @returns {{
 *  primary: string,
 *  primary2: string,
 *  accent: string,
 *  info: string,
 *  success: string,
 *  warning: string,
 *  danger: string
 * }}
 */
export const baseTheme = () => ({
  primary: cssVar('--c-primary-600', '#0f766e'),
  primary2: cssVar('--c-primary-500', '#14b8a6'),
  accent: cssVar('--c-accent-500', '#ff6b4a'),
  info: cssVar('--c-info', '#2563eb'),
  success: cssVar('--c-success', '#16a34a'),
  warning: cssVar('--c-warning', '#f59e0b'),
  danger: cssVar('--c-danger', '#dc2626')
})
