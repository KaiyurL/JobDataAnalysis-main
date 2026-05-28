import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { configApi, dataManagementApi, ragApi } from '@/services/index.js'

/**
 * 数据管理页逻辑聚合（DataManagement）
 *
 * 设计目标：
 * - 将 DataManagement.vue 中体量较大的“状态 + 请求 + 轮询 + 表单操作”收敛为 composable；
 * - 页面组件仅保留模板/样式与少量组装逻辑，降低单文件复杂度；
 * - 不改变现有接口入参/返回值解析方式，确保功能行为一致。
 *
 * 返回值说明：
 * - 返回一组 refs/computed/functions，供 DataManagement.vue 直接绑定模板使用；
 * - 轮询 timer、按钮禁用 timer 的创建与清理均在本 composable 内部完成。
 */
export function useDataManagement() {
  const loading = ref(false)
  const updating = ref(false)
  const savingConfig = ref(false)
  const buttonDisabled = ref(false)
  const overview = ref({})
  const logs = ref([])
  const logContainer = ref(null)
  const newKeyword = ref('')
  const selectedCity = ref('')
  const newCityCodeName = ref('')
  const newCityCodeValue = ref('')

  // 配置相关
  const config = ref({
    platform: 'boss',
    browser: 'auto',
    keywords: [],
    cities: [],
    pages_per_keyword: 2,
    pages_per_city_51job: 2,
    delay_min: 3,
    delay_max: 8,
    city_codes_51job: {}
  })
  const originalConfig = ref({})

  const allCities = [
    '北京',
    '上海',
    '广州',
    '深圳',
    '杭州',
    '成都',
    '武汉',
    '西安',
    '重庆',
    '南京',
    '苏州',
    '天津',
    '郑州',
    '长沙',
    '青岛',
    '大连',
    '厦门',
    '宁波',
    '无锡',
    '合肥',
    '福州',
    '济南',
    '昆明',
    '南昌',
    '哈尔滨',
    '沈阳',
    '长春',
    '石家庄',
    '太原',
    '郑州'
  ]

  const defaultCityCodes51Job = {
    北京: '010000',
    上海: '020000',
    广州: '030200',
    深圳: '040000',
    杭州: '080200',
    苏州: '070300',
    南京: '070200',
    成都: '090200',
    武汉: '180200',
    西安: '200200',
    重庆: '060000',
    天津: '050000',
    郑州: '170200',
    长沙: '190200',
    青岛: '120200',
    大连: '230200',
    厦门: '110200',
    宁波: '080300',
    无锡: '070400',
    合肥: '150200',
    福州: '110300'
  }

  let pollTimer = null
  let disabledTimer = null
  let currentPollInterval = 5000

  const statusMap = {
    idle: { type: 'success', text: '空闲' },
    running: { type: 'warning', text: '运行中' },
    failed: { type: 'danger', text: '失败' }
  }

  /**
   * 将后端 status 映射为 Element Plus Tag 的 type。
   *
   * @param {string} status 后端状态值（idle/running/failed 等）
   * @returns {string} el-tag type（success/warning/danger/info）
   */
  const getStatusType = (status) => statusMap[status]?.type || 'info'

  /**
   * 将后端 status 映射为中文展示文案。
   *
   * @param {string} status 后端状态值
   * @returns {string} 展示文本
   */
  const getStatusText = (status) => statusMap[status]?.text || '未知'

  /**
   * 是否存在未保存的配置变更。
   *
   * 说明：
   * - 采用 JSON stringify 做深比较，适用于当前配置结构（纯对象/数组/数字/字符串）；
   * - 若未来配置变得更复杂（含函数/日期等），需替换为更严格的深比较。
   */
  const configChanged = computed(() => JSON.stringify(config.value) !== JSON.stringify(originalConfig.value))

  /**
   * 深拷贝工具（用于冻结“原始配置快照”，避免引用导致比较失效）。
   *
   * @template T
   * @param {T} obj 需要拷贝的对象
   * @returns {T} 深拷贝结果
   */
  const deepClone = (obj) => JSON.parse(JSON.stringify(obj))

  /**
   * 是否展示“我已登录，继续爬取”按钮。
   *
   * 业务含义：
   * - 爬虫处于 running 状态，且后端标记 waitingForLogin（需要人工登录/验证）。
   */
  const canConfirmLogin = computed(() => overview.value?.status === 'running' && overview.value?.waitingForLogin === true)
  const canStop = computed(() => overview.value?.status === 'running')

  /**
   * 根据当前配置估算“预计请求次数”（用于预览展示）。
   *
   * 规则：
   * - boss：关键词数 * 城市数 * pages_per_keyword
   * - 51job：关键词数 * 城市数 * pages_per_city_51job
   * - both：两者相加
   */
  const expectedRequests = computed(() => {
    const kw = config.value.keywords?.length || 0
    const ct = config.value.cities?.length || 0
    const boss = kw * ct * (config.value.pages_per_keyword || 0)
    const job51 = kw * ct * (config.value.pages_per_city_51job || 0)
    if (config.value.platform === 'both') return boss + job51
    if (config.value.platform === '51job') return job51
    return boss
  })

  /**
   * 清空后端日志，并同步清空前端展示。
   *
   * @returns {void}
   */
  const clearLogs = () => {
    dataManagementApi.clearCrawlerLogsData().finally(() => {
      logs.value = []
    })
  }

  const reindexing = ref(false)
  const reindexResult = ref(null)

  /**
   * 重建向量索引（供 RAG 检索使用）。
   *
   * 关键点：
   * - 该操作可能耗时较长，因此使用 loading 状态；
   * - 成功时从 res.data.data.documents 读取索引数量（无则兜底为 0）。
   *
   * @returns {Promise<void>}
   */
  const handleReindex = async () => {
    reindexing.value = true
    reindexResult.value = null
    try {
      const data = await ragApi.reindexJobsData({ source: 'all', limit: 0, reset: true })
      reindexResult.value = data.documents || 0
      ElMessage.success(`向量索引已重建，共索引 ${data.documents || 0} 个岗位`)
    } catch (e) {
      console.error(e)
      const msg = e?.response?.data?.message || e?.message || '重建索引失败'
      ElMessage.error('重建索引失败: ' + msg)
    } finally {
      reindexing.value = false
    }
  }

  // 配置管理
  /**
   * 拉取后端配置，并将缺省字段补齐为可用默认值。
   *
   * 关键分支：
   * - 若后端未返回 city_codes_51job 或为空，则注入 defaultCityCodes51Job，避免 UI 表格空数据导致难编辑；
   * - platform/browser/pages_per_city_51job 均做兜底，兼容旧配置。
   *
   * @returns {Promise<void>}
   */
  const loadConfig = async () => {
    try {
      const incomingRaw = await configApi.getConfigData()
      const incoming = { ...(incomingRaw || {}) }
      incoming.platform = incoming.platform || 'boss'
      incoming.browser = incoming.browser || 'auto'
      incoming.pages_per_city_51job = incoming.pages_per_city_51job || 2
      const codes = incoming.city_codes_51job || {}
      incoming.city_codes_51job = Object.keys(codes).length > 0 ? codes : { ...defaultCityCodes51Job }
      config.value = deepClone(incoming)
      originalConfig.value = deepClone(incoming)
    } catch (e) {
      console.error('加载配置失败', e)
    }
  }

  /**
   * 保存当前配置到后端，并以返回结果为准刷新本地快照。
   *
   * @returns {Promise<void>}
   */
  const saveConfig = async () => {
    savingConfig.value = true
    try {
      const incomingRaw = await configApi.updateConfigData(config.value)
      const incoming = { ...(incomingRaw || {}) }
      incoming.platform = incoming.platform || 'boss'
      incoming.browser = incoming.browser || 'auto'
      incoming.pages_per_city_51job = incoming.pages_per_city_51job || 2
      const codes = incoming.city_codes_51job || {}
      incoming.city_codes_51job = Object.keys(codes).length > 0 ? codes : { ...defaultCityCodes51Job }
      config.value = deepClone(incoming)
      originalConfig.value = deepClone(incoming)
      ElMessage.success('配置保存成功！')
    } catch (e) {
      console.error('保存配置失败', e)
      ElMessage.error('保存配置失败')
    } finally {
      savingConfig.value = false
    }
  }

  /**
   * 将配置恢复为前端内置默认值（不自动保存）。
   *
   * 说明：
   * - 该操作只修改前端 state；
   * - 用户需要手动点击“保存配置”将默认值写回后端。
   */
  const resetConfig = () => {
    config.value = {
      platform: 'boss',
      browser: 'auto',
      keywords: ['Java', 'Python', '前端', '数据分析', '产品经理'],
      cities: ['北京', '上海', '广州', '深圳', '杭州'],
      pages_per_keyword: 2,
      pages_per_city_51job: 2,
      delay_min: 3,
      delay_max: 8,
      city_codes_51job: { ...defaultCityCodes51Job }
    }
  }

  // 关键词管理
  /**
   * 添加关键词（去重、去空白）。
   *
   * @returns {void}
   */
  const addKeyword = () => {
    const keyword = newKeyword.value.trim()
    if (keyword && !config.value.keywords.includes(keyword)) {
      config.value.keywords.push(keyword)
      newKeyword.value = ''
    }
  }

  /**
   * 删除指定索引的关键词。
   *
   * @param {number} index 关键词索引
   * @returns {void}
   */
  const removeKeyword = (index) => {
    config.value.keywords.splice(index, 1)
  }

  // 城市管理
  /**
   * 添加城市（从下拉选择，去重）。
   *
   * @returns {void}
   */
  const addCity = () => {
    const city = selectedCity.value
    if (city && !config.value.cities.includes(city)) {
      config.value.cities.push(city)
      selectedCity.value = ''
    }
  }

  /**
   * 删除指定索引的城市。
   *
   * @param {number} index 城市索引
   * @returns {void}
   */
  const removeCity = (index) => {
    config.value.cities.splice(index, 1)
  }

  /**
   * 将 city_codes_51job 转为表格数据行，按中文排序便于查找。
   */
  const cityCodeRows = computed(() => {
    const obj = config.value.city_codes_51job || {}
    return Object.keys(obj)
      .sort((a, b) => a.localeCompare(b, 'zh-CN'))
      .map((name) => ({ name, code: obj[name] }))
  })

  /**
   * 新增或更新 51job 城市编码映射（用于 jobArea 参数）。
   *
   * @param {string} name 城市名
   * @param {string} code 城市编码
   * @returns {void}
   */
  const upsertCityCode = (name, code) => {
    const n = String(name || '').trim()
    const c = String(code || '').trim()
    if (!n || !c) return
    if (!config.value.city_codes_51job) config.value.city_codes_51job = {}
    config.value.city_codes_51job[n] = c
  }

  /**
   * 从输入框新增城市编码映射，并清空输入框。
   *
   * @returns {void}
   */
  const addCityCode = () => {
    upsertCityCode(newCityCodeName.value, newCityCodeValue.value)
    newCityCodeName.value = ''
    newCityCodeValue.value = ''
  }

  /**
   * 删除指定城市的 51job 编码。
   *
   * @param {string} name 城市名
   * @returns {void}
   */
  const removeCityCode = (name) => {
    if (config.value.city_codes_51job && Object.prototype.hasOwnProperty.call(config.value.city_codes_51job, name)) {
      delete config.value.city_codes_51job[name]
    }
  }

  /**
   * 加载运行状态、关键词统计、日志等数据，并自动滚动到日志底部。
   *
   * @returns {Promise<void>}
   */
  const loadData = async () => {
    loading.value = true
    try {
      const data = await dataManagementApi.getDataOverviewData()
      overview.value = data
      logs.value = Array.isArray(data.logs) ? data.logs : []
      setTimeout(() => {
        if (logContainer.value) {
          logContainer.value.scrollTop = logContainer.value.scrollHeight
        }
      }, 0)
    } catch (e) {
      console.error('加载数据失败', e)
    } finally {
      loading.value = false
    }
  }

  /**
   * 向后端发送“已登录，可以继续”的确认信号。
   *
   * 关键分支：
   * - 若 payload.success === false，说明当前无需确认，展示 warning 并直接返回；
   * - 成功后刷新页面数据。
   *
   * @returns {Promise<void>}
   */
  const confirmLogin = async () => {
    try {
      const payload = await dataManagementApi.confirmCrawlerLoginData()
      if (payload?.success === false) {
        ElMessage.warning(payload.message || '当前无需确认')
        return
      }
      ElMessage.success(payload?.message || '已发送继续信号')
      await loadData()
    } catch (e) {
      console.error('确认登录失败', e)
      ElMessage.error('确认登录失败')
    }
  }

  const stopping = ref(false)

  const stopUpdate = async () => {
    stopping.value = true
    try {
      const payload = await dataManagementApi.stopDataUpdateData()
      if (payload?.success === false) {
        ElMessage.warning(payload.message || '当前无法停止')
        return
      }
      ElMessage.success(payload?.message || '已请求停止')
      await loadData()
    } catch (e) {
      console.error('停止失败', e)
      ElMessage.error('停止失败')
    } finally {
      stopping.value = false
    }
  }

  /**
   * 启动数据更新（爬虫任务）。
   *
   * 关键分支：
   * - 若按钮处于禁用窗口（30 秒）则直接忽略，防止重复触发；
   * - 若存在未保存配置变更，则阻止启动，并提示先保存；
   * - 成功启动后将按钮禁用 30 秒，并刷新一次数据。
   *
   * @returns {Promise<void>}
   */
  const startUpdate = async () => {
    if (buttonDisabled.value) return

    if (configChanged.value) {
      ElMessage.warning('请先保存配置再启动爬虫！')
      return
    }

    updating.value = true
    try {
      const payload = await dataManagementApi.startDataUpdateData()
      ElMessage.success(payload?.message || '更新任务已启动')
      await loadData()
      buttonDisabled.value = true
      disabledTimer = setTimeout(() => {
        buttonDisabled.value = false
      }, 30000)
    } catch (e) {
      console.error('启动更新失败', e)
      ElMessage.error('启动更新失败')
    } finally {
      updating.value = false
    }
  }

  onMounted(() => {
    loadData()
    loadConfig()
  })

  watch(
    () => overview.value?.status,
    (status) => {
      const nextInterval = status === 'running' ? 2000 : 8000
      if (nextInterval === currentPollInterval && pollTimer) return
      currentPollInterval = nextInterval
      if (pollTimer) clearInterval(pollTimer)
      pollTimer = setInterval(() => {
        loadData()
      }, currentPollInterval)
    },
    { immediate: true }
  )

  onUnmounted(() => {
    if (pollTimer) clearInterval(pollTimer)
    if (disabledTimer) clearTimeout(disabledTimer)
  })

  return {
    loading,
    updating,
    savingConfig,
    buttonDisabled,
    overview,
    logs,
    logContainer,
    newKeyword,
    selectedCity,
    newCityCodeName,
    newCityCodeValue,
    config,
    originalConfig,
    allCities,
    cityCodeRows,
    getStatusType,
    getStatusText,
    configChanged,
    canConfirmLogin,
    canStop,
    expectedRequests,
    clearLogs,
    stopping,
    reindexing,
    reindexResult,
    handleReindex,
    loadData,
    loadConfig,
    saveConfig,
    resetConfig,
    addKeyword,
    removeKeyword,
    addCity,
    removeCity,
    addCityCode,
    removeCityCode,
    confirmLogin,
    stopUpdate,
    startUpdate
  }
}
