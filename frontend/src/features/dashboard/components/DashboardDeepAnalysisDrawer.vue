<template>
  <el-drawer v-model="visibleModel" title="🔬 深度数据分析与特征提取" size="600px" direction="rtl">
    <div class="ai-drawer-content">
      <el-card class="drawer-card viz-card" shadow="never" style="margin-bottom: 24px">
        <template #header>
          <div class="card-header-box">
            <div class="chart-header-actions">
              <el-select v-model="selectedDeepChartModel" class="chart-select" size="small" popper-class="chart-select-popper">
                <el-option v-for="opt in deepChartOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
              <el-button type="primary" plain round :loading="pipelineRunning" @click="onRescan">
                <el-icon><RefreshRight /></el-icon> 重新扫描数据
              </el-button>
            </div>
          </div>
        </template>

        <div v-show="selectedDeepChartModel === 'cluster'" class="cluster-visual">
          <div v-if="pipelineClusterSvg" class="cluster-img-wrapper">
            <div
              class="cluster-zoom-shell"
              @dblclick="resetClusterView"
              @mousedown.prevent="onClusterMouseDown"
              @wheel.prevent="onClusterWheel"
            >
              <div ref="clusterSvgHostRef" class="cluster-svg" v-html="pipelineClusterSvg"></div>
            </div>
          </div>
          <div v-else class="empty-placeholder">
            <el-progress v-if="pipelineRunning" type="dashboard" :percentage="pipelineProgress" :color="colors" status="warning">
              <template #default="{ percentage }">
                <span class="percentage-value">{{ percentage }}%</span>
                <span class="percentage-label">{{ pipelineEtaText || '正在聚类' }}</span>
              </template>
            </el-progress>
            <el-empty v-else description="暂无聚类地图，请点击重新扫描" />
          </div>
        </div>

        <div v-show="selectedDeepChartModel === 'heatmap'" class="heatmap-visual">
          <div v-if="jobSkillHeatmapReady" :ref="jobSkillHeatmapRef" class="jd-chart heatmap-chart"></div>
          <el-empty v-else :image-size="60" description="暂无热力图，请点击重新扫描" />
        </div>

        <div v-show="selectedDeepChartModel === 'timeline'" class="timeline-visual">
          <div v-if="skillTimelineReady" :ref="skillTimelineRef" class="jd-chart timeline-chart"></div>
          <el-empty v-else :image-size="60" description="暂无趋势数据（仅统计51job），请点击重新扫描" />
        </div>

        <div v-show="selectedDeepChartModel === 'company_size_salary'" class="size-salary-visual">
          <div v-if="companySizeSalaryReady" :ref="companySizeSalaryRef" class="jd-chart size-salary-chart"></div>
          <el-empty v-else :image-size="60" description="暂无公司规模薪资数据，请点击重新扫描" />
        </div>

        <div v-show="selectedDeepChartModel === 'edu_exp_bubble'" class="bubble-visual">
          <div v-if="eduExpSalaryBubbleReady" class="bubble-box">
            <div :ref="eduExpSalaryBubbleRef" class="jd-chart bubble-chart"></div>
            <div v-if="eduExpSalaryBubbleStatsText" class="bubble-meta">{{ eduExpSalaryBubbleStatsText }}</div>
          </div>
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
            <el-tag
              :type="pipelineStatus === 'running' ? 'warning' : pipelineStatus === 'failed' ? 'danger' : 'success'"
              effect="dark"
              round
              size="small"
            >
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
              <el-progress
                :percentage="Number((pipelineSummary?.mlp_val_acc * 100 || 0).toFixed(1))"
                color="var(--c-primary-600)"
                :stroke-width="12"
                :show-text="false"
              />
            </div>
          </div>
          <div class="metric-item">
            <div class="metric-label">
              <span>TextCNN 预测准确率</span>
              <span class="u-accent">{{ (pipelineSummary?.textcnn_val_acc * 100 || 0).toFixed(1) }}%</span>
            </div>
            <div class="metric-progress">
              <el-progress
                :percentage="Number((pipelineSummary?.textcnn_val_acc * 100 || 0).toFixed(1))"
                color="var(--c-accent-500)"
                :stroke-width="12"
                :show-text="false"
              />
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
              style="margin-left: auto"
              :loading="topTokensRunning"
              :disabled="pipelineRunning"
              @click="onRefreshTopTokens"
            >
              <el-icon><RefreshRight /></el-icon> 单独刷新
            </el-button>
          </h4>
          <div v-if="(pipelineSummary?.top_tokens || []).length" class="token-cloud">
            <span v-for="t in pipelineSummary.top_tokens" :key="t.token" class="token-chip">
              {{ t.token }} <span class="token-count">{{ t.count }}</span>
            </span>
          </div>
          <el-empty v-else :image-size="60" description="待分析数据特征" />
        </div>

        <div v-if="(pipelineErrors || []).length" class="pipeline-logs">
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
</template>

<script setup>
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'

/**
 * Dashboard 深度分析抽屉（纯 UI）
 *
 * 设计目标：
 * - 把 Drawer 的大块模板从 Dashboard.vue 拆出；
 * - 状态与业务动作全部由上层 composable 提供，本组件仅绑定与触发；
 * - 深度图表容器 ref 由上层创建并传入，本组件负责挂载到对应 DOM。
 */
const props = defineProps({
  visible: {
    type: Boolean,
    required: true
  },
  selectedDeepChart: {
    type: String,
    required: true
  },
  deepChartOptions: {
    type: Array,
    required: true
  },
  pipelineRunning: {
    type: Boolean,
    required: true
  },
  topTokensRunning: {
    type: Boolean,
    required: true
  },
  pipelineStatus: {
    type: String,
    required: true
  },
  pipelineStatusText: {
    type: String,
    required: true
  },
  pipelineSummary: {
    type: Object,
    required: true
  },
  pipelineErrors: {
    type: Array,
    required: true
  },
  pipelineClusterSvg: {
    type: String,
    required: true
  },
  pipelineProgress: {
    type: Number,
    required: true
  },
  pipelineEtaText: {
    type: String,
    required: true
  },
  colors: {
    type: Function,
    required: true
  },
  jobSkillHeatmapReady: {
    type: Boolean,
    required: true
  },
  skillTimelineReady: {
    type: Boolean,
    required: true
  },
  companySizeSalaryReady: {
    type: Boolean,
    required: true
  },
  eduExpSalaryBubbleReady: {
    type: Boolean,
    required: true
  },
  eduExpSalaryBubbleStatsText: {
    type: String,
    required: true
  },
  jobSkillHeatmapRef: {
    type: Function,
    required: true
  },
  skillTimelineRef: {
    type: Function,
    required: true
  },
  companySizeSalaryRef: {
    type: Function,
    required: true
  },
  eduExpSalaryBubbleRef: {
    type: Function,
    required: true
  },
  onRescan: {
    type: Function,
    required: true
  },
  onRefreshTopTokens: {
    type: Function,
    required: true
  }
})

const emit = defineEmits(['update:visible', 'update:selected-deep-chart'])

/**
 * v-model 代理：抽屉开关。
 */
const visibleModel = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v)
})

/**
 * v-model 代理：深度图类型选择。
 */
const selectedDeepChartModel = computed({
  get: () => props.selectedDeepChart,
  set: (v) => emit('update:selected-deep-chart', v)
})

const clusterSvgHostRef = ref(null)

const clusterZoom = ref(1)
const clusterBaseViewBox = ref(null)
const clusterViewBox = ref(null)

const clusterDragging = ref(false)
const clusterStart = ref({ x: 0, y: 0, viewBox: null, rect: null })

const clamp = (v, min, max) => Math.min(max, Math.max(min, v))

const getClusterSvgEl = () => {
  const host = clusterSvgHostRef.value
  if (!host) return null
  return host.querySelector?.('svg') || null
}

const readSvgViewBox = (svgEl) => {
  if (!svgEl) return null
  const vb = svgEl.getAttribute?.('viewBox')
  if (vb) {
    const parts = vb
      .trim()
      .split(/[\s,]+/)
      .map((x) => Number(x))
      .filter((x) => Number.isFinite(x))
    if (parts.length === 4) return { x: parts[0], y: parts[1], w: parts[2], h: parts[3] }
  }
  const rect = svgEl.getBoundingClientRect?.()
  const w = Number.isFinite(rect?.width) && rect.width > 0 ? rect.width : 800
  const h = Number.isFinite(rect?.height) && rect.height > 0 ? rect.height : 450
  const fallback = { x: 0, y: 0, w, h }
  svgEl.setAttribute?.('viewBox', `${fallback.x} ${fallback.y} ${fallback.w} ${fallback.h}`)
  return fallback
}

const applySvgViewBox = () => {
  const svgEl = getClusterSvgEl()
  const vb = clusterViewBox.value
  if (!svgEl || !vb) return
  svgEl.setAttribute('preserveAspectRatio', 'xMidYMid slice')
  svgEl.setAttribute('viewBox', `${vb.x} ${vb.y} ${vb.w} ${vb.h}`)
}

const resetClusterView = () => {
  clusterZoom.value = 1
  if (clusterBaseViewBox.value) {
    clusterViewBox.value = { ...clusterBaseViewBox.value }
    applySvgViewBox()
  }
}

const onClusterWheel = (e) => {
  if (selectedDeepChartModel.value !== 'cluster') return
  const base = clusterBaseViewBox.value
  const cur = clusterViewBox.value
  if (!base || !cur) return
  const oldZoom = clusterZoom.value
  const nextZoom = clamp(oldZoom * (e.deltaY > 0 ? 0.92 : 1.08), 0.6, 6)
  const el = e.currentTarget
  const rect = el?.getBoundingClientRect?.()
  if (!rect || rect.width <= 0 || rect.height <= 0) return
  const mx = e.clientX - rect.left
  const my = e.clientY - rect.top
  const sx = cur.x + (mx / rect.width) * cur.w
  const sy = cur.y + (my / rect.height) * cur.h
  const w = base.w / nextZoom
  const h = base.h / nextZoom
  const x = sx - (mx / rect.width) * w
  const y = sy - (my / rect.height) * h
  clusterZoom.value = nextZoom
  clusterViewBox.value = { x, y, w, h }
  applySvgViewBox()
}

const onClusterMouseMove = (e) => {
  if (!clusterDragging.value) return
  const start = clusterStart.value
  const vb0 = start.viewBox
  const rect = start.rect
  if (!vb0 || !rect || rect.width <= 0 || rect.height <= 0) return
  const dx = e.clientX - start.x
  const dy = e.clientY - start.y
  const x = vb0.x - dx * (vb0.w / rect.width)
  const y = vb0.y - dy * (vb0.h / rect.height)
  clusterViewBox.value = { x, y, w: vb0.w, h: vb0.h }
  applySvgViewBox()
}

const onClusterMouseUp = () => {
  clusterDragging.value = false
}

const onClusterMouseDown = (e) => {
  if (selectedDeepChartModel.value !== 'cluster') return
  if (e.button !== 0) return
  const rect = e.currentTarget?.getBoundingClientRect?.()
  const vb = clusterViewBox.value
  if (!rect || !vb) return
  clusterDragging.value = true
  clusterStart.value = { x: e.clientX, y: e.clientY, viewBox: { ...vb }, rect }
}

watch(
  () => props.pipelineClusterSvg,
  async (v) => {
    if (!v) return
    await nextTick()
    const svgEl = getClusterSvgEl()
    const base = readSvgViewBox(svgEl)
    if (!base) return
    clusterBaseViewBox.value = base
    clusterViewBox.value = { ...base }
    clusterZoom.value = 1
    applySvgViewBox()
  },
  { immediate: true }
)

onUnmounted(() => {
  clusterDragging.value = false
})

if (typeof window !== 'undefined') {
  window.addEventListener('mousemove', onClusterMouseMove)
  window.addEventListener('mouseup', onClusterMouseUp)
  onUnmounted(() => {
    window.removeEventListener('mousemove', onClusterMouseMove)
    window.removeEventListener('mouseup', onClusterMouseUp)
  })
}
</script>

<style scoped>
.cluster-img-wrapper {
  width: 100%;
  height: 100%;
}
.cluster-zoom-shell {
  position: relative;
  overflow: hidden;
  width: 100%;
  height: 100%;
  border-radius: 12px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  background: #fff;
  cursor: grab;
  user-select: none;
}
.cluster-zoom-shell:active {
  cursor: grabbing;
}
.cluster-svg {
  display: block;
  width: 100%;
  height: 100%;
}
.cluster-svg :deep(svg) {
  display: block;
  width: 100%;
  height: 100%;
}
.cluster-visual .empty-placeholder {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 10px;
}
.percentage-value,
.percentage-label {
  display: block;
  text-align: center;
}
.bubble-box {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.bubble-chart {
  flex: 1;
}
.bubble-meta {
  margin-top: 10px;
  font-size: 12px;
  color: rgba(15, 23, 42, 0.65);
  font-weight: 600;
  text-align: center;
}
</style>
