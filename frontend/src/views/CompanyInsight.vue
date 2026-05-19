<template>
  <div class="company-insight">
    <div class="page-header">
      <h1>🏢 公司洞察</h1>
      <p>招聘市场公司分析报告</p>
    </div>

    <el-row :gutter="20" class="charts-row">
      <el-col :xs="24" :lg="12">
        <el-card class="chart-card" shadow="hover">
          <template #header>
            <span>🔥 热门公司TOP10</span>
          </template>
          <div v-loading="loading" ref="companyHotRef" class="chart-container"></div>
          <el-empty v-if="!loading && companyHotData.length === 0" description="暂无数据" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card class="chart-card" shadow="hover">
          <template #header>
            <span>💰 公司薪资排名TOP10</span>
          </template>
          <div v-loading="loading" ref="companySalaryRef" class="chart-container"></div>
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
          <div v-loading="loading" ref="companySizeRef" class="chart-container"></div>
          <el-empty v-if="!loading && companySizeData.length === 0" description="暂无数据" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import api from '../api.js'

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
    const [hotRes, salaryRes, sizeRes] = await Promise.all([
      api.getCompanyHotStats(),
      api.getCompanySalaryStats(),
      api.getCompanySizeStats()
    ])

    if (hotRes.data.code === 200) companyHotData.value = hotRes.data.data || []
    if (salaryRes.data.code === 200) companySalaryData.value = salaryRes.data.data || []
    if (sizeRes.data.code === 200) companySizeData.value = sizeRes.data.data || []

    updateCharts()
  } catch (e) {
    console.error('加载公司统计失败', e)
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
  const option = {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value', name: '岗位数' },
    yAxis: { 
      type: 'category', 
      data: companyHotData.value.map(d => d.companyName),
      axisLabel: { overflow: 'truncate', width: 150 }
    },
    series: [{
      name: '岗位数',
      type: 'bar',
      data: companyHotData.value.map(d => d.count),
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#667eea' },
          { offset: 1, color: '#764ba2' }
        ])
      }
    }]
  }
  companyHotChart.setOption(option, true)
}

const updateCompanySalaryChart = () => {
  const option = {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value', name: '平均薪资(K)' },
    yAxis: { 
      type: 'category', 
      data: companySalaryData.value.map(d => d.companyName),
      axisLabel: { overflow: 'truncate', width: 150 }
    },
    series: [{
      name: '平均薪资',
      type: 'bar',
      data: companySalaryData.value.map(d => d.avgSalary),
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#f093fb' },
          { offset: 1, color: '#f5576c' }
        ])
      }
    }]
  }
  companySalaryChart.setOption(option, true)
}

const updateCompanySizeChart = () => {
  const option = {
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      data: companySizeData.value.map(d => ({ name: d.size, value: d.count })),
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

<style scoped>
.company-insight {
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

.charts-row {
  margin-bottom: 20px;
}

.chart-card {
  border-radius: 8px;
  margin-bottom: 20px;
}

.chart-container {
  height: 400px;
  width: 100%;
}
</style>
