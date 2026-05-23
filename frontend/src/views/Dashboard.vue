<template>
  <div class="u-page" id="dashboard-content">
    <el-row :gutter="24" class="cockpit-row">
      <!-- 左侧控制台: 核心指标与玫瑰图 -->
      <el-col :xs="24" :lg="7" class="col-left">
        <div class="jd-page-head">
          <div class="jd-page-head__title jd-page-head__title-text u-title">📊 数据智能洞察</div>
          <div class="jd-page-head__desc">全网招聘动态实时监控</div>
        </div>
        
        <div class="bento-stats">
          <div v-for="stat in mainStats" :key="stat.label" class="bento-stat-card u-card u-card-pad" :class="stat.type">
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
              <div v-show="leftTab === 'industry'" v-loading="loading" ref="industryChartRef" class="jd-chart jd-chart-md"></div>
            </el-tab-pane>
            <el-tab-pane label="🎓 学历" name="edu">
              <div v-show="leftTab === 'edu'" v-loading="loading" ref="eduChartRef" class="jd-chart jd-chart-md"></div>
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
          <div v-loading="loading" ref="cityChartRef" class="jd-chart jd-chart-lg"></div>
        </el-card>

        <el-card class="glass-card chart-box" shadow="never">
          <template #header>💼 经验成长薪资曲线</template>
          <div v-loading="loading" ref="expChartRef" class="jd-chart jd-chart-sm"></div>
        </el-card>
      </el-col>

      <!-- 右侧侧翼: 操作区与技能分析 -->
      <el-col :xs="24" :lg="6" class="col-right">
        <div class="action-panel">
          <el-button type="primary" class="action-btn" plain round @click="aiDrawerVisible = true">
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
              <div v-show="rightTab === 'wordcloud'" v-loading="loading" ref="wordCloudRef" class="jd-chart jd-chart-md"></div>
            </el-tab-pane>
            <el-tab-pane label="⭐ Top10" name="top10">
              <div v-show="rightTab === 'top10'" v-loading="loading" ref="skillBarRef" class="jd-chart jd-chart-md"></div>
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
                <span class="u-primary">{{ (pipelineSummary?.mlp_val_acc * 100 || 0).toFixed(1) }}%</span>
              </div>
              <div class="metric-progress">
                <el-progress :percentage="Number((pipelineSummary?.mlp_val_acc * 100 || 0).toFixed(1))" color="var(--c-primary-600)" :stroke-width="12" :show-text="false" />
              </div>
            </div>
            <div class="metric-item">
              <div class="metric-label">
                <span>TextCNN 预测准确率</span>
                <span class="u-accent">{{ (pipelineSummary?.textcnn_val_acc * 100 || 0).toFixed(1) }}%</span>
              </div>
              <div class="metric-progress">
                <el-progress :percentage="Number((pipelineSummary?.textcnn_val_acc * 100 || 0).toFixed(1))" color="var(--c-accent-500)" :stroke-width="12" :show-text="false" />
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
defineOptions({ name: 'Dashboard' })

import { ref, onMounted, onUnmounted, computed, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import 'echarts-wordcloud'
import { 
  Search, RefreshLeft, Briefcase, Money, Location, 
  Download, Filter, Warning, RefreshRight, DataLine
} from '@element-plus/icons-vue'
import api from '../api.js'
import { ElMessage } from 'element-plus'
import { exportToPDFMultiPage } from '../utils/exportPdf.js'

const router = useRouter()

const cssVar = (name, fallback) => {
  if (typeof window === 'undefined') return fallback
  try {
    const v = getComputedStyle(document.documentElement).getPropertyValue(name)
    const t = String(v || '').trim()
    return t || fallback
  } catch {
    return fallback
  }
}

const theme = () => ({
  ink2: cssVar('--c-ink-2', '#334155'),
  ink3: cssVar('--c-ink-3', '#64748b'),
  border: cssVar('--c-border', 'rgba(15, 23, 42, 0.10)'),
  primary: cssVar('--c-primary-600', '#0f766e'),
  primary2: cssVar('--c-primary-500', '#14b8a6'),
  accent: cssVar('--c-accent-500', '#ff6b4a'),
  info: cssVar('--c-info', '#2563eb'),
  success: cssVar('--c-success', '#16a34a'),
  warning: cssVar('--c-warning', '#f59e0b'),
  danger: cssVar('--c-danger', '#dc2626')
})

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
])

const pipelineStatusText = computed(() => {
  if (pipelineStatus.value === 'running') return '模型计算中'
  if (pipelineStatus.value === 'failed') return '训练异常'
  return '模型已就绪'
})

const colors = (percentage) => {
  const t = theme()
  if (percentage < 30) return t.warning
  if (percentage < 70) return t.primary
  return t.success
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
  const t = theme()
  const chartConfigs = [
    { 
      instance: cityChart, 
      option: {
        tooltip: { trigger: 'axis', appendToBody: true, confine: true, extraCssText: 'z-index: 99999;' },
        dataZoom: [{ type: 'inside' }, { type: 'slider', height: 12, bottom: 0, showDetail: false }],
        grid: { bottom: 86, left: 40, right: 20, top: 40 },
        xAxis: { data: cityData.value.map(d => d.city), axisLabel: { rotate: 45, interval: 0, hideOverlap: false }, axisTick: { alignWithLabel: true } },
        yAxis: { name: 'K' },
        series: [{ type: 'bar', data: cityData.value.map(d => d.avgSalary), itemStyle: { color: new echarts.graphic.LinearGradient(0,0,0,1, [{ offset: 0, color: t.primary2 }, { offset: 1, color: t.info }]), borderRadius: [6, 6, 0, 0] } }]
      }
    },
    {
      instance: eduChart,
      option: {
        tooltip: { trigger: 'item', appendToBody: true, confine: true, extraCssText: 'z-index: 99999;' },
        radar: {
          indicator: (eduData.value || []).map(d => ({
            name: d.education,
            max: Math.max(1, ...(eduData.value || []).map(x => Number(x.avgSalary || 0))) * 1.2
          })),
          radius: '60%',
          center: ['50%', '55%'],
          splitNumber: 4,
          axisName: { color: t.ink2, fontSize: 10, fontWeight: 'bold' },
          splitArea: { areaStyle: { color: ['rgba(255,255,255,0.55)', 'rgba(255,255,255,0.30)'] } },
          splitLine: { lineStyle: { color: t.border } },
          axisLine: { lineStyle: { color: t.border } }
        },
        series: [{
          type: 'radar',
          data: [{ value: (eduData.value || []).map(d => d.avgSalary), name: '平均薪资(K)' }],
          itemStyle: { color: t.accent, borderWidth: 2 },
          lineStyle: { width: 2, color: t.accent },
          areaStyle: { color: 'rgba(255, 107, 74, 0.28)' },
          symbolSize: 6
        }]
      }
    },
    {
      instance: expChart,
      option: {
        tooltip: { trigger: 'axis', appendToBody: true, confine: true, extraCssText: 'z-index: 99999;' },
        xAxis: { type: 'category', data: expData.value.map(d => d.experience), boundaryGap: false },
        yAxis: { type: 'value' },
        series: [{ type: 'line', smooth: true, data: expData.value.map(d => d.avgSalary), lineStyle: { width: 4, color: t.success }, areaStyle: { color: new echarts.graphic.LinearGradient(0,0,0,1, [{ offset: 0, color: 'rgba(22, 163, 74, 0.28)' }, { offset: 1, color: 'transparent' }]) } }]
      }
    },
    {
      instance: industryChart,
      option: {
        tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)', appendToBody: true, confine: true, extraCssText: 'z-index: 99999;' },
        color: [
          t.primary,
          t.accent,
          t.info,
          t.success,
          t.warning,
          t.danger,
          t.primary2,
          'rgba(15, 118, 110, 0.55)',
          'rgba(255, 107, 74, 0.55)',
          'rgba(37, 99, 235, 0.55)'
        ],
        legend: {
          type: 'scroll',
          orient: 'vertical',
          right: 0,
          top: 'middle',
          textStyle: { fontSize: 11, color: t.ink2 },
          itemWidth: 10,
          itemHeight: 10,
          formatter: function(name) {
            return name.length > 6 ? name.substring(0, 6) + '...' : name;
          }
        },
        series: [{
          type: 'pie',
          radius: ['48%', '74%'],
          center: ['34%', '50%'],
          itemStyle: { borderRadius: 6, borderColor: 'rgba(255,255,255,0.92)', borderWidth: 2 },
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
          textStyle: { fontFamily: 'IBM Plex Sans, sans-serif', fontWeight: 'bold', color: () => `hsl(${Math.random() * 360}, 64%, 54%)` },
          data: keywordData.value.map(d => ({ name: d.keyword, value: d.count }))
        }]
      }
    },
    {
      instance: skillBarChart,
      option: {
        tooltip: { trigger: 'axis', appendToBody: true, confine: true, extraCssText: 'z-index: 99999;' },
        grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: { type: 'value' },
        yAxis: { type: 'category', data: keywordData.value.slice(0, 10).reverse().map(d => d.keyword) },
        series: [{ type: 'bar', data: keywordData.value.slice(0, 10).reverse().map(d => d.count), itemStyle: { color: new echarts.graphic.LinearGradient(0,0,1,0, [{ offset: 0, color: t.primary }, { offset: 1, color: t.accent }]) }, barWidth: 14, emphasis: { focus: 'series' } }]
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
.cockpit-row { align-items: stretch; }
.col-left, .col-main, .col-right { display: flex; flex-direction: column; gap: var(--space-5); }

.bento-stats { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-4); }
.bento-stat-card { transition: transform 200ms var(--ease-out), box-shadow 200ms var(--ease-out); }
.bento-stat-card:hover { transform: translateY(-2px); box-shadow: var(--shadow-md), var(--shadow-inset); }
.bento-stat-card:first-child { grid-column: 1 / -1; display: flex; align-items: center; justify-content: space-between; }
.bento-stat-card:first-child .stat-info { text-align: right; }
.bento-stat-card:first-child .stat-value { font-size: 26px; }
.stat-icon { width: 42px; height: 42px; border-radius: 16px; display: grid; place-items: center; font-size: 18px; }
.blue .stat-icon { background: rgba(37, 99, 235, 0.12); color: var(--c-info); }
.green .stat-icon { background: rgba(22, 163, 74, 0.12); color: var(--c-success); }
.orange .stat-icon { background: rgba(245, 158, 11, 0.14); color: var(--c-warning); }
.purple .stat-icon { background: rgba(15, 118, 110, 0.12); color: var(--c-primary-700); }
.stat-value { font-size: 22px; font-weight: 900; letter-spacing: -0.02em; line-height: 1.1; }
.stat-value small { font-size: 12px; color: var(--c-ink-3); margin-left: 6px; font-weight: 700; }
.stat-label { font-size: 12px; color: var(--c-ink-3); font-weight: 700; margin-top: 4px; }

.filter-form { display: flex; flex-wrap: wrap; gap: var(--space-3); align-items: center; }
.filter-form :deep(.el-form-item) { margin: 0; }
.filter-actions { margin-left: auto !important; }

.action-panel { display: flex; gap: var(--space-3); }
.action-btn { flex: 1; height: 56px; justify-content: center; font-weight: 800; }

.ai-drawer-content { padding: 0 var(--space-4) var(--space-4); }
.card-header-box { display: flex; justify-content: space-between; align-items: center; gap: var(--space-3); }
.title-group { display: inline-flex; align-items: center; gap: var(--space-2); }
.icon-dot { width: 8px; height: 8px; border-radius: 50%; }
.icon-dot.blue { background: var(--c-info); box-shadow: 0 0 10px rgba(37, 99, 235, 0.35); }
.icon-dot.purple { background: var(--c-accent-500); box-shadow: 0 0 10px rgba(255, 107, 74, 0.35); }
.title-text { font-weight: 800; color: var(--c-ink); }

.cluster-visual { height: 420px; display: grid; place-items: center; border-radius: var(--radius-lg); background: rgba(255, 255, 255, 0.55); border: 1px dashed var(--c-border-2); overflow: hidden; }
.cluster-img { max-width: 100%; max-height: 420px; border-radius: 14px; }

.model-metrics { margin-bottom: var(--space-5); background: rgba(255, 255, 255, 0.55); padding: var(--space-5); border-radius: var(--radius-lg); border: 1px solid var(--c-border); }
.metric-item { margin-bottom: var(--space-5); }
.metric-item:last-child { margin-bottom: 0; }
.metric-label { font-size: 13px; color: var(--c-ink-2); margin-bottom: 8px; font-weight: 800; display: flex; justify-content: space-between; gap: var(--space-3); }

.sub-title { font-size: 14px; font-weight: 900; color: var(--c-ink); margin: var(--space-5) 0 var(--space-4); display: flex; align-items: center; gap: var(--space-2); }
.sub-title.error { color: var(--c-danger); }

.token-cloud { display: flex; flex-wrap: wrap; gap: 10px; padding: var(--space-4); background: rgba(255, 255, 255, 0.55); border-radius: var(--radius-lg); border: 1px dashed var(--c-border); }
.token-chip { background: rgba(255, 255, 255, 0.74); padding: 8px 12px; border-radius: 999px; font-size: 12px; color: var(--c-ink-2); font-weight: 800; border: 1px solid var(--c-border); }
.token-count { color: var(--c-primary-700); margin-left: 6px; }

.error-list { background: rgba(220, 38, 38, 0.08); padding: var(--space-3); border-radius: var(--radius-md); font-size: 12px; color: var(--c-danger); }
.error-item { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.error-item:last-child { margin-bottom: 0; }

@media (max-width: 640px) {
  .bento-stats { grid-template-columns: 1fr; }
  .action-panel { flex-direction: column; }
}
</style>
