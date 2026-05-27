<template>
  <div class="col-main">
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
          <el-button type="primary" round :loading="loading" @click="onSearch">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="glass-card chart-box main-chart" shadow="never">
      <template #header>🏙️ 城市平均薪资全景 (K)</template>
      <div :ref="cityChartRef" v-loading="loading" class="jd-chart jd-chart-lg"></div>
    </el-card>

    <el-card class="glass-card chart-box" shadow="never">
      <template #header>💼 经验成长薪资曲线</template>
      <div :ref="expChartRef" v-loading="loading" class="jd-chart jd-chart-sm"></div>
    </el-card>
  </div>
</template>

<script setup>
/**
 * Dashboard 中间主视野（筛选 + 两张核心图）
 *
 * 设计目标：
 * - 中间区域仅承担表单输入与图表容器渲染；
 * - 数据加载与图表更新由上层 composable 负责（onSearch 触发 loadAllData + updateCharts）。
 */
defineProps({
  filters: {
    type: Object,
    required: true
  },
  cityOptions: {
    type: Array,
    required: true
  },
  loading: {
    type: Boolean,
    required: true
  },
  cityChartRef: {
    type: Function,
    required: true
  },
  expChartRef: {
    type: Function,
    required: true
  },
  onSearch: {
    type: Function,
    required: true
  }
})
</script>
