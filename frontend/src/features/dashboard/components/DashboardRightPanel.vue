<template>
  <div class="col-right">
    <div class="action-panel">
      <el-button type="primary" class="action-btn" plain round @click="onOpenDeepAnalysis">
        <el-icon><DataLine /></el-icon> 深度数据分析
      </el-button>
      <el-button type="primary" class="action-btn" plain round :loading="exporting" @click="onExport">
        <el-icon><Download /></el-icon> 导出报告
      </el-button>
    </div>

    <el-card class="glass-card chart-box" shadow="never">
      <template #header>🧠 技能画像</template>
      <el-tabs :model-value="rightTab" class="dash-tabs" stretch @update:model-value="emitUpdateRightTab">
        <el-tab-pane label="🔥 生态" name="wordcloud">
          <div v-show="rightTab === 'wordcloud'" :ref="wordCloudRef" v-loading="loading" class="jd-chart jd-chart-md"></div>
        </el-tab-pane>
        <el-tab-pane label="⭐ Top10" name="top10">
          <div v-show="rightTab === 'top10'" :ref="skillBarRef" v-loading="loading" class="jd-chart jd-chart-md"></div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
/**
 * Dashboard 右侧侧翼（操作区 + 技能画像 Tab）
 *
 * 设计目标：
 * - 将右侧“按钮区 + 技能图表”组件化，减少 Dashboard.vue 模板体积；
 * - rightTab 由上层管理并传入，本组件通过 update:right-tab 回传更新。
 */
const props = defineProps({
  rightTab: {
    type: String,
    required: true
  },
  loading: {
    type: Boolean,
    required: true
  },
  exporting: {
    type: Boolean,
    required: true
  },
  wordCloudRef: {
    type: Function,
    required: true
  },
  skillBarRef: {
    type: Function,
    required: true
  },
  onOpenDeepAnalysis: {
    type: Function,
    required: true
  },
  onExport: {
    type: Function,
    required: true
  }
})

const emit = defineEmits(['update:right-tab'])

/**
 * Element Plus tabs 更新事件回传给上层。
 *
 * @param {string} v 新的 tab name
 */
const emitUpdateRightTab = (v) => emit('update:right-tab', v)
</script>
