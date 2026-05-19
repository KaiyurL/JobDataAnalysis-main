<template>
  <div class="dashboard" id="dashboard-content">
    <div class="page-header">
      <div class="header-left">
        <h1>📊 招聘数据仪表盘</h1>
        <p>可视化展示招聘市场数据，洞察行业趋势</p>
      </div>
      <el-button type="primary" @click="handleExportDashboard" :loading="exporting">
        <el-icon><Download /></el-icon> 导出PDF
      </el-button>
    </div>

    <el-card class="filter-card" shadow="hover">
      <el-form :inline="true" :model="filters" class="filter-form">
        <el-form-item label="岗位关键词">
          <el-input v-model="filters.keyword" placeholder="请输入岗位或公司关键词" clearable />
        </el-form-item>
        <el-form-item label="城市">
          <el-select v-model="filters.selectedCities" multiple placeholder="请选择城市" collapse-tags collapse-tags-tooltip style="width: 250px">
            <el-option v-for="city in cityOptions" :key="city" :label="city" :value="city" />
          </el-select>
        </el-form-item>
        <el-form-item label="学历">
          <el-select v-model="filters.education" placeholder="请选择学历" clearable style="width: 150px">
            <el-option label="不限" value="" />
            <el-option v-for="edu in educationOptions" :key="edu" :label="edu" :value="edu" />
          </el-select>
        </el-form-item>
        <el-form-item label="经验">
          <el-select v-model="filters.experience" placeholder="请选择经验" clearable style="width: 150px">
            <el-option label="不限" value="" />
            <el-option v-for="exp in experienceOptions" :key="exp" :label="exp" :value="exp" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch" :loading="loading">
            <el-icon><Search /></el-icon> 查询
          </el-button>
          <el-button @click="handleReset">
            <el-icon><RefreshLeft /></el-icon> 重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-row :gutter="20" class="stats-cards">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon blue">
              <el-icon><Briefcase /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ totalJobs }}</div>
              <div class="stat-label">总职位数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon green">
              <el-icon><Money /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ avgMaxSalary }} K</div>
              <div class="stat-label">平均最高薪资</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon orange">
              <el-icon><Location /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ topCity }}</div>
              <div class="stat-label">最高薪城市</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon purple">
              <el-icon><Star /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ topSkill }}</div>
              <div class="stat-label">最热门技能</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="charts-row">
      <el-col :xs="24" :lg="12">
        <el-card class="chart-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>🏙️ 城市平均薪资</span>
              <el-tag size="small" type="info" v-if="selectedCityFromChart">当前选中: {{ selectedCityFromChart }}</el-tag>
            </div>
          </template>
          <div v-loading="loading" ref="cityChartRef" class="chart-container"></div>
          <el-empty v-if="!loading && cityData.length === 0" description="暂无数据" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card class="chart-card" shadow="hover">
          <template #header>
            <span>🎓 学历薪资分析</span>
          </template>
          <div v-loading="loading" ref="eduChartRef" class="chart-container"></div>
          <el-empty v-if="!loading && eduData.length === 0" description="暂无数据" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="charts-row">
      <el-col :xs="24" :lg="12">
        <el-card class="chart-card" shadow="hover">
          <template #header>
            <span>💼 经验薪资分析</span>
          </template>
          <div v-loading="loading" ref="expChartRef" class="chart-container"></div>
          <el-empty v-if="!loading && expData.length === 0" description="暂无数据" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card class="chart-card" shadow="hover">
          <template #header>
            <span>🏢 公司类型分布</span>
          </template>
          <div v-loading="loading" ref="industryChartRef" class="chart-container"></div>
          <el-empty v-if="!loading && industryData.length === 0" description="暂无数据" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="charts-row">
      <el-col :xs="24" :lg="12">
        <el-card class="chart-card" shadow="hover">
          <template #header>
            <span>🔥 技能关键词云</span>
          </template>
          <div v-loading="loading" ref="wordCloudRef" class="chart-container"></div>
          <el-empty v-if="!loading && keywordData.length === 0" description="暂无数据" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card class="chart-card" shadow="hover">
          <template #header>
            <span>⭐ 热门技能 TOP10</span>
          </template>
          <div v-loading="loading" ref="skillBarRef" class="chart-container"></div>
          <el-empty v-if="!loading && keywordData.length === 0" description="暂无数据" />
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
import { Search, RefreshLeft, Briefcase, Money, Location, Star, Download } from '@element-plus/icons-vue'
import api from '../api.js'
import { ElMessage } from 'element-plus'
import { exportToPDFMultiPage } from '../utils/exportPdf.js'

const router = useRouter()

const exporting = ref(false)

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

const loading = ref(false)
const selectedCityFromChart = ref(null)

const filters = ref({
  keyword: '',
  selectedCities: [],
  education: '',
  experience: ''
})

const cityOptions = ref([])
const educationOptions = ref(['大专', '本科', '硕士', '博士'])
const experienceOptions = ref(['应届生', '1-3年', '3-5年', '5-10年', '10年以上'])

const jobs = ref([])
const totalJobs = ref(0)
const avgMaxSalary = ref(0)
const topCity = ref('-')
const topSkill = ref('-')

const cityData = ref([])
const eduData = ref([])
const expData = ref([])
const industryData = ref([])
const keywordData = ref([])

const getApiFilters = () => {
  return {
    keyword: filters.value.keyword || undefined,
    city: filters.value.selectedCities.length > 0 ? filters.value.selectedCities.join(',') : undefined,
    education: filters.value.education || undefined,
    experience: filters.value.experience || undefined
  }
}

const loadAllData = async () => {
  loading.value = true
  try {
    const res = await api.getOverview(getApiFilters())
    if (res.data.code === 200) {
      const data = res.data.data
      totalJobs.value = data.total || 0
      cityData.value = data.citySalary || []
      eduData.value = data.educationSalary || []
      expData.value = data.experienceSalary || []
      industryData.value = data.industry || []
      keywordData.value = data.keywords || []

      if (cityOptions.value.length === 0) {
        cityOptions.value = [...new Set(cityData.value.map(d => d.city))].sort()
      }

      calculateStats()
      updateCharts()
    }
  } catch (e) {
    console.error('加载数据失败', e)
    ElMessage.error('加载数据失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const calculateStats = () => {
  if (cityData.value.length > 0) {
    const topCityData = [...cityData.value].sort((a, b) => b.avgSalary - a.avgSalary)[0]
    topCity.value = topCityData?.city || '-'
  }

  let totalMaxSalary = 0
  let count = 0
  cityData.value.forEach(city => {
    totalMaxSalary += city.avgSalary
    count++
  })
  avgMaxSalary.value = count > 0 ? Math.round(totalMaxSalary / count * 100) / 100 : 0

  if (keywordData.value.length > 0) {
    topSkill.value = keywordData.value[0]?.keyword || '-'
  }
}

const handleSearch = () => {
  selectedCityFromChart.value = null
  loadAllData()
}

const handleReset = () => {
  filters.value = {
    keyword: '',
    selectedCities: [],
    education: '',
    experience: ''
  }
  selectedCityFromChart.value = null
  loadAllData()
}

const initCharts = () => {
  cityChart = echarts.init(cityChartRef.value)
  eduChart = echarts.init(eduChartRef.value)
  expChart = echarts.init(expChartRef.value)
  industryChart = echarts.init(industryChartRef.value)
  wordCloudChart = echarts.init(wordCloudRef.value)
  skillBarChart = echarts.init(skillBarRef.value)

  cityChart.on('click', (params) => {
    selectedCityFromChart.value = params.name
    router.push({
      path: '/job-analysis',
      query: { city: params.name }
    })
  })
}

const updateCharts = () => {
  updateCityChart()
  updateEduChart()
  updateExpChart()
  updateIndustryChart()
  updateWordCloud()
  updateSkillBar()
}

const updateCityChart = () => {
  const option = {
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { 
      type: 'category', 
      data: cityData.value.map(d => d.city),
      axisLabel: { rotate: 45, interval: 0 }
    },
    yAxis: { type: 'value', name: 'K' },
    series: [{
      name: '平均薪资',
      type: 'bar',
      data: cityData.value.map(d => d.avgSalary),
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#667eea' },
          { offset: 1, color: '#764ba2' }
        ])
      },
      cursor: 'pointer'
    }]
  }
  cityChart.setOption(option, true)
}

const updateEduChart = () => {
  const option = {
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: eduData.value.map(d => d.education) },
    yAxis: { type: 'value', name: 'K' },
    series: [{
      name: '平均薪资',
      type: 'bar',
      data: eduData.value.map(d => d.avgSalary),
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#f093fb' },
          { offset: 1, color: '#f5576c' }
        ])
      }
    }]
  }
  eduChart.setOption(option, true)
}

const updateExpChart = () => {
  const option = {
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: expData.value.map(d => d.experience) },
    yAxis: { type: 'value', name: 'K' },
    series: [{
      name: '平均薪资',
      type: 'line',
      data: expData.value.map(d => d.avgSalary),
      smooth: true,
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(102, 126, 234, 0.3)' },
          { offset: 1, color: 'rgba(102, 126, 234, 0.05)' }
        ])
      },
      lineStyle: { color: '#667eea', width: 3 }
    }]
  }
  expChart.setOption(option, true)
}

const updateIndustryChart = () => {
  const option = {
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      data: industryData.value.map(d => ({ name: d.industry, value: d.count })),
      emphasis: {
        itemStyle: {
          shadowBlur: 10,
          shadowOffsetX: 0,
          shadowColor: 'rgba(0, 0, 0, 0.5)'
        }
      },
      label: {
        formatter: '{b}: {c} ({d}%)'
      }
    }]
  }
  industryChart.setOption(option, true)
}

const updateWordCloud = () => {
  const words = keywordData.value.map(d => ({
    name: d.keyword,
    value: d.count
  }))
  
  const option = {
    series: [{
      type: 'wordCloud',
      gridSize: 10,
      sizeRange: [12, 50],
      rotationRange: [-45, 45],
      shape: 'pentagon',
      textStyle: {
        fontFamily: 'sans-serif',
        fontWeight: 'bold',
        color: function () {
          const colors = ['#667eea', '#764ba2', '#f093fb', '#f5576c', '#4facfe', '#00f2fe']
          return colors[Math.floor(Math.random() * colors.length)]
        }
      },
      data: words
    }]
  }
  wordCloudChart.setOption(option, true)
}

const updateSkillBar = () => {
  const topSkills = keywordData.value.slice(0, 10).reverse()
  const option = {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value', name: '出现次数' },
    yAxis: { type: 'category', data: topSkills.map(d => d.keyword) },
    series: [{
      name: '出现次数',
      type: 'bar',
      data: topSkills.map(d => d.count),
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#667eea' },
          { offset: 1, color: '#764ba2' }
        ])
      }
    }]
  }
  skillBarChart.setOption(option, true)
}

const handleResize = () => {
  cityChart && cityChart.resize()
  eduChart && eduChart.resize()
  expChart && expChart.resize()
  industryChart && industryChart.resize()
  wordCloudChart && wordCloudChart.resize()
  skillBarChart && skillBarChart.resize()
}

const handleExportDashboard = async () => {
  exporting.value = true
  try {
    const filename = `招聘数据仪表盘_${new Date().toISOString().slice(0,10)}.pdf`
    await exportToPDFMultiPage('dashboard-content', filename)
    ElMessage.success('PDF导出成功！')
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error('PDF导出失败，请重试')
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  initCharts()
  loadAllData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  cityChart && cityChart.dispose()
  eduChart && eduChart.dispose()
  expChart && expChart.dispose()
  industryChart && industryChart.dispose()
  wordCloudChart && wordCloudChart.dispose()
  skillBarChart && skillBarChart.dispose()
})
</script>

<style scoped>
.dashboard {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 20px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.header-left h1 {
  margin: 0 0 8px 0;
  font-size: 28px;
  color: #333;
}

.header-left p {
  margin: 0;
  color: #666;
  font-size: 14px;
}

.filter-card {
  margin-bottom: 20px;
  border-radius: 8px;
}

.filter-form {
  margin-bottom: 0;
}

.stats-cards {
  margin-bottom: 20px;
}

.stat-card {
  border-radius: 8px;
  margin-bottom: 20px;
}

.stat-content {
  display: flex;
  align-items: center;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
}

.stat-icon.blue {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.stat-icon.green {
  background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%);
}

.stat-icon.orange {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
}

.stat-icon.purple {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
}

.stat-icon .el-icon {
  font-size: 28px;
  color: #fff;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #333;
  line-height: 1.2;
}

.stat-label {
  font-size: 14px;
  color: #999;
  margin-top: 4px;
}

.charts-row {
  margin-bottom: 20px;
}

.chart-card {
  border-radius: 8px;
  margin-bottom: 20px;
}

.chart-container {
  height: 350px;
  width: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
