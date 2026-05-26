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
        <el-card class="drawer-card viz-card" shadow="never" style="margin-bottom: 24px;">
          <template #header>
            <div class="card-header-box">
              <div class="chart-header-actions">
                <el-select
                  v-model="selectedDeepChart"
                  class="chart-select"
                  size="small"
                  popper-class="chart-select-popper"
                >
                  <el-option
                    v-for="opt in deepChartOptions"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
                <el-button type="primary" plain round @click="startPipeline(true)" :loading="pipelineRunning">
                  <el-icon><RefreshRight /></el-icon> 重新扫描数据
                </el-button>
              </div>
            </div>
          </template>
          <div v-show="selectedDeepChart === 'cluster'" class="cluster-visual">
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

          <div v-show="selectedDeepChart === 'heatmap'" class="heatmap-visual">
            <div v-if="jobSkillHeatmapReady" ref="jobSkillHeatmapRef" class="jd-chart heatmap-chart"></div>
            <el-empty v-else :image-size="60" description="暂无热力图，请点击重新扫描" />
          </div>

          <div v-show="selectedDeepChart === 'timeline'" class="timeline-visual">
            <div v-if="skillTimelineReady" ref="skillTimelineRef" class="jd-chart timeline-chart"></div>
            <el-empty v-else :image-size="60" description="暂无趋势数据，请点击重新扫描" />
          </div>

          <div v-show="selectedDeepChart === 'company_size_salary'" class="size-salary-visual">
            <div v-if="companySizeSalaryReady" ref="companySizeSalaryRef" class="jd-chart size-salary-chart"></div>
            <el-empty v-else :image-size="60" description="暂无公司规模薪资数据，请点击重新扫描" />
          </div>

          <div v-show="selectedDeepChart === 'edu_exp_bubble'" class="bubble-visual">
            <div v-if="eduExpSalaryBubbleReady" ref="eduExpSalaryBubbleRef" class="jd-chart bubble-chart"></div>
            <el-empty v-else :image-size="60" description="暂无学历经验薪资数据，请点击重新扫描" />
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
const jobSkillHeatmapPayload = ref(null)
const jobSkillHeatmapReady = computed(() => Boolean(jobSkillHeatmapPayload.value && jobSkillHeatmapPayload.value.x && jobSkillHeatmapPayload.value.y))
const skillTimelinePayload = ref(null)
const skillTimelineReady = computed(() => Boolean(skillTimelinePayload.value && skillTimelinePayload.value.months && skillTimelinePayload.value.series))
const companySizeSalaryPayload = ref(null)
const companySizeSalaryReady = computed(() => Boolean(companySizeSalaryPayload.value && companySizeSalaryPayload.value.x && companySizeSalaryPayload.value.avg_salary))
const eduExpSalaryBubblePayload = ref(null)
const eduExpSalaryBubbleReady = computed(() => Boolean(eduExpSalaryBubblePayload.value && eduExpSalaryBubblePayload.value.x && eduExpSalaryBubblePayload.value.y))
const deepChartOptions = [
  { value: 'cluster', label: 'NLP 语义聚类地图' },
  { value: 'heatmap', label: '岗位-技能热力图' },
  { value: 'timeline', label: '技术招聘趋势' },
  { value: 'company_size_salary', label: '公司规模-薪资' },
  { value: 'edu_exp_bubble', label: '学历 × 经验 薪资气泡图' },
]
const selectedDeepChart = ref(deepChartOptions[0]?.value || 'cluster')
const leftTab = ref('industry')
const rightTab = ref('wordcloud')

// 图表实例与引用
const cityChartRef = ref(null)
const eduChartRef = ref(null)
const expChartRef = ref(null)
const industryChartRef = ref(null)
const wordCloudRef = ref(null)
const skillBarRef = ref(null)
const jobSkillHeatmapRef = ref(null)
const skillTimelineRef = ref(null)
const companySizeSalaryRef = ref(null)
const eduExpSalaryBubbleRef = ref(null)

let cityChart = null
let eduChart = null
let expChart = null
let industryChart = null
let wordCloudChart = null
let skillBarChart = null
let jobSkillHeatmapChart = null
let skillTimelineChart = null
let companySizeSalaryChart = null
let eduExpSalaryBubbleChart = null

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

const ensureHeatmapChart = async () => {
  await nextTick()
  if (!jobSkillHeatmapRef.value) return false
  if (!jobSkillHeatmapChart) {
    jobSkillHeatmapChart = echarts.init(jobSkillHeatmapRef.value)
  }
  return true
}

const renderJobSkillHeatmap = async () => {
  const ok = await ensureHeatmapChart()
  if (!ok || !jobSkillHeatmapChart) return
  const t = theme()
  const p = jobSkillHeatmapPayload.value
  if (!p || !Array.isArray(p.x) || !Array.isArray(p.y) || !Array.isArray(p.data)) return

  const x = p.x
  const y = p.y
  const max = Number(p.max || 0)
  const maxClip = Number(p.max_clip || 0) || max
  jobSkillHeatmapChart.setOption(
    {
      tooltip: {
        position: 'top',
        appendToBody: true,
        confine: true,
        extraCssText: 'z-index: 99999;',
        formatter: (params) => {
          const v = Array.isArray(params?.value) ? params.value : []
          const xi = Number(v[0])
          const yi = Number(v[1])
          const val = Number(v[2] || 0)
          const sx = x[xi] ?? ''
          const sy = y[yi] ?? ''
          return `${sy}<br/>${sx}: ${val}`
        }
      },
      grid: { top: 70, left: 20, right: 30, bottom: 90, containLabel: true },
      xAxis: {
        type: 'category',
        data: x,
        axisLabel: {
          interval: 0,
          rotate: 45,
          color: t.ink2,
          fontWeight: '600',
          fontSize: 10,
          align: 'right',
          verticalAlign: 'middle',
          formatter: (v) => {
            const s = String(v ?? '')
            return s.length > 12 ? `${s.slice(0, 10)}…` : s
          }
        },
        splitArea: { show: true },
        axisTick: { show: false },
        axisLine: { lineStyle: { color: t.border } },
      },
      yAxis: {
        type: 'category',
        data: y,
        axisLabel: {
          color: '#1e293b',
          fontWeight: '800',
          fontSize: 11,
          align: 'right',
          margin: 12,
          formatter: (v) => {
            const s = String(v ?? '')
            return s.length > 15 ? `${s.slice(0, 12)}…` : s
          }
        },
        splitArea: { show: true },
        axisTick: { show: false },
        axisLine: { lineStyle: { color: t.border } },
      },
      dataZoom: [
        { type: 'inside', xAxisIndex: 0 },
        { type: 'inside', yAxisIndex: 0 },
        { 
          type: 'slider', 
          xAxisIndex: 0, 
          height: 14, 
          bottom: 45, 
          handleIcon: 'path://M10.7,11.9v-1.3H9.3v1.3c-4.9,0.3-8.8,4.4-8.8,9.4c0,5,3.9,9.1,8.8,9.4v1.3h1.3v-1.3c4.9-0.3,8.8-4.4,8.8-9.4C19.5,16.3,15.6,12.2,10.7,11.9z M13.3,24.4H6.7V23h6.6V24.4z M13.3,19.6H6.7v-1.4h6.6V19.6z',
          handleSize: '80%',
          showDetail: false 
        }
      ],
      visualMap: {
        min: 0,
        max: Math.max(1, maxClip),
        calculable: true,
        orient: 'horizontal',
        left: 'center',
        top: 10,
        itemWidth: 15,
        itemHeight: 120,
        inRange: { 
          color: ['#f0f9ff', '#bae6fd', '#7dd3fc', '#38bdf8', '#0284c7', '#0369a1'] 
        },
        text: ['高频', '低频'],
        textStyle: { color: t.ink2, fontWeight: '700', fontSize: 11 },
      },
      series: [
        {
          name: '技能热度',
          type: 'heatmap',
          data: p.data.map((d) => {
            const xi = Number(d?.[0])
            const yi = Number(d?.[1])
            const val = Number(d?.[2] || 0)
            return [xi, yi, Math.min(val, maxClip)]
          }),
          label: { show: false },
          emphasis: { 
            itemStyle: { 
              shadowBlur: 10, 
              shadowColor: 'rgba(0,0,0,0.3)',
              borderColor: '#fff',
              borderWidth: 2
            } 
          },
        },
      ],
    },
    true
  )
}

const ensureTimelineChart = async () => {
  await nextTick()
  if (!skillTimelineRef.value) return false
  if (!skillTimelineChart) {
    skillTimelineChart = echarts.init(skillTimelineRef.value)
  }
  return Boolean(skillTimelineChart)
}

const renderSkillTimeline = async () => {
  const ok = await ensureTimelineChart()
  if (!ok || !skillTimelineChart) return
  const t = theme()
  const p = skillTimelinePayload.value
  if (!p || !Array.isArray(p.months) || !Array.isArray(p.series)) return

  const lineColors = [
    '#0ea5e9', '#f43f5e', '#10b981', '#f59e0b', 
    '#8b5cf6', '#ec4899', '#06b6d4', '#f97316', 
    '#84cc16', '#6366f1'
  ]

  skillTimelineChart.setOption(
    {
      tooltip: {
        trigger: 'axis',
        appendToBody: true,
        confine: true,
        backgroundColor: 'rgba(255, 255, 255, 0.96)',
        borderColor: t.border,
        borderWidth: 1,
        textStyle: { color: t.ink2, fontSize: 13 },
        extraCssText: 'z-index: 99999; box-shadow: 0 4px 12px rgba(0,0,0,0.1);',
        axisPointer: {
          type: 'line',
          lineStyle: { color: t.primary, width: 1, type: 'dashed' }
        }
      },
      legend: {
        data: p.series.map((s) => s.name),
        top: 10,
        textStyle: { color: t.ink2, fontWeight: '700', fontSize: 12 },
        type: 'scroll',
        itemGap: 15,
        itemWidth: 12,
        itemHeight: 12,
      },
      grid: { 
        top: 70, 
        left: 20, 
        right: 40, 
        bottom: 80, 
        containLabel: true 
      },
      xAxis: {
        type: 'category',
        data: p.months,
        boundaryGap: false,
        axisLabel: {
          rotate: 35,
          color: t.ink3,
          fontWeight: '600',
          fontSize: 10,
          margin: 12,
          formatter: (v) => {
            const parts = String(v || '').split('-')
            if (parts.length === 2) {
              return `${parts[0].slice(2)}/${parts[1]}`
            }
            return v
          }
        },
        axisLine: { lineStyle: { color: t.border } },
        axisTick: { show: false },
      },
      yAxis: {
        type: 'value',
        name: '招聘热度',
        nameTextStyle: { 
          color: t.ink3, 
          fontWeight: '700', 
          fontSize: 12,
          padding: [0, 0, 0, 40]
        },
        axisLabel: { 
          color: t.ink3,
          fontWeight: '600',
          fontSize: 11
        },
        splitLine: { 
          show: true,
          lineStyle: { color: t.border, type: 'dashed', dashOffset: 5 } 
        },
        axisLine: { show: false },
      },
      dataZoom: [
        { 
          type: 'inside', 
          xAxisIndex: 0 
        },
        { 
          type: 'slider', 
          xAxisIndex: 0, 
          height: 18, 
          bottom: 15, 
          borderColor: 'transparent',
          fillerColor: 'rgba(15, 118, 110, 0.1)',
          handleIcon: 'path://M10.7,11.9v-1.3H9.3v1.3c-4.9,0.3-8.8,4.4-8.8,9.4c0,5,3.9,9.1,8.8,9.4v1.3h1.3v-1.3c4.9-0.3,8.8-4.4,8.8-9.4C19.5,16.3,15.6,12.2,10.7,11.9z M13.3,24.4H6.7V23h6.6V24.4z M13.3,19.6H6.7v-1.4h6.6V19.6z',
          handleSize: '80%',
          handleStyle: {
            color: t.primary,
            shadowBlur: 3,
            shadowColor: 'rgba(0, 0, 0, 0.2)',
          },
          textStyle: { color: t.ink3, fontWeight: '600' },
          showDetail: false 
        }
      ],
      series: p.series.map((s, idx) => {
        const color = lineColors[idx % lineColors.length]
        return {
          name: s.name,
          type: 'line',
          data: s.data,
          smooth: 0.4,
          symbol: 'circle',
          symbolSize: 4,
          showSymbol: false,
          lineStyle: { width: 3, color: color },
          itemStyle: { color: color },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: color + '44' },
              { offset: 1, color: color + '00' }
            ])
          },
          emphasis: {
            focus: 'series',
            itemStyle: { 
              borderColor: '#fff', 
              borderWidth: 2,
              shadowBlur: 10,
              shadowColor: color
            },
            lineStyle: { width: 4 }
          },
        }
      }),
    },
    true
  )
}

const ensureCompanySizeSalaryChart = async () => {
  await nextTick()
  if (!companySizeSalaryRef.value) return false
  if (!companySizeSalaryChart) {
    companySizeSalaryChart = echarts.init(companySizeSalaryRef.value)
  }
  return Boolean(companySizeSalaryChart)
}

const renderCompanySizeSalary = async () => {
  const ok = await ensureCompanySizeSalaryChart()
  if (!ok || !companySizeSalaryChart) return
  const t = theme()
  const p = companySizeSalaryPayload.value
  if (!p || !Array.isArray(p.x) || !Array.isArray(p.avg_salary) || !Array.isArray(p.count)) return

  const x = p.x
  const salary = p.avg_salary.map(v => Number(v || 0))
  const count = p.count.map(v => Number(v || 0))

  companySizeSalaryChart.setOption(
    {
      tooltip: {
        trigger: 'axis',
        appendToBody: true,
        confine: true,
        extraCssText: 'z-index: 99999;',
        formatter: (params) => {
          const rows = Array.isArray(params) ? params : []
          const idx = Number(rows?.[0]?.dataIndex ?? 0)
          const s = salary[idx] ?? 0
          const c = count[idx] ?? 0
          const label = x[idx] ?? ''
          return `${label}<br/>平均薪资: ${s}${p.unit || 'K'}<br/>岗位数量: ${c}`
        }
      },
      grid: { top: 36, left: 20, right: 20, bottom: 70, containLabel: true },
      xAxis: {
        type: 'category',
        data: x,
        axisLabel: {
          interval: 0,
          rotate: 20,
          color: t.ink2,
          fontWeight: '700',
          fontSize: 11,
        },
        axisTick: { show: false },
        axisLine: { lineStyle: { color: t.border } },
      },
      yAxis: {
        type: 'value',
        name: `平均薪资(${p.unit || 'K'})`,
        nameTextStyle: { color: t.ink3, fontWeight: '700', fontSize: 12 },
        axisLabel: { color: t.ink3, fontWeight: '600' },
        splitLine: { lineStyle: { color: t.border, type: 'dashed' } },
      },
      dataZoom: [
        { type: 'inside', xAxisIndex: 0 },
        { type: 'slider', xAxisIndex: 0, height: 14, bottom: 18, showDetail: false }
      ],
      series: [
        {
          name: '平均薪资',
          type: 'bar',
          data: salary,
          barWidth: 16,
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: t.primary2 },
              { offset: 1, color: t.info },
            ]),
            borderRadius: [6, 6, 0, 0]
          },
          emphasis: { focus: 'series' },
        }
      ]
    },
    true
  )
}

const ensureEduExpSalaryBubbleChart = async () => {
  await nextTick()
  if (!eduExpSalaryBubbleRef.value) return false
  if (!eduExpSalaryBubbleChart) {
    eduExpSalaryBubbleChart = echarts.init(eduExpSalaryBubbleRef.value)
  }
  return Boolean(eduExpSalaryBubbleChart)
}

const renderEduExpSalaryBubble = async () => {
  const ok = await ensureEduExpSalaryBubbleChart()
  if (!ok || !eduExpSalaryBubbleChart) return
  const t = theme()
  const p = eduExpSalaryBubblePayload.value
  if (!p || !Array.isArray(p.x) || !Array.isArray(p.y) || !Array.isArray(p.data)) return

  const x = p.x
  const y = p.y
  const maxCount = Math.max(1, Number(p.max_count || 0))
  const salaryMin = Number(p.salary_min || 0)
  const salaryMax = Math.max(salaryMin + 1, Number(p.salary_max || 0))

  eduExpSalaryBubbleChart.setOption(
    {
      tooltip: {
        trigger: 'item',
        appendToBody: true,
        confine: true,
        extraCssText: 'z-index: 99999;',
        formatter: (params) => {
          const v = Array.isArray(params?.value) ? params.value : []
          const xi = Number(v[0])
          const yi = Number(v[1])
          const cnt = Number(v[2] || 0)
          const sal = Number(v[3] || 0)
          const sx = x[xi] ?? ''
          const sy = y[yi] ?? ''
          return `${sy} / ${sx}<br/>平均薪资: ${sal}${p.unit || 'K'}<br/>岗位数量: ${cnt}`
        }
      },
      grid: { top: 30, left: 20, right: 30, bottom: 80, containLabel: true },
      xAxis: {
        type: 'category',
        data: x,
        axisLabel: {
          interval: 0,
          rotate: 25,
          color: t.ink2,
          fontWeight: '700',
          fontSize: 11,
        },
        axisTick: { show: false },
        axisLine: { lineStyle: { color: t.border } },
        splitLine: { show: false },
      },
      yAxis: {
        type: 'category',
        data: y,
        axisLabel: {
          color: t.ink2,
          fontWeight: '800',
          fontSize: 11,
          margin: 14,
        },
        axisTick: { show: false },
        axisLine: { lineStyle: { color: t.border } },
        splitLine: { show: true, lineStyle: { color: t.border, type: 'dashed' } },
      },
      dataZoom: [
        { type: 'inside', xAxisIndex: 0 },
        { type: 'slider', xAxisIndex: 0, height: 14, bottom: 18, showDetail: false }
      ],
      visualMap: {
        min: salaryMin,
        max: salaryMax,
        dimension: 3,
        orient: 'horizontal',
        left: 'center',
        bottom: 48,
        itemWidth: 15,
        itemHeight: 110,
        inRange: { color: ['#eff6ff', '#93c5fd', '#3b82f6', '#1d4ed8', '#f97316', '#dc2626'] },
        textStyle: { color: t.ink2, fontWeight: '700', fontSize: 11 },
      },
      series: [
        {
          type: 'scatter',
          data: (p.data || []).map(d => [Number(d?.[0]), Number(d?.[1]), Number(d?.[2] || 0), Number(d?.[3] || 0)]),
          symbolSize: (val) => {
            const cnt = Number(Array.isArray(val) ? val[2] : 0)
            const r = Math.sqrt(Math.max(0, cnt) / maxCount)
            return 10 + 26 * r
          },
          emphasis: {
            itemStyle: {
              borderColor: '#fff',
              borderWidth: 2,
              shadowBlur: 10,
              shadowColor: 'rgba(0,0,0,0.25)'
            }
          },
        }
      ],
    },
    true
  )
}

const renderSelectedDeepChart = async () => {
  if (!aiDrawerVisible.value) return
  await nextTick()
  const k = selectedDeepChart.value
  if (k === 'heatmap') {
    if (jobSkillHeatmapPayload.value) {
      await renderJobSkillHeatmap()
      jobSkillHeatmapChart?.resize()
    }
    return
  }
  if (k === 'timeline') {
    if (skillTimelinePayload.value) {
      await renderSkillTimeline()
      skillTimelineChart?.resize()
    }
    return
  }
  if (k === 'company_size_salary') {
    if (companySizeSalaryPayload.value) {
      await renderCompanySizeSalary()
      companySizeSalaryChart?.resize()
    }
    return
  }
  if (k === 'edu_exp_bubble') {
    if (eduExpSalaryBubblePayload.value) {
      await renderEduExpSalaryBubble()
      eduExpSalaryBubbleChart?.resize()
    }
    return
  }
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

watch(aiDrawerVisible, async (v) => {
  if (!v) return
  await renderSelectedDeepChart()
})

watch(selectedDeepChart, async () => {
  await renderSelectedDeepChart()
})

const handleResize = () => {
  ;[
    cityChart,
    eduChart,
    expChart,
    industryChart,
    wordCloudChart,
    skillBarChart,
    jobSkillHeatmapChart,
    skillTimelineChart,
    companySizeSalaryChart,
    eduExpSalaryBubbleChart
  ].forEach(c => c?.resize())
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
  if (data.artifacts?.job_skill_heatmap_json) {
    const blobRes = await api.getPipelineFile('job_skill_heatmap_json')
    if (blobRes?.data) {
      const txt = await blobRes.data.text()
      const parsed = JSON.parse(txt)
      jobSkillHeatmapPayload.value = parsed
      if (aiDrawerVisible.value && selectedDeepChart.value === 'heatmap') {
        await renderJobSkillHeatmap()
      }
    }
  }
  if (data.artifacts?.skill_timeline_json) {
    const blobRes = await api.getPipelineFile('skill_timeline_json')
    if (blobRes?.data) {
      const txt = await blobRes.data.text()
      const parsed = JSON.parse(txt)
      skillTimelinePayload.value = parsed
      if (aiDrawerVisible.value && selectedDeepChart.value === 'timeline') {
        await renderSkillTimeline()
      }
    }
  }
  if (data.artifacts?.company_size_salary_json) {
    const blobRes = await api.getPipelineFile('company_size_salary_json')
    if (blobRes?.data) {
      const txt = await blobRes.data.text()
      const parsed = JSON.parse(txt)
      companySizeSalaryPayload.value = parsed
      if (aiDrawerVisible.value && selectedDeepChart.value === 'company_size_salary') {
        await renderCompanySizeSalary()
      }
    }
  }
  if (data.artifacts?.edu_exp_salary_bubble_json) {
    const blobRes = await api.getPipelineFile('edu_exp_salary_bubble_json')
    if (blobRes?.data) {
      const txt = await blobRes.data.text()
      const parsed = JSON.parse(txt)
      eduExpSalaryBubblePayload.value = parsed
      if (aiDrawerVisible.value && selectedDeepChart.value === 'edu_exp_bubble') {
        await renderEduExpSalaryBubble()
      }
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
  if (jobSkillHeatmapChart) {
    jobSkillHeatmapChart.dispose()
    jobSkillHeatmapChart = null
  }
  if (skillTimelineChart) {
    skillTimelineChart.dispose()
    skillTimelineChart = null
  }
  if (companySizeSalaryChart) {
    companySizeSalaryChart.dispose()
    companySizeSalaryChart = null
  }
  if (eduExpSalaryBubbleChart) {
    eduExpSalaryBubbleChart.dispose()
    eduExpSalaryBubbleChart = null
  }
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
.icon-dot.teal { background: var(--c-primary-600); box-shadow: 0 0 10px rgba(15, 118, 110, 0.35); }
.icon-dot.indigo { background: #6366f1; box-shadow: 0 0 10px rgba(99, 102, 241, 0.35); }
.icon-dot.purple { background: var(--c-accent-500); box-shadow: 0 0 10px rgba(255, 107, 74, 0.35); }
.title-text { font-weight: 800; color: var(--c-ink); }
.chart-header-actions { display: inline-flex; align-items: center; gap: var(--space-3); }
.chart-select { width: 220px; }
.chart-select :deep(.el-select__wrapper) { border-radius: 999px; border: 1px solid var(--c-border); background: rgba(255, 255, 255, 0.70); box-shadow: none; }
.chart-select :deep(.el-select__placeholder) { color: var(--c-ink-2); font-weight: 700; }
.chart-select :deep(.el-select__selected-item) { color: var(--c-ink-2); font-weight: 800; }
:deep(.chart-select-popper .el-select-dropdown__item.selected) { font-weight: 900; }

.cluster-visual { height: 420px; display: grid; place-items: center; border-radius: var(--radius-lg); background: rgba(255, 255, 255, 0.55); border: 1px dashed var(--c-border-2); overflow: hidden; }
.cluster-img { max-width: 100%; max-height: 420px; border-radius: 14px; }

.heatmap-visual { height: 520px; border-radius: var(--radius-lg); background: #ffffff; border: 1px solid var(--c-border-2); overflow: hidden; position: relative; }
.heatmap-chart { width: 100%; height: 100%; }

.timeline-visual { height: 400px; border-radius: var(--radius-lg); background: #ffffff; border: 1px solid var(--c-border-2); overflow: hidden; position: relative; }
.timeline-chart { width: 100%; height: 100%; }

.size-salary-visual { height: 340px; border-radius: var(--radius-lg); background: #ffffff; border: 1px solid var(--c-border-2); overflow: hidden; position: relative; }
.size-salary-chart { width: 100%; height: 100%; }

.bubble-visual { height: 380px; border-radius: var(--radius-lg); background: #ffffff; border: 1px solid var(--c-border-2); overflow: hidden; position: relative; }
.bubble-chart { width: 100%; height: 100%; }

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
  .chart-header-actions { width: 100%; justify-content: space-between; }
  .chart-select { width: 100%; }
}
</style>
