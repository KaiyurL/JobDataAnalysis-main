<template>
  <div class="u-stack">
    <div class="jd-page-head">
      <div class="jd-page-head__title jd-page-head__title-text u-title">🏢 公司洞察</div>
      <div class="jd-page-head__desc">招聘市场公司分析报告</div>
    </div>

    <el-row :gutter="20" class="charts-row">
      <el-col :xs="24" :lg="12">
        <el-card class="chart-card" shadow="hover">
          <template #header>
            <span>🔥 热门公司TOP10</span>
          </template>
          <div ref="companyHotRef" v-loading="loading" class="jd-chart jd-chart-lg"></div>
          <el-empty v-if="!loading && companyHotData.length === 0" description="暂无数据" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card class="chart-card" shadow="hover">
          <template #header>
            <span>💰 公司薪资排名TOP10</span>
          </template>
          <div ref="companySalaryRef" v-loading="loading" class="jd-chart jd-chart-lg"></div>
          <el-empty v-if="!loading && companySalaryData.length === 0" description="暂无数据" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="charts-row">
      <el-col :xs="24" :lg="12">
        <el-card class="chart-card" shadow="hover">
          <template #header>
            <span>📊 公司规模分布</span>
          </template>
          <div ref="companySizeRef" v-loading="loading" class="jd-chart jd-chart-lg"></div>
          <el-empty v-if="!loading && companySizeData.length === 0" description="暂无数据" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { jobsApi } from '@/services/index.js'
import { ElMessage } from 'element-plus'
import { baseTheme, cssVar } from '@/shared/theme.js'

const theme = () => ({
  ...baseTheme(),
  warning: cssVar('--c-warning', '#f59e0b')
})

const companyHotRef = ref(null)
const companySalaryRef = ref(null)
const companySizeRef = ref(null)

let companyHotChart = null
let companySalaryChart = null
let companySizeChart = null

const loading = ref(false)
const companyHotData = ref([])
const companySalaryData = ref([])
const companySizeData = ref([])

const loadCompanyStats = async () => {
  loading.value = true
  try {
    const [hotRes, salaryRes, sizeRes] = await Promise.allSettled([
      jobsApi.getCompanyHotStatsData(),
      jobsApi.getCompanySalaryStatsData(),
      jobsApi.getCompanySizeStatsData()
    ])

    if (hotRes.status === 'fulfilled') companyHotData.value = hotRes.value || []
    if (salaryRes.status === 'fulfilled') companySalaryData.value = salaryRes.value || []
    if (sizeRes.status === 'fulfilled') companySizeData.value = sizeRes.value || []

    updateCharts()
  } catch (e) {
    const msg = e?.response?.data?.message || e?.message || '加载公司统计失败'
    ElMessage.error(String(msg))
  } finally {
    loading.value = false
  }
}

const initCharts = () => {
  companyHotChart = echarts.init(companyHotRef.value)
  companySalaryChart = echarts.init(companySalaryRef.value)
  companySizeChart = echarts.init(companySizeRef.value)
}

const updateCharts = () => {
  updateCompanyHotChart()
  updateCompanySalaryChart()
  updateCompanySizeChart()
}

const updateCompanyHotChart = () => {
  const t = theme()
  const option = {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value', name: '岗位数' },
    yAxis: {
      type: 'category',
      data: companyHotData.value.map((d) => d.companyName),
      axisLabel: { overflow: 'truncate', width: 150 }
    },
    series: [
      {
        name: '岗位数',
        type: 'bar',
        data: companyHotData.value.map((d) => d.count),
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: t.primary },
            { offset: 1, color: t.info }
          ])
        },
        barWidth: 14
      }
    ]
  }
  companyHotChart.setOption(option, true)
}

const updateCompanySalaryChart = () => {
  const t = theme()
  const option = {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value', name: '平均薪资(K)' },
    yAxis: {
      type: 'category',
      data: companySalaryData.value.map((d) => d.companyName),
      axisLabel: { overflow: 'truncate', width: 150 }
    },
    series: [
      {
        name: '平均薪资',
        type: 'bar',
        data: companySalaryData.value.map((d) => d.avgSalary),
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: t.accent },
            { offset: 1, color: t.warning }
          ])
        },
        barWidth: 14
      }
    ]
  }
  companySalaryChart.setOption(option, true)
}

const updateCompanySizeChart = () => {
  const t = theme()
  const option = {
    tooltip: { trigger: 'item' },
    color: [t.primary, t.accent, t.info, t.success, t.warning],
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        data: companySizeData.value.map((d) => ({ name: d.size, value: d.count })),
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
      }
    ]
  }
  companySizeChart.setOption(option, true)
}

const handleResize = () => {
  companyHotChart && companyHotChart.resize()
  companySalaryChart && companySalaryChart.resize()
  companySizeChart && companySizeChart.resize()
}

onMounted(() => {
  initCharts()
  loadCompanyStats()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  companyHotChart && companyHotChart.dispose()
  companySalaryChart && companySalaryChart.dispose()
  companySizeChart && companySizeChart.dispose()
})
</script>
