
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
            
            <div class="tips">
              <p>⚠️ 注意：点击后将启动爬虫脚本，过程可能需要几分钟</p>
              <p>💡 请先保存配置，再启动爬虫</p>
              <p>🔒 爬虫运行时按钮将禁用30秒，防止重复触发</p>
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
              <span class="preview-value">{{ config.keywords.length * config.cities.length * config.pages_per_keyword }} 次</span>
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

// 配置相关
const config = ref({
  keywords: [],
  cities: [],
  pages_per_keyword: 2,
  delay_min: 3,
  delay_max: 8
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

const addLog = (text) => {
  const time = new Date().toLocaleTimeString()
  logs.value.unshift({ time, text })
  if (logs.value.length > 50) logs.value.pop()
  setTimeout(() => {
    if (logContainer.value) {
      logContainer.value.scrollTop = 0
    }
  }, 0)
}

const clearLogs = () => {
  logs.value = []
}

// 配置管理
const loadConfig = async () => {
  try {
    const res = await api.getConfig()
    if (res.data.code === 200) {
      config.value = { ...res.data.data }
      originalConfig.value = { ...res.data.data }
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
      config.value = { ...res.data.data }
      originalConfig.value = { ...res.data.data }
      ElMessage.success('配置保存成功！')
      addLog('✅ 配置已保存')
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
    keywords: ['Java', 'Python', '前端', '数据分析', '产品经理'],
    cities: ['北京', '上海', '广州', '深圳', '杭州'],
    pages_per_keyword: 2,
    delay_min: 3,
    delay_max: 8
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

const loadData = async () => {
  loading.value = true
  try {
    const res = await api.getDataOverview()
    if (res.data.code === 200) {
      const data = res.data.data
      overview.value = data
      keywordCounts.value = data.keywordCounts || {}
      if (data.status === 'running') {
        addLog('🔄 爬虫正在运行...')
      } else if (data.status === 'idle' && overview.value.status !== 'idle') {
        addLog('✅ 爬虫运行完成！')
      } else if (data.status === 'failed') {
        addLog('❌ 爬虫运行失败')
      }
    }
  } catch (e) {
    console.error('加载数据失败', e)
  } finally {
    loading.value = false
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
      addLog('🚀 更新任务已启动，请稍候...')
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
  
  addLog('📊 数据管理页面已加载')
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
