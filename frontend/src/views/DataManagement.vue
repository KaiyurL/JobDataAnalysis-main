
<template>
  <div class="data-management">
    <div class="page-header">
      <h1>🔧 数据管理</h1>
      <p>监控爬虫状态、管理数据更新和自定义爬取配置</p>
    </div>

    <el-row :gutter="20">
      <!-- 左侧：数据概况 + 配置表单 -->
      <el-col :xs="24" :lg="12">
        <el-card class="stats-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>📊 数据概况</span>
              <el-button size="small" type="primary" @click="loadData" :loading="loading">
                刷新
              </el-button>
            </div>
          </template>

          <el-descriptions :column="1" border>
            <el-descriptions-item label="总数据量">
              <span class="stat-number">{{ overview.totalCount || 0 }}</span>
              条记录
            </el-descriptions-item>
            <el-descriptions-item label="本次开始时间">
              <span class="stat-text">{{ overview.lastStartTime || '未开始' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="本次结束时间">
              <span class="stat-text">{{ overview.lastEndTime || (overview.status === 'running' ? '进行中' : '未知') }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="上次爬取时间">
              <span class="stat-text">{{ overview.lastCrawlTime || '未知' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="爬虫状态">
              <el-tag :type="getStatusType(overview.status)">
                {{ getStatusText(overview.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="状态信息">
              <span class="status-message">{{ overview.lastMessage || '暂无' }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card class="config-card" shadow="hover" style="margin-top: 20px;">
          <template #header>
            <div class="card-header">
              <span>⚙️ 爬虫配置</span>
              <el-button 
                size="small" 
                type="success" 
                @click="saveConfig" 
                :loading="savingConfig"
                :disabled="!configChanged"
              >
                保存配置
              </el-button>
            </div>
          </template>

          <el-form :model="config" label-width="120px">
            <el-form-item label="数据源">
              <el-select v-model="config.platform" style="width: 100%;">
                <el-option label="BOSS直聘" value="boss" />
                <el-option label="前程无忧" value="51job" />
                <el-option label="全部" value="both" />
              </el-select>
              <div class="form-tip">选择要爬取的数据平台</div>
            </el-form-item>

            <el-form-item label="岗位关键词">
              <div class="keywords-input">
                <el-tag
                  v-for="(keyword, index) in config.keywords"
                  :key="index"
                  closable
                  @close="removeKeyword(index)"
                  style="margin-right: 8px; margin-bottom: 8px;"
                >
                  {{ keyword }}
                </el-tag>
              </div>
              <div style="display: flex; gap: 10px; margin-top: 10px;">
                <el-input 
                  v-model="newKeyword" 
                  placeholder="输入关键词，按回车添加"
                  @keyup.enter="addKeyword"
                />
                <el-button type="primary" @click="addKeyword">添加</el-button>
              </div>
              <div class="form-tip">多个关键词将分别爬取</div>
            </el-form-item>

            <el-form-item label="目标城市">
              <div class="cities-input">
                <el-tag
                  v-for="(city, index) in config.cities"
                  :key="index"
                  closable
                  @close="removeCity(index)"
                  style="margin-right: 8px; margin-bottom: 8px;"
                >
                  {{ city }}
                </el-tag>
              </div>
              <el-select 
                v-model="selectedCity" 
                placeholder="选择/搜索城市"
                filterable
                style="width: 100%; margin-top: 10px;"
                @change="addCity"
              >
                <el-option 
                  v-for="city in allCities" 
                  :key="city" 
                  :label="city" 
                  :value="city"
                />
              </el-select>
              <div class="form-tip">从下拉选择或直接输入搜索</div>
            </el-form-item>

            <el-form-item label="爬取页数">
              <el-input-number 
                v-model="config.pages_per_keyword" 
                :min="1" 
                :max="10"
              />
              <span style="margin-left: 10px;">页/关键词</span>
            </el-form-item>

            <el-form-item v-if="config.platform !== 'boss'" label="前程无忧页数">
              <el-input-number 
                v-model="config.pages_per_city_51job" 
                :min="1" 
                :max="20"
              />
              <span style="margin-left: 10px;">页/城市/关键词</span>
            </el-form-item>

            <el-form-item v-if="config.platform !== 'boss'" label="前程无忧城市编码">
              <el-table :data="cityCodeRows" size="small" border style="width: 100%;">
                <el-table-column prop="name" label="城市" width="140" />
                <el-table-column label="编码" min-width="160">
                  <template #default="{ row }">
                    <el-input
                      v-model="config.city_codes_51job[row.name]"
                      size="small"
                      placeholder="例如 010000"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="90">
                  <template #default="{ row }">
                    <el-button type="danger" size="small" link @click="removeCityCode(row.name)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>

              <div style="display: flex; gap: 10px; margin-top: 10px; width: 100%;">
                <el-input v-model="newCityCodeName" placeholder="新增城市名（如 北京）" />
                <el-input v-model="newCityCodeValue" placeholder="新增编码（如 010000）" />
                <el-button type="primary" @click="addCityCode">添加</el-button>
              </div>
              <div class="form-tip">只对前程无忧有效；用于 jobArea 参数</div>
            </el-form-item>

            <el-form-item label="请求延迟">
              <el-input-number 
                v-model="config.delay_min" 
                :min="1" 
                :max="20"
                style="width: 120px;"
              />
              <span style="margin: 0 10px;">-</span>
              <el-input-number 
                v-model="config.delay_max" 
                :min="config.delay_min" 
                :max="30"
                style="width: 120px;"
              />
              <span style="margin-left: 10px;">秒</span>
            </el-form-item>

            <el-form-item>
              <el-button type="warning" @click="resetConfig">恢复默认</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card class="keyword-card" shadow="hover" style="margin-top: 20px;">
          <template #header>
            <span>📈 关键词数据分布</span>
          </template>

          <div v-if="Object.keys(keywordCounts).length > 0" class="keyword-list">
            <div v-for="(count, keyword) in keywordCounts" :key="keyword" class="keyword-item">
              <div class="keyword-name">{{ keyword }}</div>
              <div class="keyword-bar-wrapper">
                <div 
                  class="keyword-bar" 
                  :style="{ width: getBarWidth(count) + '%' }"
                ></div>
                <span class="keyword-count">{{ count }} 条</span>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无数据" />
        </el-card>
      </el-col>

      <!-- 右侧：操作区 + 日志 -->
      <el-col :xs="24" :lg="12">
        <el-card class="action-card" shadow="hover">
          <template #header>
            <span>⚡️ 数据操作</span>
          </template>

          <div class="update-section">
            <el-button 
              type="danger" 
              size="large" 
              @click="startUpdate" 
              :loading="updating"
              :disabled="buttonDisabled"
              style="width: 100%; height: 50px;"
            >
              <el-icon><Refresh /></el-icon>
              {{ buttonDisabled ? '请等待...' : '立即更新数据' }}
            </el-button>

            <el-button
              v-if="canConfirmLogin"
              type="primary"
              size="large"
              @click="confirmLogin"
              style="width: 100%; height: 50px; margin-top: 12px;"
            >
              我已登录，继续爬取
            </el-button>
            
            <div class="tips">
              <p>⚠️ 注意：点击后将启动爬虫脚本，过程可能需要几分钟</p>
              <p>💡 请先保存配置，再启动爬虫</p>
              <p>🔒 爬虫运行时按钮将禁用30秒，防止重复触发</p>
              <p v-if="canConfirmLogin">✅ 请先在弹出的浏览器中完成登录/验证，然后点击“我已登录，继续爬取”</p>
            </div>
          </div>
        </el-card>

        <el-card class="preview-card" shadow="hover" style="margin-top: 20px;">
          <template #header>
            <span>🔍 本次爬取预览</span>
          </template>
          
          <div class="preview-content">
            <div class="preview-item">
              <span class="preview-label">关键词：</span>
              <span class="preview-value">{{ config.keywords.join('、') || '-' }}</span>
            </div>
            <div class="preview-item">
              <span class="preview-label">城市：</span>
              <span class="preview-value">{{ config.cities.join('、') || '-' }}</span>
            </div>
            <div class="preview-item">
              <span class="preview-label">预计请求：</span>
              <span class="preview-value">{{ expectedRequests }} 次</span>
            </div>
          </div>
        </el-card>

        <el-card class="log-card" shadow="hover" style="margin-top: 20px;">
          <template #header>
            <div class="card-header">
              <span>📝 运行日志</span>
              <el-button size="small" link @click="clearLogs">清空</el-button>
            </div>
          </template>

          <div class="log-container" ref="logContainer">
            <div v-if="logs.length === 0" class="empty-log">暂无日志</div>
            <div v-for="(log, index) in logs" :key="index" class="log-item">
              <span class="log-time">[{{ log.time }}]</span>
              <span class="log-text">{{ log.text }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import api from '../api.js'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const updating = ref(false)
const savingConfig = ref(false)
const buttonDisabled = ref(false)
const overview = ref({})
const keywordCounts = ref({})
const logs = ref([])
const logContainer = ref(null)
const newKeyword = ref('')
const selectedCity = ref('')
const newCityCodeName = ref('')
const newCityCodeValue = ref('')

// 配置相关
const config = ref({
  platform: 'boss',
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
  '北京', '上海', '广州', '深圳', '杭州',
  '成都', '武汉', '西安', '重庆', '南京',
  '苏州', '天津', '郑州', '长沙', '青岛',
  '大连', '厦门', '宁波', '无锡', '合肥',
  '福州', '济南', '昆明', '南昌', '哈尔滨',
  '沈阳', '长春', '石家庄', '太原', '郑州'
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

const statusMap = {
  'idle': { type: 'success', text: '空闲' },
  'running': { type: 'warning', text: '运行中' },
  'failed': { type: 'danger', text: '失败' }
}

const getStatusType = (status) => statusMap[status]?.type || 'info'
const getStatusText = (status) => statusMap[status]?.text || '未知'

const getBarWidth = (count) => {
  const max = Math.max(...Object.values(keywordCounts.value), 1)
  return Math.min(100, (count / max) * 100)
}

const configChanged = computed(() => {
  return JSON.stringify(config.value) !== JSON.stringify(originalConfig.value)
})

const deepClone = (obj) => JSON.parse(JSON.stringify(obj))

const canConfirmLogin = computed(() => {
  return overview.value?.status === 'running' && overview.value?.waitingForLogin === true
})

const expectedRequests = computed(() => {
  const kw = config.value.keywords?.length || 0
  const ct = config.value.cities?.length || 0
  const boss = kw * ct * (config.value.pages_per_keyword || 0)
  const job51 = kw * ct * (config.value.pages_per_city_51job || 0)
  if (config.value.platform === 'both') return boss + job51
  if (config.value.platform === '51job') return job51
  return boss
})

const clearLogs = () => {
  api.clearCrawlerLogs().finally(() => {
    logs.value = []
  })
}

// 配置管理
const loadConfig = async () => {
  try {
    const res = await api.getConfig()
    if (res.data.code === 200) {
      const incoming = { ...res.data.data }
      incoming.platform = incoming.platform || 'boss'
      incoming.pages_per_city_51job = incoming.pages_per_city_51job || 2
      const codes = incoming.city_codes_51job || {}
      incoming.city_codes_51job = Object.keys(codes).length > 0 ? codes : { ...defaultCityCodes51Job }
      config.value = deepClone(incoming)
      originalConfig.value = deepClone(incoming)
    }
  } catch (e) {
    console.error('加载配置失败', e)
  }
}

const saveConfig = async () => {
  savingConfig.value = true
  try {
    const res = await api.updateConfig(config.value)
    if (res.data.code === 200) {
      const incoming = { ...res.data.data }
      incoming.platform = incoming.platform || 'boss'
      incoming.pages_per_city_51job = incoming.pages_per_city_51job || 2
      const codes = incoming.city_codes_51job || {}
      incoming.city_codes_51job = Object.keys(codes).length > 0 ? codes : { ...defaultCityCodes51Job }
      config.value = deepClone(incoming)
      originalConfig.value = deepClone(incoming)
      ElMessage.success('配置保存成功！')
    } else {
      ElMessage.error(res.data.message || '保存失败')
    }
  } catch (e) {
    console.error('保存配置失败', e)
    ElMessage.error('保存配置失败')
  } finally {
    savingConfig.value = false
  }
}

const resetConfig = () => {
  config.value = {
    platform: 'boss',
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
const addKeyword = () => {
  const keyword = newKeyword.value.trim()
  if (keyword && !config.value.keywords.includes(keyword)) {
    config.value.keywords.push(keyword)
    newKeyword.value = ''
  }
}

const removeKeyword = (index) => {
  config.value.keywords.splice(index, 1)
}

// 城市管理
const addCity = () => {
  const city = selectedCity.value
  if (city && !config.value.cities.includes(city)) {
    config.value.cities.push(city)
    selectedCity.value = ''
  }
}

const removeCity = (index) => {
  config.value.cities.splice(index, 1)
}

const cityCodeRows = computed(() => {
  const obj = config.value.city_codes_51job || {}
  return Object.keys(obj)
    .sort((a, b) => a.localeCompare(b, 'zh-CN'))
    .map(name => ({ name, code: obj[name] }))
})

const upsertCityCode = (name, code) => {
  const n = String(name || '').trim()
  const c = String(code || '').trim()
  if (!n || !c) return
  if (!config.value.city_codes_51job) config.value.city_codes_51job = {}
  config.value.city_codes_51job[n] = c
}

const addCityCode = () => {
  upsertCityCode(newCityCodeName.value, newCityCodeValue.value)
  newCityCodeName.value = ''
  newCityCodeValue.value = ''
}

const removeCityCode = (name) => {
  if (config.value.city_codes_51job && Object.prototype.hasOwnProperty.call(config.value.city_codes_51job, name)) {
    delete config.value.city_codes_51job[name]
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await api.getDataOverview()
    if (res.data.code === 200) {
      const data = res.data.data
      overview.value = data
      keywordCounts.value = data.keywordCounts || {}
      logs.value = Array.isArray(data.logs) ? data.logs : []
      setTimeout(() => {
        if (logContainer.value) {
          logContainer.value.scrollTop = logContainer.value.scrollHeight
        }
      }, 0)
    }
  } catch (e) {
    console.error('加载数据失败', e)
  } finally {
    loading.value = false
  }
}

const confirmLogin = async () => {
  try {
    const res = await api.confirmCrawlerLogin()
    if (res.data.code === 200) {
      const payload = res.data.data || {}
      if (payload.success === false) {
        ElMessage.warning(payload.message || '当前无需确认')
        return
      }
      ElMessage.success(payload.message || '已发送继续信号')
      await loadData()
    } else {
      ElMessage.warning(res.data.message || '确认失败')
    }
  } catch (e) {
    console.error('确认登录失败', e)
    ElMessage.error('确认登录失败')
  }
}

const startUpdate = async () => {
  if (buttonDisabled.value) return
  
  if (configChanged.value) {
    ElMessage.warning('请先保存配置再启动爬虫！')
    return
  }
  
  updating.value = true
  try {
    const res = await api.startDataUpdate()
    if (res.data.code === 200) {
      ElMessage.success(res.data.data?.message || '更新任务已启动')
      await loadData()
      buttonDisabled.value = true
      disabledTimer = setTimeout(() => {
        buttonDisabled.value = false
      }, 30000)
    } else {
      ElMessage.warning(res.data.message || '启动失败')
    }
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
  
  pollTimer = setInterval(() => {
    loadData()
  }, 3000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  if (disabledTimer) clearTimeout(disabledTimer)
})
</script>

<style scoped>
.data-management {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h1 {
  margin: 0 0 8px 0;
  font-size: 28px;
  color: #333;
}

.page-header p {
  margin: 0;
  color: #666;
  font-size: 14px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stats-card,
.keyword-card,
.action-card,
.log-card,
.config-card,
.preview-card {
  border-radius: 8px;
}

.stat-number {
  font-size: 24px;
  font-weight: bold;
  color: #409EFF;
}

.stat-text {
  color: #333;
}

.status-message {
  color: #666;
}

.keyword-list {
  padding: 10px 0;
}

.keyword-item {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}

.keyword-name {
  width: 100px;
  flex-shrink: 0;
  font-weight: 500;
}

.keyword-bar-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  height: 20px;
  background: #f5f7fa;
  border-radius: 10px;
  overflow: hidden;
  padding: 0 10px;
  position: relative;
}

.keyword-bar {
  position: absolute;
  left: 0;
  top: 0;
  height: 100%;
  background: linear-gradient(90deg, #409EFF, #67C23A);
  border-radius: 10px;
  transition: width 0.3s;
}

.keyword-count {
  position: relative;
  z-index: 1;
  margin-left: auto;
  font-size: 12px;
  color: #666;
}

.update-section {
  padding: 20px 0;
}

.tips {
  margin-top: 20px;
  padding: 15px;
  background: #f5f7fa;
  border-radius: 6px;
}

.tips p {
  margin: 0 0 8px 0;
  font-size: 13px;
  color: #666;
}

.tips p:last-child {
  margin-bottom: 0;
}

.form-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #999;
}

.log-container {
  height: 300px;
  overflow-y: auto;
  background: #1e1e1e;
  border-radius: 6px;
  padding: 15px;
}

.empty-log {
  text-align: center;
  color: #666;
  padding: 50px;
}

.log-item {
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 13px;
  margin-bottom: 8px;
  color: #fff;
}

.log-time {
  color: #4ec9b0;
  margin-right: 10px;
}

.preview-content {
  padding: 10px 0;
}

.preview-item {
  display: flex;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.preview-item:last-child {
  margin-bottom: 0;
  padding-bottom: 0;
  border-bottom: none;
}

.preview-label {
  width: 100px;
  color: #666;
  flex-shrink: 0;
}

.preview-value {
  flex: 1;
  color: #333;
  font-weight: 500;
}
</style>
