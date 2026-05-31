<template>
  <div id="dashboard-content" class="u-page">
    <el-row :gutter="24" class="cockpit-row">
      <el-col :xs="24" :lg="7" class="col-left">
        <DashboardLeftPanel
          :main-stats="mainStats"
          :left-tab="leftTab"
          :loading="loading"
          :industry-chart-ref="setIndustryChartRef"
          :edu-chart-ref="setEduChartRef"
          @update:left-tab="leftTab = $event"
        />
      </el-col>

      <el-col :xs="24" :lg="11" class="col-main">
        <DashboardMainPanel
          :filters="filters"
          :city-options="cityOptions"
          :loading="loading"
          :city-chart-ref="setCityChartRef"
          :exp-chart-ref="setExpChartRef"
          :on-search="handleSearch"
        />
      </el-col>

      <el-col :xs="24" :lg="6" class="col-right">
        <DashboardRightPanel
          :right-tab="rightTab"
          :loading="loading"
          :exporting="exporting"
          :word-cloud-ref="setWordCloudRef"
          :skill-bar-ref="setSkillBarRef"
          :on-open-deep-analysis="() => (aiDrawerVisible = true)"
          :on-export="handleExportDashboard"
          @update:right-tab="rightTab = $event"
        />
      </el-col>
    </el-row>

    <DashboardDeepAnalysisDrawer
      :visible="aiDrawerVisible"
      :selected-deep-chart="selectedDeepChart"
      :deep-chart-options="deepChartOptions"
      :pipeline-running="pipelineRunning"
      :top-tokens-running="topTokensRunning"
      :pipeline-status="pipelineStatus"
      :pipeline-status-text="pipelineStatusText"
      :pipeline-summary="pipelineSummary"
      :pipeline-errors="pipelineErrors"
      :pipeline-cluster-svg="pipelineClusterSvg"
      :pipeline-progress="pipelineProgress"
      :pipeline-eta-text="pipelineEtaText"
      :colors="colors"
      :job-skill-heatmap-ready="jobSkillHeatmapReady"
      :skill-timeline-ready="skillTimelineReady"
      :company-size-salary-ready="companySizeSalaryReady"
      :edu-exp-salary-bubble-ready="eduExpSalaryBubbleReady"
      :edu-exp-salary-bubble-stats-text="eduExpSalaryBubbleStatsText"
      :job-skill-heatmap-ref="setJobSkillHeatmapRef"
      :skill-timeline-ref="setSkillTimelineRef"
      :company-size-salary-ref="setCompanySizeSalaryRef"
      :edu-exp-salary-bubble-ref="setEduExpSalaryBubbleRef"
      :on-rescan="() => startPipeline(true)"
      :on-refresh-top-tokens="() => startStatsPipeline(true)"
      @update:visible="aiDrawerVisible = $event"
      @update:selected-deep-chart="selectedDeepChart = $event"
    />
  </div>
</template>

<script setup>
defineOptions({ name: 'Dashboard' })

import { ref, onMounted, onUnmounted, computed, nextTick, onActivated } from 'vue'
import { Briefcase, Money, Location } from '@element-plus/icons-vue'
import { exportToPDFMultiPage } from '@/utils/exportPdf.js'
import { baseTheme, cssVar } from '@/shared/theme.js'
import { useDashboardDeepAnalysis } from '@/features/dashboard/composables/useDashboardDeepAnalysis.js'
import { useDashboardMarketData } from '@/features/dashboard/composables/useDashboardMarketData.js'
import { useDashboardCharts } from '@/features/dashboard/composables/useDashboardCharts.js'
import DashboardLeftPanel from '@/features/dashboard/components/DashboardLeftPanel.vue'
import DashboardMainPanel from '@/features/dashboard/components/DashboardMainPanel.vue'
import DashboardRightPanel from '@/features/dashboard/components/DashboardRightPanel.vue'
import DashboardDeepAnalysisDrawer from '@/features/dashboard/components/DashboardDeepAnalysisDrawer.vue'

const theme = () => ({
  ink2: cssVar('--c-ink-2', '#334155'),
  ink3: cssVar('--c-ink-3', '#64748b'),
  border: cssVar('--c-border', 'rgba(15, 23, 42, 0.10)'),
  ...baseTheme()
})

// 状态变量
const exporting = ref(false)
// 市场统计数据（BOSS + 51job）下沉到 composable，负责拉取/融合/错误处理
const {
  loading,
  totalJobs,
  avgMaxSalary,
  topCity,
  cityData,
  eduData,
  expData,
  industryData,
  keywordData,
  cityOptions,
  filters,
  loadAllData
} = useDashboardMarketData()
// 深度分析抽屉（pipeline + 深度图表）逻辑下沉到 composable，Dashboard 只负责状态绑定与基础图表渲染
const {
  aiDrawerVisible,
  pipelineRunning,
  topTokensRunning,
  pipelineStatus,
  pipelineSummary,
  pipelineErrors,
  pipelineClusterSvg,
  pipelineProgress,
  pipelineEtaText,
  jobSkillHeatmapReady,
  skillTimelineReady,
  companySizeSalaryReady,
  eduExpSalaryBubbleReady,
  eduExpSalaryBubbleStatsText,
  deepChartOptions,
  selectedDeepChart,
  jobSkillHeatmapRef,
  skillTimelineRef,
  companySizeSalaryRef,
  eduExpSalaryBubbleRef,
  startPipeline,
  startStatsPipeline,
  resizeDeepCharts
} = useDashboardDeepAnalysis({ theme })
const leftTab = ref('industry')
const rightTab = ref('wordcloud')

// 图表实例与引用
const cityChartRef = ref(null)
const eduChartRef = ref(null)
const expChartRef = ref(null)
const industryChartRef = ref(null)
const wordCloudRef = ref(null)
const skillBarRef = ref(null)

const setCityChartRef = (el) => (cityChartRef.value = el)
const setEduChartRef = (el) => (eduChartRef.value = el)
const setExpChartRef = (el) => (expChartRef.value = el)
const setIndustryChartRef = (el) => (industryChartRef.value = el)
const setWordCloudRef = (el) => (wordCloudRef.value = el)
const setSkillBarRef = (el) => (skillBarRef.value = el)

const setJobSkillHeatmapRef = (el) => (jobSkillHeatmapRef.value = el)
const setSkillTimelineRef = (el) => (skillTimelineRef.value = el)
const setCompanySizeSalaryRef = (el) => (companySizeSalaryRef.value = el)
const setEduExpSalaryBubbleRef = (el) => (eduExpSalaryBubbleRef.value = el)

// 计算属性
const mainStats = computed(() => [
  { label: '总职位规模', value: totalJobs.value, icon: Briefcase, type: 'blue' },
  { label: '市场均薪', value: avgMaxSalary.value, unit: 'K', icon: Money, type: 'green' },
  { label: '高薪聚集地', value: topCity.value, icon: Location, type: 'orange' }
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
// 基础图表（ECharts）逻辑下沉到 composable，Dashboard 只负责绑定 refs 与触发更新
const { initBaseCharts, updateBaseCharts, resizeBaseCharts, disposeBaseCharts } = useDashboardCharts({
  theme,
  leftTab,
  rightTab,
  refs: {
    cityChartRef,
    eduChartRef,
    expChartRef,
    industryChartRef,
    wordCloudRef,
    skillBarRef
  },
  data: {
    cityData,
    eduData,
    expData,
    industryData,
    keywordData
  }
})

const handleResize = () => {
  resizeBaseCharts()
  // 深度分析抽屉内的 ECharts 由 composable 管理，这里统一触发 resize 以适配窗口变化
  resizeDeepCharts()
}

const handleSearch = () => loadAllData().then(() => updateBaseCharts())
const handleExportDashboard = () => exportToPDFMultiPage('dashboard-content', '招聘数据洞察报告.pdf')

onMounted(() => {
  nextTick(() => {
    initBaseCharts()
    loadAllData().then(() => {
      updateBaseCharts()
      nextTick(() => resizeBaseCharts())
      setTimeout(() => resizeBaseCharts(), 80)
    })
  })
  window.addEventListener('resize', handleResize)
})

onActivated(() => {
  nextTick(() => handleResize())
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  disposeBaseCharts()
})
</script>
