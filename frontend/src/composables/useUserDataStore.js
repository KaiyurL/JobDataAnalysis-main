import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { userApi } from '@/services/index.js'

/**
 * 用户数据聚合（收藏 / 历史 / 匹配历史）
 *
 * 设计目标：
 * - 将 App.vue 中与“用户数据”相关的状态与请求逻辑下沉为 composable，降低根组件体积；
 * - 通过 provide/inject 暴露“跨页面复用能力”（收藏判断/收藏切换/记录浏览历史等）；
 * - 通过 services/http.js 的 requestApi 做统一解包，页面逻辑只拿业务数据或错误信息。
 *
 * @param {{ isLoginPage: import('vue').ComputedRef<boolean> }} deps
 * @returns {Record<string, any>} 返回 refs/computed/actions（供 App.vue 直接绑定模板与 provide）
 */
export function useUserDataStore(deps) {
  const isLoginPage = deps?.isLoginPage

  const favoritesVisible = ref(false)
  const favoritesLoading = ref(false)
  const favoritesRaw = ref([])
  const favoriteBusyKey = ref('')
  const favoriteMap = ref({})

  const historyVisible = ref(false)
  const historyTab = ref('view')
  const jobHistoryLoading = ref(false)
  const jobHistoryRaw = ref([])

  /**
   * 生成收藏/历史的稳定 key。
   *
   * 约定：
   * - key 由 sourceTable + jobUrl 组合，确保同一岗位在同一来源下唯一；
   * - 两个字段均会 trim，避免后端写入空白导致前端无法命中。
   *
   * @param {string} sourceTable 来源表（如 job_info / job_info_51job）
   * @param {string} jobUrl 岗位链接（可能为完整 URL 或相对路径）
   * @returns {string}
   */
  const favoriteKeyOf = (sourceTable, jobUrl) => `${String(sourceTable || '').trim()}::${String(jobUrl || '').trim()}`

  /**
   * 针对页面卡片对象生成 key（用于 loading/busy 状态标记）。
   *
   * @param {{ sourceTable?: string, job?: { jobUrl?: string } }} card
   * @returns {string}
   */
  const favoriteKeyOfCard = (card) => favoriteKeyOf(card?.sourceTable, card?.job?.jobUrl)

  /**
   * 安全解析 JSON 字符串。
   *
   * @param {string} v JSON 字符串
   * @returns {any|null} 解析失败或空值时返回 null
   */
  const safeJsonParse = (v) => {
    if (!v) return null
    try {
      return JSON.parse(v)
    } catch {
      return null
    }
  }

  /**
   * 将后端返回的链接（可能为相对路径）规范化为可直接打开的 URL。
   *
   * 支持规则：
   * - 已包含 http/https：原样返回
   * - 以 // 开头：补齐 https:
   * - BOSS 相对路径：/job_detail/...
   * - 51job 相对路径：/...
   *
   * @param {string} url 原始链接
   * @returns {string} 可用 URL；不可用时返回 '#'
   */
  const normalizeJobUrl = (url) => {
    if (!url) return '#'
    const t = String(url).trim()
    if (!t) return '#'
    if (t.startsWith('http://') || t.startsWith('https://')) return t
    if (t.startsWith('//')) return `https:${t}`
    if (t.startsWith('/job_detail/')) return `https://www.zhipin.com${t}`
    if (t.startsWith('/')) return `https://jobs.51job.com${t}`
    return `https://${t}`
  }

  /**
   * 在新标签页打开岗位链接。
   *
   * 安全性：
   * - 使用 noopener/noreferrer，避免被打开页面获取 window.opener。
   *
   * @param {string} url 原始链接
   * @returns {void}
   */
  const openJobUrl = (url) => {
    const u = normalizeJobUrl(url)
    if (!u || u === '#') return
    window.open(u, '_blank', 'noopener,noreferrer')
  }

  /**
   * 将时间字段转为统一展示格式。
   *
   * @param {string|number|Date} v 时间值
   * @returns {string}
   */
  const formatTimeDisplay = (v) => {
    if (!v) return '—'
    const d = new Date(v)
    if (Number.isNaN(d.getTime())) return String(v)
    const pad = (n) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  }

  /**
   * 来源表 → 文案。
   *
   * @param {string} sourceTable
   * @returns {string}
   */
  const sourceLabel = (sourceTable) => {
    if (sourceTable === 'job_info_51job') return '51job'
    if (sourceTable === 'job_info') return 'Boss'
    return '未知'
  }

  /**
   * 来源表 → Tag 类型（用于区分颜色）。
   *
   * @param {string} sourceTable
   * @returns {'success'|'warning'|'info'}
   */
  const sourceTagType = (sourceTable) => {
    if (sourceTable === 'job_info_51job') return 'warning'
    if (sourceTable === 'job_info') return 'success'
    return 'info'
  }

  /**
   * 判断某岗位是否已收藏。
   *
   * @param {string} sourceTable
   * @param {string} jobUrl
   * @returns {boolean}
   */
  const isFavorite = (sourceTable, jobUrl) => {
    const key = favoriteKeyOf(sourceTable, jobUrl)
    if (!key || key === '::') return false
    return !!favoriteMap.value[key]
  }

  /**
   * 拉取收藏列表并建立快速查询 map。
   *
   * @param {boolean} silent 是否静默失败（不弹出错误提示）
   * @returns {Promise<void>}
   */
  const refreshFavorites = async (silent) => {
    favoritesLoading.value = true
    try {
      const list = await userApi.listFavoritesData()
      favoritesRaw.value = list
      const m = {}
      for (const f of list) {
        m[favoriteKeyOf(f.sourceTable, f.jobUrl)] = true
      }
      favoriteMap.value = m
    } catch (e) {
      if (!silent) ElMessage.error(String(e?.message || '加载收藏失败'))
    } finally {
      favoritesLoading.value = false
    }
  }

  /**
   * 将后端收藏记录统一映射为 UI 卡片模型。
   *
   * 关键分支：
   * - 优先使用 jobJson（若后端存了完整 JSON），否则回退到扁平字段；
   * - 生成稳定 key，避免列表渲染时出现重复 key。
   */
  const favoriteList = computed(() => {
    const out = []
    for (const f of favoritesRaw.value || []) {
      let job = safeJsonParse(f.jobJson)
      if (!job) {
        job = {
          id: f.jobId,
          jobName: f.jobName,
          companyName: f.companyName,
          city: f.city,
          jobUrl: f.jobUrl,
          salaryMin: f.salaryMin,
          salaryMax: f.salaryMax,
          experience: f.experience,
          education: f.education
        }
      }
      out.push({
        key: `${f.sourceTable || ''}::${f.jobUrl || ''}::fav::${f.id || ''}`,
        sourceTable: f.sourceTable,
        job
      })
    }
    return out
  })

  const refreshJobHistory = async (silent) => {
    jobHistoryLoading.value = true
    try {
      jobHistoryRaw.value = await userApi.listJobHistoryData({ size: 60 })
    } catch (e) {
      if (!silent) ElMessage.error(String(e?.message || '加载历史失败'))
    } finally {
      jobHistoryLoading.value = false
    }
  }

  const jobHistoryList = computed(() => {
    const out = []
    for (const h of jobHistoryRaw.value || []) {
      let job = safeJsonParse(h.jobJson)
      if (!job) {
        job = {
          jobName: h.jobName,
          companyName: h.companyName,
          city: h.city,
          jobUrl: h.jobUrl,
          salaryMin: h.salaryMin,
          salaryMax: h.salaryMax,
          experience: h.experience,
          education: h.education
        }
      }
      out.push({
        key: `${h.sourceTable || ''}::${h.jobUrl || ''}::his::${h.id || ''}`,
        id: h.id,
        sourceTable: h.sourceTable,
        job,
        updatedAt: h.updatedAt || h.createdAt
      })
    }
    return out
  })

  const deleteJobHistoryBatch = async (ids) => {
    const list = Array.isArray(ids) ? ids.map((x) => Number(x)).filter((x) => Number.isFinite(x) && x > 0) : []
    if (!list.length) return
    await userApi.batchDeleteJobHistoryData({ ids: list })
    await refreshJobHistory(true)
  }

  /**
   * 切换收藏状态（收藏/取消收藏）。
   *
   * 关键分支：
   * - 已收藏：调用 removeFavorite，并对本地 map/raw 做同步删除，减少一次全量 refresh；
   * - 未收藏：调用 addFavorite，成功后 refreshFavorites(true) 以拿到后端最新记录。
   *
   * @param {{ sourceTable?: string, job?: any }} cardLike 页面卡片对象（至少包含 sourceTable 与 job.jobUrl）
   * @returns {Promise<void>}
   */
  const toggleFavorite = async (cardLike) => {
    const sourceTable = String(cardLike?.sourceTable || '').trim()
    const jobUrl = String(cardLike?.job?.jobUrl || '').trim()
    if (!sourceTable || !jobUrl) return
    const key = favoriteKeyOf(sourceTable, jobUrl)
    favoriteBusyKey.value = key
    try {
      if (isFavorite(sourceTable, jobUrl)) {
        await userApi.removeFavoriteData({ sourceTable, jobUrl })
        const m = { ...favoriteMap.value }
        delete m[key]
        favoriteMap.value = m
        favoritesRaw.value = (favoritesRaw.value || []).filter((x) => favoriteKeyOf(x.sourceTable, x.jobUrl) !== key)
        ElMessage.success('已取消收藏')
      } else {
        await userApi.addFavoriteData({ sourceTable, job: cardLike.job })
        await refreshFavorites(true)
        ElMessage.success('已收藏')
      }
    } catch (e) {
      ElMessage.error(String(e?.message || '操作失败'))
    } finally {
      favoriteBusyKey.value = ''
    }
  }

  /**
   * 记录浏览历史（用于岗位详情弹窗打开等场景）。
   *
   * @param {string} sourceTable
   * @param {any} job
   * @returns {Promise<void>}
   */
  const recordJobHistory = async (sourceTable, job) => {
    const st = String(sourceTable || '').trim()
    const jobUrl = String(job?.jobUrl || job?.job_url || job?.url || '').trim()
    if (!st || !jobUrl) return
    try {
      const payloadJob = jobUrl === String(job?.jobUrl || '').trim() ? job : { ...(job || {}), jobUrl }
      await userApi.recordJobHistoryData({ sourceTable: st, job: payloadJob })
    } catch {}
  }

  const openFavorites = async () => {
    favoritesVisible.value = true
    await refreshFavorites(true)
  }

  const openHistory = async () => {
    historyVisible.value = true
    historyTab.value = 'view'
    await refreshJobHistory(true)
  }

  let disposed = false

  onMounted(async () => {
    if (isLoginPage?.value === false) {
      await refreshFavorites(true)
    }
  })

  watch(
    () => isLoginPage?.value,
    async (v) => {
      if (disposed) return
      if (v === false) {
        await refreshFavorites(true)
      }
    }
  )

  onUnmounted(() => {
    disposed = true
  })

  /**
   * 注入给业务页面的跨页能力（favorites + history record）。
   *
   * 说明：
   * - 保持历史页面对 inject('userDataStore') 的使用方式不变；
   * - 仅暴露必要接口，避免页面可以随意改动内部 raw 状态。
   */
  const providePayload = {
    isFavorite,
    toggleFavorite,
    favoriteBusyKey,
    openFavorites,
    openHistory,
    recordJobHistory,
    refreshFavorites
  }

  return {
    favoritesVisible,
    favoritesLoading,
    favoriteBusyKey,
    favoriteKeyOfCard,
    favoriteList,
    historyVisible,
    historyTab,
    jobHistoryLoading,
    jobHistoryList,
    openFavorites,
    openHistory,
    isFavorite,
    toggleFavorite,
    deleteJobHistoryBatch,
    openJobUrl,
    sourceLabel,
    sourceTagType,
    formatTimeDisplay,
    providePayload
  }
}
