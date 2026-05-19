<template>
  <div class="skill-analysis">
    <div class="page-header">
      <h1>📊 技能分析</h1>
      <p>深入分析招聘市场中热门技能的需求情况</p>
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

    <el-row :gutter="20">
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
          <span>⭐ 热门技能 TOP20</span>
        </template>
          <div v-loading="loading" ref="skillBarRef" class="chart-container"></div>
          <el-empty v-if="!loading && keywordData.length === 0" description="暂无数据" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="24">
        <el-card class="chart-card" shadow="hover">
          <template #header>
          <span>📈 技能分布图</span>
        </template>
          <div v-loading="loading" ref="skillScatterRef" class="chart-container"></div>
          <el-empty v-if="!loading && keywordData.length === 0" description="暂无数据" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="24">
        <el-card class="stats-card" shadow="hover">
          <template #header>
            <span>📋 技能详细列表</span>
          </template>
          <el-table 
            :data="keywordData" 
            style="width: 100%" 
            :default-sort="{ prop: 'count', order: 'descending' }"
            stripe
            border
          >
            <el-table-column type="index" label="排名" width="80" align="center">
              <template #default="{ $index }">
                <el-tag v-if="$index < 3" :type="['danger', 'warning', 'success'][$index]" effect="dark">
                  {{ $index + 1 }}
                </el-tag>
                <span v-else>{{ $index + 1 }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="keyword" label="技能" min-width="150" />
            <el-table-column prop="count" label="出现次数" width="120" sortable />
            <el-table-column label="占比" width="120">
              <template #default="{ row }">
                <el-progress :percentage="calculatePercentage(row.count)" :color="getProgressColor(row.count)" :stroke-width="12" />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import 'echarts-wordcloud'
import { Search, RefreshLeft } from '@element-plus/icons-vue'
import api from '../api.js'
import { ElMessage } from 'element-plus'

const wordCloudRef = ref(null)
const skillBarRef = ref(null)
const skillScatterRef = ref(null)

let wordCloudChart = null
let skillBarChart = null
let skillScatterChart = null

const loading = ref(false)

const filters = ref({
  keyword: '',
  selectedCities: [],
  education: '',
  experience: ''
})

const cityOptions = ref([])
const educationOptions = ref(['大专', '本科', '硕士', '博士'])
const experienceOptions = ref(['应届生', '1-3年', '3-5年', '5-10年', '10年以上'])

const keywordData = ref([])
const totalCount = ref(0)

const getApiFilters = () => {
  return {
    keyword: filters.value.keyword || undefined,
    city: filters.value.selectedCities.length > 0 ? filters.value.selectedCities.join(',') : undefined,
    education: filters.value.education || undefined,
    experience: filters.value.experience || undefined
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await api.getOverview(getApiFilters())
    if (res.data.code === 200) {
      keywordData.value = res.data.data.keywords || []
      
      if (cityOptions.value.length === 0 && res.data.data.citySalary) {
        cityOptions.value = [...new Set(res.data.data.citySalary.map(d => d.city))].sort()
      }

      totalCount.value = keywordData.value.reduce((sum, item) => sum + item.count, 0)
      
      updateCharts()
    }
  } catch (e) {
    console.error('加载数据失败', e)
    ElMessage.error('加载数据失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  loadData()
}

const handleReset = () => {
  filters.value = {
    keyword: '',
    selectedCities: [],
    education: '',
    experience: ''
  }
  loadData()
}

const calculatePercentage = (count) => {
  if (totalCount.value === 0) return 0
  return Math.round((count / totalCount.value) * 100 * 100) / 100
}

const getProgressColor = (count) => {
  const percentage = calculatePercentage(count)
  if (percentage > 10) return '#f5576c'
  if (percentage > 5) return '#f093fb'
  if (percentage > 2) return '#667eea'
  return '#4facfe'
}

const initCharts = () => {
  wordCloudChart = echarts.init(wordCloudRef.value)
  skillBarChart = echarts.init(skillBarRef.value)
  skillScatterChart = echarts.init(skillScatterRef.value)
}

const updateCharts = () => {
  updateWordCloud()
  updateSkillBar()
  updateSkillScatter()
}

const updateWordCloud = () => {
  const words = keywordData.value.map(d => ({
    name: d.keyword,
    value: d.count
  }))
  
  const option = {
    series: [{
      type: 'wordCloud',
      gridSize: 12,
      sizeRange: [14, 60],
      rotationRange: [-45, 45],
      shape: 'pentagon',
      textStyle: {
        fontFamily: 'sans-serif',
        fontWeight: 'bold',
        color: function () {
          const colors = ['#667eea', '#764ba2', '#f093fb', '#f5576c', '#4facfe', '#00f2fe', '#11998e', '#38ef7d']
          return colors[Math.floor(Math.random() * colors.length)]
        }
      },
      data: words
    }]
  }
  wordCloudChart.setOption(option, true)
}

const updateSkillBar = () => {
  const topSkills = keywordData.value.slice(0, 20).reverse()
  const option = {
    tooltip: { 
      trigger: 'axis', 
      axisPointer: { type: 'shadow' }
    },
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

const updateSkillScatter = () => {
  const topSkills = keywordData.value.slice(0, 30)
  const option = {
    tooltip: {
      trigger: 'item',
      formatter: (params) => `${params.data[0]}: ${params.data[1]}次`
    },
    grid: { left: '3%', right: '7%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: topSkills.map(d => d.keyword),
      axisLabel: { rotate: 45 }
    },
    yAxis: { type: 'value', name: '出现次数' },
    series: [{
      type: 'scatter',
      data: topSkills.map((d, index) => [d.keyword, d.count]),
      symbolSize: (data) => Math.sqrt(data[1]) * 5,
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#4facfe' },
          { offset: 1, color: '#00f2fe' }
        ])
      }
    }]
  }
  skillScatterChart.setOption(option, true)
}

const handleResize = () => {
  wordCloudChart && wordCloudChart.resize()
  skillBarChart && skillBarChart.resize()
  skillScatterChart && skillScatterChart.resize()
}

onMounted(() => {
  initCharts()
  loadData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  wordCloudChart && wordCloudChart.dispose()
  skillBarChart && skillBarChart.dispose()
  skillScatterChart && skillScatterChart.dispose()
})
</script>

<style scoped>
.skill-analysis {
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

.filter-card {
  margin-bottom: 20px;
  border-radius: 8px;
}

.filter-form {
  margin-bottom: 0;
}

.chart-card {
  border-radius: 8px;
  margin-bottom: 20px;
}

.chart-container {
  height: 400px;
  width: 100%;
}

.stats-card {
  border-radius: 8px;
}
</style>
