<template>
  <div class="dashboard cockpit-layout" id="dashboard-content">
    <el-row :gutter="24" class="cockpit-row">
      <!-- 左侧控制台: 核心指标与玫瑰图 -->
      <el-col :xs="24" :lg="7" class="col-left">
        <div class="brand-header">
          <h1 class="gradient-text">📊 数据智能洞察</h1>
          <p class="subtitle">全网招聘动态实时监控</p>
        </div>
        
        <div class="bento-stats">
          <div v-for="stat in mainStats" :key="stat.label" class="bento-stat-card" :class="stat.type">
            <div class="stat-icon"><el-icon><component :is="stat.icon" /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value">{{ stat.value }}<small v-if="stat.unit">{{ stat.unit }}</small></div>
              <div class="stat-label">{{ stat.label }}</div>
            </div>
          </div>
        </div>

        <el-card class="glass-card chart-box" shadow="never">
          <template #header>📌 结构分布</template>
          <el-tabs v-model="leftTab" class="dash-tabs" stretch>
            <el-tab-pane label="🏢 行业" name="industry">
              <div v-show="leftTab === 'industry'" v-loading="loading" ref="industryChartRef" class="chart-content tab-chart"></div>
            </el-tab-pane>
            <el-tab-pane label="🎓 学历" name="edu">
              <div v-show="leftTab === 'edu'" v-loading="loading" ref="eduChartRef" class="chart-content tab-chart"></div>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>

      <!-- 中间主视野: 筛选与核心大图 -->
      <el-col :xs="24" :lg="11" class="col-main">
        <el-card class="glass-card filter-cockpit" shadow="never">
          <el-form :inline="true" :model="filters" class="filter-form">
            <el-form-item>
              <el-input v-model="filters.keyword" placeholder="岗位关键词" clearable prefix-icon="Search" style="width: 180px" />
            </el-form-item>
            <el-form-item>
              <el-select v-model="filters.selectedCities" multiple placeholder="目标城市" collapse-tags style="width: 160px">
                <el-option v-for="city in cityOptions" :key="city" :label="city" :value="city" />
              </el-select>
            </el-form-item>
            <el-form-item class="filter-actions">
              <el-button type="primary" round @click="handleSearch" :loading="loading">查询</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card class="glass-card chart-box main-chart" shadow="never">
          <template #header>🏙️ 城市平均薪资全景 (K)</template>
          <div v-loading="loading" ref="cityChartRef" class="chart-content large-chart"></div>
        </el-card>

        <el-card class="glass-card chart-box" shadow="never">
          <template #header>💼 经验成长薪资曲线</template>
          <div v-loading="loading" ref="expChartRef" class="chart-content"></div>
        </el-card>
      </el-col>

      <!-- 右侧侧翼: 操作区与技能分析 -->
      <el-col :xs="24" :lg="6" class="col-right">
        <div class="action-panel">
          <el-button color="#7c3aed" class="action-btn" plain round @click="aiDrawerVisible = true">
            <el-icon><DataLine /></el-icon> 深度数据分析
          </el-button>
          <el-button type="primary" class="action-btn" plain round @click="handleExportDashboard" :loading="exporting">
            <el-icon><Download /></el-icon> 导出报告
          </el-button>
        </div>

        <el-card class="glass-card chart-box" shadow="never">
          <template #header>🧠 技能画像</template>
          <el-tabs v-model="rightTab" class="dash-tabs" stretch>
            <el-tab-pane label="🔥 生态" name="wordcloud">
              <div v-show="rightTab === 'wordcloud'" v-loading="loading" ref="wordCloudRef" class="chart-content tab-chart"></div>
            </el-tab-pane>
            <el-tab-pane label="⭐ Top10" name="top10">
              <div v-show="rightTab === 'top10'" v-loading="loading" ref="skillBarRef" class="chart-content tab-chart"></div>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>

    <!-- 深度数据分析区域 (NLP & Deep Learning) - 隐藏在抽屉中 -->
    <el-drawer v-model="aiDrawerVisible" title="🔬 深度数据分析与特征提取" size="600px" direction="rtl">
      <div class="ai-drawer-content">
        <el-card class="drawer-card cluster-card" shadow="never" style="margin-bottom: 24px;">
          <template #header>
            <div class="card-header-box">
              <div class="title-group">
                <span class="icon-dot blue"></span>
                <span class="title-text">NLP 语义聚类地图</span>
              </div>
              <el-button type="primary" plain round @click="startPipeline(true)" :loading="pipelineRunning">
                <el-icon><RefreshRight /></el-icon> 重新扫描数据
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

        <el-card class="drawer-card results-card" shadow="never">
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
              <div class="metric-label">
                <span>MLP 预测准确率</span>
                <span style="color: #4f46e5;">{{ (pipelineSummary?.mlp_val_acc * 100 || 0).toFixed(1) }}%</span>
              </div>
              <div class="metric-progress">
                <el-progress :percentage="Number((pipelineSummary?.mlp_val_acc * 100 || 0).toFixed(1))" color="#4f46e5" :stroke-width="12" :show-text="false" />
              </div>
            </div>
            <div class="metric-item">
              <div class="metric-label">
                <span>TextCNN 预测准确率</span>
                <span style="color: #7c3aed;">{{ (pipelineSummary?.textcnn_val_acc * 100 || 0).toFixed(1) }}%</span>
              </div>
              <div class="metric-progress">
                <el-progress :percentage="Number((pipelineSummary?.textcnn_val_acc * 100 || 0).toFixed(1))" color="#7c3aed" :stroke-width="12" :show-text="false" />
              </div>
            </div>
          </div>

          <div class="top-tokens-section">
            <h4 class="sub-title">
              核心语义特征 (Top Tokens)
              <el-button
                size="small"
                link
                type="primary"
                style="margin-left: auto;"
                :loading="topTokensRunning"
                :disabled="pipelineRunning"
                @click="startStatsPipeline(true)"
              >
                <el-icon><RefreshRight /></el-icon> 单独刷新
              </el-button>
            </h4>
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
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import 'echarts-wordcloud'
import { 
  Search, RefreshLeft, Briefcase, Money, Location, 
  Star, Download, Filter, Warning, RefreshRight, DataLine
} from '@element-plus/icons-vue'
import api from '../api.js'
import { ElMessage } from 'element-plus'
import { exportToPDFMultiPage } from '../utils/exportPdf.js'

const router = useRouter()

// 状态变量
const exporting = ref(false)
const loading = ref(false)
const aiDrawerVisible = ref(false)
const pipelineRunning = ref(false)
const topTokensRunning = ref(false)
const pipelineStatus = ref('idle')
const pipelineMessage = ref('')
const pipelineSummary = ref({})
const pipelineErrors = ref([])
const pipelineImageUrls = ref({ cluster: '' })
const pipelineObjectUrls = ref({ cluster: '' })
const pipelinePollTimer = ref(null)
const pipelineProgress = ref(0)
const leftTab = ref('industry')
const rightTab = ref('wordcloud')

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
      .sort((a, b) => Number(b.avgSalary || 0) - Number(a.avgSalary || 0))
    const getExpWeight = (exp) => {
      if (!exp) return 0;
      if (exp.includes('应届') || exp.includes('在校')) return 1;
      if (exp.includes('无需') || exp.includes('不限')) return 2;
      const match = exp.match(/(\d+)/);
      if (match) {
        return parseInt(match[1]) + 3;
      }
      return 99;
    }

    eduData.value = mergeAvgStatList(boss.educationSalary || [], job51.educationSalary || [], 'education')
    expData.value = mergeAvgStatList(boss.experienceSalary || [], job51.experienceSalary || [], 'experience')
      .sort((a, b) => getExpWeight(a.experience) - getExpWeight(b.experience))
    industryData.value = mergeCountList(boss.industry || [], job51.industry || [], 'industry')
      .sort((a, b) => (b.count || 0) - (a.count || 0))
      .slice(0, 10)
    keywordData.value = (Array.isArray(boss.keywords) ? boss.keywords : [])
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
        dataZoom: [{ type: 'inside' }, { type: 'slider', height: 12, bottom: 0, showDetail: false }],
        grid: { bottom: 86, left: 40, right: 20, top: 40 },
        xAxis: { data: cityData.value.map(d => d.city), axisLabel: { rotate: 45, interval: 0, hideOverlap: false }, axisTick: { alignWithLabel: true } },
        yAxis: { name: 'K' },
        series: [{ type: 'bar', data: cityData.value.map(d => d.avgSalary), itemStyle: { color: new echarts.graphic.LinearGradient(0,0,0,1, [{offset:0, color:'#6366f1'},{offset:1, color:'#3b82f6'}]), borderRadius: [4, 4, 0, 0] } }]
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
          })),
          radius: '60%',
          center: ['50%', '55%'],
          splitNumber: 4,
          axisName: { color: '#475569', fontSize: 10, fontWeight: 'bold' },
          splitArea: { areaStyle: { color: ['rgba(241,245,249,0.5)', 'rgba(255,255,255,0.5)'] } },
          splitLine: { lineStyle: { color: '#e2e8f0' } },
          axisLine: { lineStyle: { color: '#e2e8f0' } }
        },
        series: [{
          type: 'radar',
          data: [{ value: (eduData.value || []).map(d => d.avgSalary), name: '平均薪资(K)' }],
          itemStyle: { color: '#8b5cf6', borderWidth: 2 },
          lineStyle: { width: 2, color: '#8b5cf6' },
          areaStyle: { color: 'rgba(139, 92, 246, 0.35)' },
          symbolSize: 6
        }]
      }
    },
    {
      instance: expChart,
      option: {
        tooltip: { trigger: 'axis' },
        xAxis: { type: 'category', data: expData.value.map(d => d.experience), boundaryGap: false },
        yAxis: { type: 'value' },
        series: [{ type: 'line', smooth: true, data: expData.value.map(d => d.avgSalary), lineStyle: { width: 4, color: '#10b981' }, areaStyle: { color: new echarts.graphic.LinearGradient(0,0,0,1, [{offset:0, color:'rgba(16, 185, 129, 0.3)'},{offset:1, color:'transparent'}]) } }]
      }
    },
    {
      instance: industryChart,
      option: {
        tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
        color: ['#6366f1', '#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#14b8a6', '#f97316', '#22c55e', '#0ea5e9'],
        legend: {
          type: 'scroll',
          orient: 'vertical',
          right: 0,
          top: 'middle',
          textStyle: { fontSize: 11, color: '#475569' },
          itemWidth: 10,
          itemHeight: 10,
          formatter: function(name) {
            return name.length > 6 ? name.substring(0, 6) + '...' : name;
          }
        },
        series: [{ 
          type: 'pie', 
          roseType: 'radius', 
          radius: ['20%', '75%'], 
          center: ['30%', '50%'], 
          itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 }, 
          label: { show: false }, 
          labelLine: { show: false },
          data: industryData.value.map(d => ({ name: d.industry, value: d.count })) 
        }]
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

watch(leftTab, async () => {
  await nextTick()
  eduChart?.resize()
  industryChart?.resize()
})

watch(rightTab, async () => {
  await nextTick()
  wordCloudChart?.resize()
  skillBarChart?.resize()
})

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
  if (pipelinePollTimer.value) clearInterval(pipelinePollTimer.value)
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

const startStatsPipeline = async (force = false) => {
  topTokensRunning.value = true
  pipelineStatus.value = 'running'
  pipelineMessage.value = '正在提取 Top Tokens'
  if (pipelinePollTimer.value) clearInterval(pipelinePollTimer.value)
  try {
    const runRes = force ? await api.runStatsPipelineForce() : await api.runStatsPipeline()
    const cached = runRes?.data?.data?.cached === true
    if (!force && cached) {
      topTokensRunning.value = false
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
      if (s.status !== 'running') {
        topTokensRunning.value = false
        clearInterval(pipelinePollTimer.value)
        loadPipelineArtifacts()
      }
    }, 1500)
  } catch (e) {
    topTokensRunning.value = false
    pipelineStatus.value = 'failed'
  }
}

const revokePipelineUrls = () => {
  if (pipelineObjectUrls.value.cluster) URL.revokeObjectURL(pipelineObjectUrls.value.cluster)
  pipelineImageUrls.value.cluster = ''
}

const handleSearch = () => loadAllData()
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
  padding: 18px 12px 28px;
  background: radial-gradient(1200px 600px at 20% -10%, rgba(99, 102, 241, 0.12), transparent 60%),
    radial-gradient(900px 500px at 90% 0%, rgba(16, 185, 129, 0.10), transparent 55%),
    linear-gradient(180deg, #f8fafc 0%, #ffffff 60%, #ffffff 100%);
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 驾驶舱布局 (Bento-like 3 Column Layout) */
.cockpit-layout {
  max-width: 1600px;
  margin: 0 auto;
  animation: fadeIn 0.6s cubic-bezier(0.2, 0.8, 0.2, 1);
}

.cockpit-row {
  display: flex;
  align-items: stretch;
}

.col-left, .col-right {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.col-main {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* Header */
.brand-header {
  margin-bottom: 8px;
}
.gradient-text {
  font-size: 28px;
  font-weight: 900;
  background: linear-gradient(135deg, #1e293b 0%, #4f46e5 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  margin: 0 0 4px;
}
.subtitle {
  color: #64748b;
  font-size: 13px;
  margin: 0;
}

/* 核心指标网格 (Bento Box) */
.bento-stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.bento-stat-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05);
  border: 1px solid #f1f5f9;
  transition: all 0.3s;
}
.bento-stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 15px -3px rgba(0,0,0,0.1);
}
.stat-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}
.blue .stat-icon { background: #eff6ff; color: #3b82f6; }
.green .stat-icon { background: #f0fdf4; color: #10b981; }
.orange .stat-icon { background: #fff7ed; color: #f97316; }
.purple .stat-icon { background: #faf5ff; color: #8b5cf6; }

.stat-value {
  font-size: 22px;
  font-weight: 800;
  color: #1e293b;
  line-height: 1.1;
}
.stat-value small { font-size: 12px; color: #94a3b8; margin-left: 4px; }
.stat-label { font-size: 12px; color: #64748b; font-weight: 600; margin-top: 4px; }

/* 筛选舱 */
.filter-cockpit {
  padding: 3px 20px;
  border-radius: 16px;
  background: #ffffff;
  box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05);
  margin-bottom: 0;
}
.filter-form {
  display: flex; 
  flex-wrap: wrap;
  gap: 12px;  
  align-items: center;
}
.filter-form .el-form-item {
  margin: 0;
}
.filter-actions {
  margin-left: auto !important;
}

/* 操作面板 */
.action-panel {
  display: flex;
  gap: 12px;
}
.action-btn {
  flex: 1;
  height: 70px;
  justify-content: center;
  font-weight: bold;
}

/* 通用图表卡片 */
.glass-card {
  border: 1px solid #f1f5f9;
  border-radius: 16px;
  background: #ffffff;
  box-shadow: 0 4px 6px -1px rgba(0,0,0,0.03);
  display: flex;
  flex-direction: column;
}
:deep(.el-card__header) {
  padding: 14px 20px;
  border-bottom: 1px solid #f1f5f9;
  font-weight: 700;
  font-size: 14px;
  color: #334155;
}
:deep(.el-card__body) {
  padding: 16px;
  flex: 1;
  display: flex;
  flex-direction: column;
}
.chart-content {
  height: 240px;
  width: 100%;
}
.large-chart {
  height: 380px;
}
.tab-chart {
  height: 260px;
}
:deep(.dash-tabs .el-tabs__header) {
  margin: 0 0 14px;
}
:deep(.dash-tabs .el-tabs__nav-wrap) {
  padding: 4px;
  background: #f1f5f9;
  border-radius: 14px;
}
:deep(.dash-tabs .el-tabs__nav-wrap::after) {
  display: none;
}
:deep(.dash-tabs .el-tabs__active-bar) {
  display: none;
}
:deep(.dash-tabs .el-tabs__item) {
  height: 34px;
  line-height: 34px;
  border-radius: 12px;
  padding: 0 14px;
  font-weight: 800;
  color: #64748b;
  transition: all 0.2s;
}
:deep(.dash-tabs .el-tabs__item:hover) {
  color: #334155;
}
:deep(.dash-tabs .el-tabs__item.is-active) {
  color: #1e293b;
  background: #ffffff;
  box-shadow: 0 4px 10px rgba(15, 23, 42, 0.08);
}
:deep(.dash-tabs .el-tabs__content) {
  padding: 0;
}

.divider-text {
  font-size: 14px;
  font-weight: 700;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 1px;
}

/* AI 分析抽屉 */
.ai-drawer-content {
  padding: 0 20px 20px;
}
.drawer-card {
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
  height: 480px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8fafc;
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  overflow: hidden;
}

.cluster-img {
  max-width: 100%;
  max-height: 480px;
  border-radius: 12px;
  transition: transform 0.3s;
}

.cluster-img:hover {
  transform: scale(1.02);
}

.model-metrics {
  margin-bottom: 24px;
  background: #f8fafc;
  padding: 20px;
  border-radius: 16px;
  border: 1px solid #e2e8f0;
}

.metric-item {
  margin-bottom: 20px;
}
.metric-item:last-child {
  margin-bottom: 0;
}

.metric-label {
  font-size: 14px;
  color: #475569;
  margin-bottom: 8px;
  font-weight: 700;
  display: flex;
  justify-content: space-between;
}

.sub-title {
  font-size: 15px;
  font-weight: 800;
  color: #1e293b;
  margin: 24px 0 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.sub-title::before {
  content: "";
  display: block;
  width: 4px;
  height: 16px;
  background: #6366f1;
  border-radius: 2px;
}

.sub-title.error::before { background: #ef4444; }
.sub-title.error { color: #ef4444; }

.token-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 16px;
  background: #ffffff;
  border-radius: 16px;
  border: 1px dashed #e2e8f0;
}

.token-chip {
  background: linear-gradient(135deg, #f1f5f9 0%, #e2e8f0 100%);
  padding: 8px 14px;
  border-radius: 12px;
  font-size: 13px;
  color: #334155;
  font-weight: 700;
  border: 1px solid #cbd5e1;
  transition: all 0.2s;
  cursor: default;
}

.token-chip:hover {
  background: #4f46e5;
  color: #ffffff;
  border-color: #4f46e5;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.2);
}

.token-chip:hover .token-count {
  color: #e0e7ff;
  background: rgba(255, 255, 255, 0.2);
}

.token-count {
  color: #4f46e5;
  margin-left: 6px;
  background: #ffffff;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.05);
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
