<template>
  <div class="col-left">
    <div class="jd-page-head">
      <div class="jd-page-head__title jd-page-head__title-text u-title">📊 数据智能洞察</div>
      <div class="jd-page-head__desc">全网招聘动态实时监控</div>
    </div>

    <div class="bento-stats">
      <div v-for="stat in mainStats" :key="stat.label" class="bento-stat-card u-card u-card-pad" :class="stat.type">
        <div class="stat-icon">
          <el-icon><component :is="stat.icon" /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">
            {{ stat.value }}<small v-if="stat.unit">{{ stat.unit }}</small>
          </div>
          <div class="stat-label">{{ stat.label }}</div>
        </div>
      </div>
    </div>

    <el-card class="glass-card chart-box" shadow="never">
      <template #header>📌 结构分布</template>
      <el-tabs :model-value="leftTab" class="dash-tabs" stretch @update:model-value="emitUpdateLeftTab">
        <el-tab-pane label="🏢 行业" name="industry">
          <div v-show="leftTab === 'industry'" :ref="industryChartRef" v-loading="loading" class="jd-chart jd-chart-md"></div>
        </el-tab-pane>
        <el-tab-pane label="🎓 学历" name="edu">
          <div v-show="leftTab === 'edu'" :ref="eduChartRef" v-loading="loading" class="jd-chart jd-chart-md"></div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
/**
 * Dashboard 左侧面板（标题 + 指标卡 + 结构分布 Tab）
 *
 * 设计目标：
 * - 将 Dashboard.vue 的左侧 UI 区块组件化，页面只负责“数据与 refs 绑定”；
 * - 图表 DOM ref 由上层创建（composable 依赖这些 ref 初始化 ECharts），本组件仅负责挂载。
 */
const props = defineProps({
  mainStats: {
    type: Array,
    required: true
  },
  leftTab: {
    type: String,
    required: true
  },
  loading: {
    type: Boolean,
    required: true
  },
  industryChartRef: {
    type: Function,
    required: true
  },
  eduChartRef: {
    type: Function,
    required: true
  }
})

const emit = defineEmits(['update:left-tab'])

/**
 * Element Plus 的 tabs 使用 update:model-value 事件更新选中值。
 *
 * @param {string} v 新的 tab name
 */
const emitUpdateLeftTab = (v) => emit('update:left-tab', v)
</script>
