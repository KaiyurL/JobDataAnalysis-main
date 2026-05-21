<template>
  <div class="dashboard" id="dashboard-content">
    <!-- 顶部概览与导出 -->
    <div class="dashboard-header">
      <div class="header-main">
        <h1 class="gradient-text">📊 数据智能洞察</h1>
        <p class="subtitle">实时监控全网招聘动态，基于 NLP 与深度学习提供市场分析</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" plain round @click="handleExportDashboard" :loading="exporting">
          <el-icon><Download /></el-icon> 导出分析报告
        </el-button>
      </div>
    </div>

    <!-- 顶部核心指标卡片 -->
    <el-row :gutter="24" class="stats-grid">
      <el-col :xs="24" :sm="12" :md="6" v-for="stat in mainStats" :key="stat.label">
        <div class="glass-stat-card" :class="stat.type">
          <div class="stat-icon-wrapper">
            <el-icon><component :is="stat.icon" /></el-icon>
          </div>
          <div class="stat-body">
            <div class="stat-value">{{ stat.value }}<small v-if="stat.unit">{{ stat.unit }}</small></div>
            <div class="stat-label">{{ stat.label }}</div>
          </div>
          <div class="stat-decoration"></div>
        </div>
      </el-col>
    </el-row>

    <!-- AI 智能分析区域 (NLP & Deep Learning) -->
    <div class="section-divider">
      <span class="divider-text">🤖 AI 智能分析引擎</span>
    </div>

    <el-row :gutter="24" class="ai-analysis-row">
      <el-col :xs="24" :lg="14">
        <el-card class="glass-card cluster-card" shadow="never">
          <template #header>
            <div class="card-header-box">
              <div class="title-group">
                <span class="icon-dot blue"></span>
                <span class="title-text">NLP 语义聚类地图</span>
              </div>
              <el-button type="primary" size="small" round @click="startPipeline(true)" :loading="pipelineRunning">
                重新扫描数据
              </el-button>
            </div>
          </template>
          <div class="cluster-visual">
            <div v-if="pipelineImageUrls.cluster" class="cluster-img-wrapper">
              <img :src="pipelineImageUrls.cluster" alt="NLP Clustering" class="cluster-img" />
            </div>
            <div v-else class="empty-placeholder">
              <el-progress 
                v-if="pipelineRunning" 
                type="dashboard" 
                :percentage="pipelineProgress" 
                :color="colors"
                status="warning"
              >
                <template #default="{ percentage }">
                  <span class="percentage-value">{{ percentage }}%</span>
                  <span class="percentage-label">正在聚类</span>
                </template>
              </el-progress>
              <el-empty v-else description="暂无聚类地图，请点击重新扫描" />
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="10">
        <el-card class="glass-card results-card" shadow="never">
          <template #header>
            <div class="card-header-box">
              <div class="title-group">
                <span class="icon-dot purple"></span>
                <span class="title-text">模型训练效能</span>
              </div>
              <el-tag :type="pipelineStatus === 'running' ? 'warning' : (pipelineStatus === 'failed' ? 'danger' : 'success')" effect="dark" round size="small">
                {{ pipelineStatusText }}
              </el-tag>
            </div>
          </template>
          
          <div class="model-metrics">
            <div class="metric-item">
              <div class="metric-label">MLP 预测准确率</div>
              <div class="metric-progress">
                <el-progress :percentage="Number((pipelineSummary?.mlp_val_acc * 100 || 0).toFixed(1))" color="#4f46e5" :stroke-width="12" />
              </div>
            </div>
            <div class="metric-item">
              <div class="metric-label">TextCNN 预测准确率</div>
              <div class="metric-progress">
                <el-progress :percentage="Number((pipelineSummary?.textcnn_val_acc * 100 || 0).toFixed(1))" color="#7c3aed" :stroke-width="12" />
              </div>
            </div>
          </div>

          <div class="top-tokens-section">
            <h4 class="sub-title">核心语义特征 (Top Tokens)</h4>
            <div class="token-cloud" v-if="(pipelineSummary?.top_tokens || []).length">
              <span v-for="t in pipelineSummary.top_tokens" :key="t.token" class="token-chip">
                {{ t.token }} <span class="token-count">{{ t.count }}</span>
              </span>
            </div>
            <el-empty v-else :image-size="60" description="待分析数据特征" />
          </div>

          <div class="pipeline-logs" v-if="(pipelineErrors || []).length">
            <h4 class="sub-title error">异常监控</h4>
            <div class="error-list">
              <div v-for="(e, idx) in pipelineErrors" :key="idx" class="error-item">
                <el-icon><Warning /></el-icon> {{ e }}
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 市场趋势可视化 -->
    <div class="section-divider">
      <span class="divider-text">📈 市场趋势可视化</span>
    </div>

    <!-- 筛选过滤 -->
    <el-card class="glass-card filter-panel" shadow="never">
      <div class="filter-header">
        <el-icon><Filter /></el-icon> 数据筛选
      </div>
      <el-form :inline="true" :model="filters" class="filter-form">
        <el-form-item>
          <el-input v-model="filters.keyword" placeholder="岗位关键词" clearable />
        </el-form-item>
        <el-form-item>
          <el-select v-model="filters.selectedCities" multiple placeholder="目标城市" collapse-tags style="width: 200px">
            <el-option v-for="city in cityOptions" :key="city" :label="city" :value="city" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" round @click="handleSearch" :loading="loading">查询</el-button>
          <el-button round @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 图表矩阵 -->
    <el-row :gutter="24" class="charts-grid">
      <el-col :xs="24" :lg="16">
        <el-card class="glass-card chart-box" shadow="never">
          <template #header>🏙️ 城市平均薪资分布 (K)</template>
          <div v-loading="loading" ref="cityChartRef" class="chart-content"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card class="glass-card chart-box" shadow="never">
          <template #header>🏢 行业需求分布</template>
          <div v-loading="loading" ref="industryChartRef" class="chart-content"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="24" class="charts-grid">
      <el-col :xs="24" :lg="12">
        <el-card class="glass-card chart-box" shadow="never">
          <template #header>🎓 学历与薪资关联分析</template>
          <div v-loading="loading" ref="eduChartRef" class="chart-content"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card class="glass-card chart-box" shadow="never">
          <template #header>💼 经验成长薪资曲线</template>
          <div v-loading="loading" ref="expChartRef" class="chart-content"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="24" class="charts-grid">
      <el-col :xs="24" :lg="14">
        <el-card class="glass-card chart-box" shadow="never">
          <template #header>⭐ 核心技能需求排名</template>
          <div v-loading="loading" ref="skillBarRef" class="chart-content"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="10">
        <el-card class="glass-card chart-box" shadow="never">
          <template #header>🔥 技能关键词生态</template>
          <div v-loading="loading" ref="wordCloudRef" class="chart-content"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import 'echarts-wordcloud'
import { 
  Search, RefreshLeft, Briefcase, Money, Location, 
  Star, Download, Filter, Warning 
} from '@element-plus/icons-vue'
import api from '../api.js'
import { ElMessage } from 'element-plus'
import { exportToPDFMultiPage } from '../utils/exportPdf.js'

const router = useRouter()

// 状态变量
const exporting = ref(false)
const loading = ref(false)
const pipelineRunning = ref(false)
const pipelineStatus = ref('idle')
const pipelineMessage = ref('')
const pipelineSummary = ref({})
const pipelineErrors = ref([])
const pipelineImageUrls = ref({ cluster: '' })
const pipelineObjectUrls = ref({ cluster: '' })
const pipelinePollTimer = ref(null)
const pipelineProgress = ref(0)

// 图表实例与引用
const cityChartRef = ref(null)
const eduChartRef = ref(null)
const expChartRef = ref(null)
const industryChartRef = ref(null)
const wordCloudRef = ref(null)
const skillBarRef = ref(null)

let cityChart = null
let eduChart = null
let expChart = null
let industryChart = null
let wordCloudChart = null
let skillBarChart = null

// 数据变量
const totalJobs = ref(0)
const avgMaxSalary = ref(0)
const topCity = ref('-')
const topSkill = ref('-')
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

// 计算属性
const mainStats = computed(() => [
  { label: '总职位规模', value: totalJobs.value, icon: Briefcase, type: 'blue' },
  { label: '市场均薪', value: avgMaxSalary.value, unit: 'K', icon: Money, type: 'green' },
  { label: '高薪聚集地', value: topCity.value, icon: Location, type: 'orange' },
  { label: '最紧缺技能', value: topSkill.value, icon: Star, type: 'purple' },
])

const pipelineStatusText = computed(() => {
  if (pipelineStatus.value === 'running') return '模型计算中'
  if (pipelineStatus.value === 'failed') return '训练异常'
  return '模型已就绪'
})

const colors = (percentage) => {
  if (percentage < 30) return '#f59e0b'
  if (percentage < 70) return '#4f46e5'
  return '#22c55e'
}

// 方法
const getApiFilters = () => ({
  keyword: filters.value.keyword || undefined,
  city: filters.value.selectedCities.length > 0 ? filters.value.selectedCities.join(',') : undefined,
  education: filters.value.education || undefined,
  experience: filters.value.experience || undefined
})

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
  return Array.from(merged.values()).map(v => ({
    [keyField]: v.key,
    count: v.count,
    avgSalary: v.count > 0 ? Math.round((v.sumSalary / v.count) * 100) / 100 : 0
  }))
}

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

const loadAllData = async () => {
  loading.value = true
  try {
    const filtersToSend = getApiFilters()
    const [bossRes, job51Res] = await Promise.allSettled([
      api.getOverview(filtersToSend),
      api.getOverview51(filtersToSend)
    ])

    const bossOk = bossRes.status === 'fulfilled' && bossRes.value?.data?.code === 200
    const job51Ok = job51Res.status === 'fulfilled' && job51Res.value?.data?.code === 200
    const boss = bossOk ? (bossRes.value.data.data || {}) : {}
    const job51 = job51Ok ? (job51Res.value.data.data || {}) : {}

    if (!bossOk && !job51Ok) {
      const msg = bossRes.status === 'rejected'
        ? (bossRes.reason?.message || 'BOSS数据获取失败')
        : (bossRes.value?.data?.message || 'BOSS数据获取失败')
      const msg2 = job51Res.status === 'rejected'
        ? (job51Res.reason?.message || '51job数据获取失败')
        : (job51Res.value?.data?.message || '51job数据获取失败')
      throw new Error(`${msg}; ${msg2}`)
    }
    if (bossOk && !job51Ok) {
      const msg2 = job51Res.status === 'rejected'
        ? (job51Res.reason?.message || '51job数据获取失败')
        : (job51Res.value?.data?.message || '51job数据获取失败')
      ElMessage.warning(`51job 暂未统计：${msg2}`)
    }
    if (!bossOk && job51Ok) {
      const msg = bossRes.status === 'rejected'
        ? (bossRes.reason?.message || 'BOSS数据获取失败')
        : (bossRes.value?.data?.message || 'BOSS数据获取失败')
      ElMessage.warning(`BOSS 暂未统计：${msg}`)
    }

    totalJobs.value = Number(boss.total || 0) + Number(job51.total || 0)
    cityData.value = mergeAvgStatList(boss.citySalary || [], job51.citySalary || [], 'city')
      .sort((a, b) => (b.count || 0) - (a.count || 0))
      .slice(0, 15)
    eduData.value = mergeAvgStatList(boss.educationSalary || [], job51.educationSalary || [], 'education')
    expData.value = mergeAvgStatList(boss.experienceSalary || [], job51.experienceSalary || [], 'experience')
    industryData.value = mergeCountList(boss.industry || [], job51.industry || [], 'industry')
      .sort((a, b) => (b.count || 0) - (a.count || 0))
      .slice(0, 10)
    keywordData.value = mergeCountList(boss.keywords || [], job51.keywords || [], 'keyword')
      .sort((a, b) => (b.count || 0) - (a.count || 0))
      .slice(0, 50)

    cityOptions.value = [...new Set((cityData.value || []).map(d => d.city).filter(Boolean))].sort()

    calculateStats()
    updateCharts()
  } catch (e) {
    ElMessage.error(e?.message ? `获取市场数据失败：${e.message}` : '获取市场数据失败')
  } finally {
    loading.value = false
  }
}

const calculateStats = () => {
  if (cityData.value.length) {
    topCity.value = [...cityData.value].sort((a, b) => b.avgSalary - a.avgSalary)[0]?.city || '-'
  }
  const salaries = cityData.value.map(d => d.avgSalary)
  avgMaxSalary.value = salaries.length ? Math.round(salaries.reduce((a,b)=>a+b, 0) / salaries.length * 10) / 10 : 0
  topSkill.value = keywordData.value[0]?.keyword || '-'
}

const initCharts = () => {
  cityChart = echarts.init(cityChartRef.value)
  eduChart = echarts.init(eduChartRef.value)
  expChart = echarts.init(expChartRef.value)
  industryChart = echarts.init(industryChartRef.value)
  wordCloudChart = echarts.init(wordCloudRef.value)
  skillBarChart = echarts.init(skillBarRef.value)

  cityChart.on('click', (p) => router.push({ path: '/job-analysis', query: { city: p.name } }))
}

const updateCharts = () => {
  if (!cityChart || !eduChart || !expChart || !industryChart || !wordCloudChart || !skillBarChart) return
  const chartConfigs = [
    { 
      instance: cityChart, 
      option: {
        tooltip: { trigger: 'axis' },
        xAxis: { data: cityData.value.map(d => d.city), axisLabel: { rotate: 30 } },
        yAxis: { name: 'K' },
        series: [{ type: 'bar', data: cityData.value.map(d => d.avgSalary), itemStyle: { color: '#4f46e5', borderRadius: [4, 4, 0, 0] } }]
      }
    },
    {
      instance: eduChart,
      option: {
        tooltip: { trigger: 'item' },
        radar: {
          indicator: (eduData.value || []).map(d => ({
            name: d.education,
            max: Math.max(1, ...(eduData.value || []).map(x => Number(x.avgSalary || 0))) * 1.2
          }))
        },
        series: [{
          type: 'radar',
          data: [{ value: (eduData.value || []).map(d => d.avgSalary), name: '平均薪资' }],
          itemStyle: { color: '#7c3aed' },
          areaStyle: { opacity: 0.25 }
        }]
      }
    },
    {
      instance: expChart,
      option: {
        tooltip: { trigger: 'axis' },
        xAxis: { type: 'category', data: expData.value.map(d => d.experience) },
        yAxis: { type: 'value' },
        series: [{ type: 'line', smooth: true, data: expData.value.map(d => d.avgSalary), lineStyle: { width: 4, color: '#4f46e5' }, areaStyle: { color: new echarts.graphic.LinearGradient(0,0,0,1, [{offset:0, color:'rgba(79,70,229,0.2)'},{offset:1, color:'transparent'}]) } }]
      }
    },
    {
      instance: industryChart,
      option: {
        tooltip: { trigger: 'item' },
        series: [{ type: 'pie', radius: ['40%', '70%'], avoidLabelOverlap: false, itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 }, label: { show: false }, emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' } }, data: industryData.value.map(d => ({ name: d.industry, value: d.count })) }]
      }
    },
    {
      instance: wordCloudChart,
      option: {
        series: [{
          type: 'wordCloud',
          sizeRange: [12, 45],
          rotationRange: [-45, 45],
          gridSize: 8,
          textStyle: { fontFamily: 'sans-serif', fontWeight: 'bold', color: () => `hsl(${Math.random() * 360}, 70%, 60%)` },
          data: keywordData.value.map(d => ({ name: d.keyword, value: d.count }))
        }]
      }
    },
    {
      instance: skillBarChart,
      option: {
        tooltip: { trigger: 'axis' },
        grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: { type: 'value' },
        yAxis: { type: 'category', data: keywordData.value.slice(0, 10).reverse().map(d => d.keyword) },
        series: [{ type: 'bar', data: keywordData.value.slice(0, 10).reverse().map(d => d.count), itemStyle: { color: new echarts.graphic.LinearGradient(0,0,1,0, [{offset:0, color:'#4f46e5'},{offset:1, color:'#7c3aed'}]) } }]
      }
    }
  ]
  chartConfigs.forEach(c => c.instance.setOption(c.option, true))
}

const handleResize = () => {
  ;[cityChart, eduChart, expChart, industryChart, wordCloudChart, skillBarChart].forEach(c => c?.resize())
}

// Pipeline 相关逻辑
const loadPipelineArtifacts = async () => {
  const res = await api.getPipelineArtifacts()
  if (res?.data?.code !== 200) return
  const data = res.data.data
  pipelineSummary.value = data.summary
  pipelineErrors.value = data.errors
  if (data.artifacts?.cluster_scatter_png) {
    const blobRes = await api.getPipelineFile('cluster_scatter_png')
    if (blobRes?.data) {
      const url = URL.createObjectURL(blobRes.data)
      pipelineObjectUrls.value.cluster = url
      pipelineImageUrls.value.cluster = url
    }
  }
  return Boolean(data?.runDir) && Boolean(data?.artifacts) && Object.keys(data.artifacts || {}).length > 0
}

const startPipeline = async (force = false) => {
  pipelineRunning.value = true
  pipelineProgress.value = 10
  pipelineStatus.value = 'running'
  revokePipelineUrls()
  try {
    const runRes = force ? await api.runDashboardPipelineForce() : await api.runDashboardPipeline()
    const cached = runRes?.data?.data?.cached === true
    if (!force && cached) {
      pipelineRunning.value = false
      pipelineProgress.value = 100
      pipelineStatus.value = 'idle'
      pipelineMessage.value = runRes?.data?.data?.message || '已使用缓存结果'
      await loadPipelineArtifacts()
      return
    }
    pipelinePollTimer.value = setInterval(async () => {
      const res = await api.getPipelineStatus()
      const s = res.data.data
      pipelineStatus.value = s.status
      pipelineMessage.value = s.message
      pipelineProgress.value = Math.min(95, pipelineProgress.value + 5)
      if (s.status !== 'running') {
        pipelineRunning.value = false
        pipelineProgress.value = 100
        clearInterval(pipelinePollTimer.value)
        loadPipelineArtifacts()
      }
    }, 3000)
  } catch (e) {
    pipelineRunning.value = false
    pipelineStatus.value = 'failed'
  }
}

const revokePipelineUrls = () => {
  if (pipelineObjectUrls.value.cluster) URL.revokeObjectURL(pipelineObjectUrls.value.cluster)
  pipelineImageUrls.value.cluster = ''
}

const handleSearch = () => loadAllData()
const handleReset = () => { filters.value = { keyword: '', selectedCities: [], education: '', experience: '' }; loadAllData() }
const handleExportDashboard = () => exportToPDFMultiPage('dashboard-content', '招聘数据洞察报告.pdf')

onMounted(() => {
  initCharts()
  loadAllData()
  loadPipelineArtifacts().then((has) => {
    if (has) {
      pipelineStatus.value = 'idle'
      pipelineMessage.value = '已加载上次分析结果'
      pipelineProgress.value = 100
      pipelineRunning.value = false
      return
    }
    startPipeline(false)
  })
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  if (pipelinePollTimer.value) clearInterval(pipelinePollTimer.value)
  revokePipelineUrls()
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.dashboard {
  max-width: 1440px;
  margin: 0 auto;
  animation: fadeIn 0.8s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
}

.gradient-text {
  font-size: 32px;
  font-weight: 800;
  background: linear-gradient(135deg, #1e293b 0%, #4f46e5 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.subtitle {
  color: #64748b;
  margin-top: 4px;
}

/* 核心指标卡片 */
.stats-grid {
  margin-bottom: 32px;
}

.glass-stat-card {
  height: 120px;
  background: #ffffff;
  border-radius: 20px;
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 20px;
  position: relative;
  overflow: hidden;
  transition: all 0.3s;
  box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05);
}

.glass-stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 20px -8px rgba(0,0,0,0.1);
}

.stat-icon-wrapper {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.blue .stat-icon-wrapper { background: #eff6ff; color: #3b82f6; }
.green .stat-icon-wrapper { background: #f0fdf4; color: #22c55e; }
.orange .stat-icon-wrapper { background: #fff7ed; color: #f97316; }
.purple .stat-icon-wrapper { background: #faf5ff; color: #a855f7; }

.stat-value {
  font-size: 28px;
  font-weight: 800;
  color: #1e293b;
  line-height: 1;
}

.stat-value small {
  font-size: 14px;
  margin-left: 4px;
  color: #64748b;
}

.stat-label {
  font-size: 13px;
  color: #64748b;
  margin-top: 6px;
  font-weight: 600;
}

.stat-decoration {
  position: absolute;
  right: -20px;
  top: -20px;
  width: 80px;
  height: 80px;
  background: currentColor;
  opacity: 0.03;
  border-radius: 50%;
}

/* 区域分割线 */
.section-divider {
  display: flex;
  align-items: center;
  margin: 40px 0 24px;
}

.section-divider::after {
  content: "";
  flex: 1;
  height: 1px;
  background: linear-gradient(to right, #e2e8f0, transparent);
  margin-left: 16px;
}

.divider-text {
  font-size: 14px;
  font-weight: 700;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 1px;
}

/* AI 分析卡片 */
.ai-analysis-row {
  margin-bottom: 32px;
}

.glass-card {
  border: none;
  background: #ffffff;
  border-radius: 24px;
  box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05);
}

.card-header-box {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.icon-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.icon-dot.blue { background: #3b82f6; box-shadow: 0 0 8px #3b82f6; }
.icon-dot.purple { background: #a855f7; box-shadow: 0 0 8px #a855f7; }

.title-text {
  font-weight: 700;
  color: #334155;
}

.cluster-visual {
  height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cluster-img {
  max-width: 100%;
  max-height: 400px;
  border-radius: 12px;
}

.model-metrics {
  margin-bottom: 24px;
}

.metric-item {
  margin-bottom: 16px;
}

.metric-label {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 8px;
  font-weight: 600;
}

.sub-title {
  font-size: 14px;
  font-weight: 700;
  color: #475569;
  margin: 24px 0 12px;
}

.sub-title.error { color: #ef4444; }

.token-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.token-chip {
  background: #f8fafc;
  padding: 6px 12px;
  border-radius: 8px;
  font-size: 12px;
  color: #475569;
  font-weight: 600;
  border: 1px solid #e2e8f0;
}

.token-count {
  color: #4f46e5;
  margin-left: 4px;
}

.error-list {
  background: #fef2f2;
  padding: 12px;
  border-radius: 12px;
  font-size: 12px;
  color: #b91c1c;
}

.error-item {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

/* 筛选面板 */
.filter-panel {
  padding: 20px;
  margin-bottom: 24px;
}

.filter-header {
  font-size: 14px;
  font-weight: 700;
  color: #334155;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 图表框 */
.chart-box {
  margin-bottom: 24px;
}

.chart-content {
  height: 320px;
}

:deep(.el-card__header) {
  border-bottom: 1px solid #f1f5f9;
  padding: 16px 24px;
  font-weight: 700;
  color: #334155;
}
</style>
