import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { jobsApi } from '@/services/index.js'

/**
 * 仪表盘市场数据（概览/统计）逻辑聚合（Dashboard Market Data）
 *
 * 覆盖范围：
 * - 根据筛选条件拉取 BOSS 与 51job 的统计概览
 * - 兼容单侧失败：一侧成功仍可渲染；双侧失败则给出错误提示
 * - 对齐数据结构：城市/学历/经验采用“按 count 加权”的均值融合；行业采用 count 融合
 *
 * 设计目标：
 * - 将 Dashboard.vue 中“数据获取 + 融合计算 + 异常处理”抽离，降低页面脚本复杂度；
 * - 不改变现有后端协议（仍返回 axios response，由上层按 code/message/data 解析）；
 * - 将加载态与错误提示集中管理，页面只在合适时机触发图表更新。
 */
export function useDashboardMarketData() {
  const loading = ref(false)

  const totalJobs = ref(0)
  const avgMaxSalary = ref(0)
  const topCity = ref('-')

  const cityData = ref([])
  const eduData = ref([])
  const expData = ref([])
  const industryData = ref([])
  const keywordData = ref([])

  const cityOptions = ref([])

  const filters = ref({
    keyword: '',
    selectedCities: [],
    education: '',
    experience: ''
  })

  /**
   * 将页面筛选条件转换为后端接受的 query params。
   *
   * 关键点：
   * - 空值转为 undefined，避免后端误判为“显式传参”；
   * - 多城市采用逗号分隔字符串（与后端约定一致）。
   */
  const getApiFilters = () => ({
    keyword: filters.value.keyword || undefined,
    city: filters.value.selectedCities.length > 0 ? filters.value.selectedCities.join(',') : undefined,
    education: filters.value.education || undefined,
    experience: filters.value.experience || undefined
  })

  /**
   * 将两侧统计列表进行“加权均值融合”。
   *
   * 适用场景：
   * - 城市/学历/经验 维度：每个 key 有 count 与 avgSalary；
   * - 融合后的 avgSalary 需按 count 加权，避免直接平均导致偏差。
   *
   * @param {Array<any>} listA 数据源 A
   * @param {Array<any>} listB 数据源 B
   * @param {string} keyField 用作分组的 key 字段名（如 city/education/experience）
   * @returns {Array<any>} 融合后的列表
   */
  const mergeAvgStatList = (listA = [], listB = [], keyField) => {
    const merged = new Map()
    const add = (item) => {
      if (!item) return
      const key = item[keyField]
      if (!key) return
      const count = Number(item.count || 0)
      const avgSalary = Number(item.avgSalary || 0)
      const prev = merged.get(key) || { key, count: 0, sumSalary: 0 }
      merged.set(key, {
        key,
        count: prev.count + count,
        sumSalary: prev.sumSalary + avgSalary * count
      })
    }
    ;(Array.isArray(listA) ? listA : []).forEach(add)
    ;(Array.isArray(listB) ? listB : []).forEach(add)
    return Array.from(merged.values()).map((v) => ({
      [keyField]: v.key,
      count: v.count,
      avgSalary: v.count > 0 ? Math.round((v.sumSalary / v.count) * 100) / 100 : 0
    }))
  }

  /**
   * 将两侧统计列表进行“计数融合”（同 key 的 count 相加）。
   *
   * 适用场景：
   * - 行业分布：只有 count，无均值字段。
   *
   * @param {Array<any>} listA 数据源 A
   * @param {Array<any>} listB 数据源 B
   * @param {string} keyField 用作分组的 key 字段名（如 industry）
   * @returns {Array<any>} 融合后的列表
   */
  const mergeCountList = (listA = [], listB = [], keyField) => {
    const merged = new Map()
    const add = (item) => {
      if (!item) return
      const key = item[keyField]
      if (!key) return
      merged.set(key, (merged.get(key) || 0) + Number(item.count || 0))
    }
    ;(Array.isArray(listA) ? listA : []).forEach(add)
    ;(Array.isArray(listB) ? listB : []).forEach(add)
    return Array.from(merged.entries()).map(([key, count]) => ({ [keyField]: key, count }))
  }

  /**
   * 计算顶部关键指标（高薪聚集地/市场均薪）。
   *
   * 规则：
   * - topCity：按平均薪资最高的城市
   * - avgMaxSalary：所有城市 avgSalary 的均值（保留 1 位小数）
   */
  const calculateStats = () => {
    if (cityData.value.length) {
      topCity.value = [...cityData.value].sort((a, b) => Number(b.avgSalary || 0) - Number(a.avgSalary || 0))[0]?.city || '-'
    }
    const salaries = cityData.value.map((d) => Number(d.avgSalary || 0))
    avgMaxSalary.value = salaries.length ? Math.round((salaries.reduce((a, b) => a + b, 0) / salaries.length) * 10) / 10 : 0
  }

  /**
   * 拉取并融合两侧市场统计数据。
   *
   * 关键分支：
   * - 双侧都失败：抛出错误并提示；保持页面不会误用旧数据；
   * - 单侧失败：使用成功侧数据继续渲染，同时给出 warning，提示某侧暂不可用。
   *
   * @returns {Promise<void>}
   */
  const loadAllData = async () => {
    loading.value = true
    try {
      const filtersToSend = getApiFilters()
      const [bossRes, job51Res] = await Promise.allSettled([
        jobsApi.getOverviewData(filtersToSend),
        jobsApi.getOverview51Data(filtersToSend)
      ])

      const bossOk = bossRes.status === 'fulfilled'
      const job51Ok = job51Res.status === 'fulfilled'
      const boss = bossOk ? bossRes.value || {} : {}
      const job51 = job51Ok ? job51Res.value || {} : {}

      if (!bossOk && !job51Ok) {
        const msg = bossRes.status === 'rejected' ? bossRes.reason?.message || 'BOSS数据获取失败' : 'BOSS数据获取失败'
        const msg2 = job51Res.status === 'rejected' ? job51Res.reason?.message || '51job数据获取失败' : '51job数据获取失败'
        throw new Error(`${msg}; ${msg2}`)
      }
      if (bossOk && !job51Ok) {
        const msg2 = job51Res.status === 'rejected' ? job51Res.reason?.message || '51job数据获取失败' : '51job数据获取失败'
        ElMessage.warning(`51job 暂未统计：${msg2}`)
      }
      if (!bossOk && job51Ok) {
        const msg = bossRes.status === 'rejected' ? bossRes.reason?.message || 'BOSS数据获取失败' : 'BOSS数据获取失败'
        ElMessage.warning(`BOSS 暂未统计：${msg}`)
      }

      totalJobs.value = Number(boss.total || 0) + Number(job51.total || 0)

      cityData.value = mergeAvgStatList(boss.citySalary || [], job51.citySalary || [], 'city').sort(
        (a, b) => Number(b.avgSalary || 0) - Number(a.avgSalary || 0)
      )

      const getExpWeight = (exp) => {
        if (!exp) return 0
        if (exp.includes('应届') || exp.includes('在校')) return 1
        if (exp.includes('无需') || exp.includes('不限')) return 2
        const match = exp.match(/(\d+)/)
        if (match) return parseInt(match[1]) + 3
        return 99
      }

      eduData.value = mergeAvgStatList(boss.educationSalary || [], job51.educationSalary || [], 'education')
      expData.value = mergeAvgStatList(boss.experienceSalary || [], job51.experienceSalary || [], 'experience').sort(
        (a, b) => getExpWeight(a.experience) - getExpWeight(b.experience)
      )

      industryData.value = mergeCountList(boss.industry || [], job51.industry || [], 'industry')
        .sort((a, b) => Number(b.count || 0) - Number(a.count || 0))
        .slice(0, 10)

      keywordData.value = (Array.isArray(boss.keywords) ? boss.keywords : [])
        .sort((a, b) => Number(b.count || 0) - Number(a.count || 0))
        .slice(0, 50)

      cityOptions.value = [...new Set((cityData.value || []).map((d) => d.city).filter(Boolean))].sort()

      calculateStats()
    } catch (e) {
      ElMessage.error(e?.message ? `获取市场数据失败：${e.message}` : '获取市场数据失败')
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    totalJobs,
    avgMaxSalary,
    topCity,
    cityData,
    eduData,
    expData,
    industryData,
    keywordData,
    cityOptions,
    filters,
    loadAllData
  }
}
