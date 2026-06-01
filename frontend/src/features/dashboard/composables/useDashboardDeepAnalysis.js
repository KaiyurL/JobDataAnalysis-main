import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { pipelineApi } from '@/services/index.js'

/**
 * 仪表盘“深度数据分析”抽屉逻辑聚合（Dashboard Deep Analysis）
 *
 * 覆盖范围：
 * - pipeline 运行/轮询状态、缓存结果复用、产物拉取与解析（图片/JSON blob）
 * - 深度图表（热力图/趋势图/公司规模薪资/学历经验气泡图）初始化、渲染、resize 与销毁
 *
 * 设计目标：
 * - 将 Dashboard.vue 中与深度分析抽屉强相关的逻辑抽离，避免页面脚本膨胀；
 * - 保持现有接口协议与页面交互行为不变；
 * - 将资源释放（interval、ObjectURL、echarts instance）集中管理，避免内存泄漏。
 *
 * @param {{ theme: () => Record<string, string> }} deps
 * @returns {Record<string, any>} refs/computed/actions（供 Dashboard.vue 模板直接绑定）
 */
export function useDashboardDeepAnalysis(deps) {
  const theme = typeof deps?.theme === 'function' ? deps.theme : () => ({})

  const aiDrawerVisible = ref(false)
  const pipelineRunning = ref(false)
  const topTokensRunning = ref(false)
  const pipelineStatus = ref('idle')
  const pipelineMessage = ref('')
  const pipelineSummary = ref({})
  const pipelineErrors = ref([])
  const pipelineClusterSvg = ref('')
  const pipelinePollTimer = ref(null)
  const pipelineProgress = ref(0)
  const pipelineEstimatedEndMs = ref(0)
  const pipelineEstimatedTotalSeconds = ref(0)
  const pipelineEtaText = computed(() => {
    const endMs = Number(pipelineEstimatedEndMs.value || 0)
    const totalSec = Number(pipelineEstimatedTotalSeconds.value || 0)
    if (!endMs || !totalSec) return ''
    if (totalSec < 60) return `预计完成（约 ${totalSec}s）`
    const m = Math.floor(totalSec / 60)
    const sec = totalSec % 60
    return `预计完成（约 ${m}m${sec}s）`
  })

  const jobSkillHeatmapPayload = ref(null)
  const jobSkillHeatmapReady = computed(() =>
    Boolean(jobSkillHeatmapPayload.value && jobSkillHeatmapPayload.value.x && jobSkillHeatmapPayload.value.y)
  )
  const skillTimelinePayload = ref(null)
  const skillTimelineReady = computed(() =>
    Boolean(skillTimelinePayload.value && skillTimelinePayload.value.months && skillTimelinePayload.value.series)
  )
  const companySizeSalaryPayload = ref(null)
  const companySizeSalaryReady = computed(() =>
    Boolean(companySizeSalaryPayload.value && companySizeSalaryPayload.value.x && companySizeSalaryPayload.value.avg_salary)
  )
  const eduExpSalaryBubblePayload = ref(null)
  const eduExpSalaryBubbleReady = computed(() =>
    Boolean(eduExpSalaryBubblePayload.value && eduExpSalaryBubblePayload.value.x && eduExpSalaryBubblePayload.value.y)
  )
  const eduExpSalaryBubbleStatsText = computed(() => {
    const p = eduExpSalaryBubblePayload.value
    if (!p) return ''
    const total = Number(p.total_rows_used || 0)
    const shown = Number(p.shown_rows_used || 0)
    const sc = p.source_counts || {}
    const boss = Number(sc.job_info || 0)
    const job51 = Number(sc.job_info_51job || 0)
    if (total > 0 && shown > 0 && shown !== total) {
      if (boss > 0 || job51 > 0) return `参与统计：${total}，图上覆盖：${shown}（BOSS ${boss} / 51job ${job51}）`
      return `参与统计：${total}，图上覆盖：${shown}`
    }
    if (total > 0 && (boss > 0 || job51 > 0)) return `参与统计：${total}（BOSS ${boss} / 51job ${job51}）`
    if (total > 0) return `参与统计：${total}`
    return ''
  })

  const deepChartOptions = [
    { value: 'cluster', label: 'NLP 语义聚类地图' },
    { value: 'heatmap', label: '岗位-技能热力图' },
    { value: 'timeline', label: '前程技术招聘趋势' },
    { value: 'company_size_salary', label: '公司规模-薪资' },
    { value: 'edu_exp_bubble', label: '学历 × 经验 薪资气泡图' }
  ]
  const selectedDeepChart = ref(deepChartOptions[0]?.value || 'cluster')

  const jobSkillHeatmapRef = ref(null)
  const skillTimelineRef = ref(null)
  const companySizeSalaryRef = ref(null)
  const eduExpSalaryBubbleRef = ref(null)

  let jobSkillHeatmapChart = null
  let skillTimelineChart = null
  let companySizeSalaryChart = null
  let eduExpSalaryBubbleChart = null

  /**
   * 释放 pipeline 产物的 ObjectURL（仅对 blob→URL 的产物生效）。
   *
   * 关键点：
   * - URL.revokeObjectURL 需要传入之前 createObjectURL 的结果；
   * - 释放后同步清空展示用的图片 URL，避免 UI 继续引用已释放资源。
   */
  const revokePipelineUrls = () => {
    pipelineClusterSvg.value = ''
  }

  const ensureHeatmapChart = async () => {
    await nextTick()
    if (!jobSkillHeatmapRef.value) return false
    if (!jobSkillHeatmapChart) {
      jobSkillHeatmapChart = echarts.init(jobSkillHeatmapRef.value)
    }
    return Boolean(jobSkillHeatmapChart)
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
          axisLine: { lineStyle: { color: t.border } }
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
          axisLine: { lineStyle: { color: t.border } }
        },
        dataZoom: [
          { type: 'inside', xAxisIndex: 0 },
          { type: 'inside', yAxisIndex: 0 },
          {
            type: 'slider',
            xAxisIndex: 0,
            height: 14,
            bottom: 45,
            handleIcon:
              'path://M10.7,11.9v-1.3H9.3v1.3c-4.9,0.3-8.8,4.4-8.8,9.4c0,5,3.9,9.1,8.8,9.4v1.3h1.3v-1.3c4.9-0.3,8.8-4.4,8.8-9.4C19.5,16.3,15.6,12.2,10.7,11.9z M13.3,24.4H6.7V23h6.6V24.4z M13.3,19.6H6.7v-1.4h6.6V19.6z',
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
          textStyle: { color: t.ink2, fontWeight: '700', fontSize: 11 }
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
            }
          }
        ]
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

    const lineColors = ['#0ea5e9', '#f43f5e', '#10b981', '#f59e0b', '#8b5cf6', '#ec4899', '#06b6d4', '#f97316', '#84cc16', '#6366f1']
    const legendSelected = {}
    p.series.forEach((s, idx) => {
      legendSelected[s.name] = idx < 4
    })

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
          selected: legendSelected
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
              if (parts.length === 2) return `${parts[0].slice(2)}/${parts[1]}`
              return v
            }
          },
          axisLine: { lineStyle: { color: t.border } },
          axisTick: { show: false }
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
          axisLine: { show: false }
        },
        dataZoom: [
          { type: 'inside', xAxisIndex: 0 },
          {
            type: 'slider',
            xAxisIndex: 0,
            height: 18,
            bottom: 15,
            borderColor: 'transparent',
            fillerColor: 'rgba(15, 118, 110, 0.1)',
            handleIcon:
              'path://M10.7,11.9v-1.3H9.3v1.3c-4.9,0.3-8.8,4.4-8.8,9.4c0,5,3.9,9.1,8.8,9.4v1.3h1.3v-1.3c4.9-0.3,8.8-4.4,8.8-9.4C19.5,16.3,15.6,12.2,10.7,11.9z M13.3,24.4H6.7V23h6.6V24.4z M13.3,19.6H6.7v-1.4h6.6V19.6z',
            handleSize: '80%',
            handleStyle: {
              color: t.primary,
              shadowBlur: 3,
              shadowColor: 'rgba(0, 0, 0, 0.2)'
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
            emphasis: {
              focus: 'series',
              itemStyle: {
                borderColor: '#fff',
                borderWidth: 2,
                shadowBlur: 10,
                shadowColor: color
              },
              lineStyle: { width: 4 }
            }
          }
        })
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
    const salary = p.avg_salary.map((v) => Number(v || 0))
    const count = p.count.map((v) => Number(v || 0))

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
            fontSize: 11
          },
          axisTick: { show: false },
          axisLine: { lineStyle: { color: t.border } }
        },
        yAxis: {
          type: 'value',
          name: `平均薪资(${p.unit || 'K'})`,
          nameTextStyle: { color: t.ink3, fontWeight: '700', fontSize: 12 },
          axisLabel: { color: t.ink3, fontWeight: '600' },
          splitLine: { lineStyle: { color: t.border, type: 'dashed' } }
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
                { offset: 1, color: t.info }
              ]),
              borderRadius: [6, 6, 0, 0]
            },
            emphasis: { focus: 'series' }
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
            fontSize: 11
          },
          axisTick: { show: false },
          axisLine: { lineStyle: { color: t.border } },
          splitLine: { show: false }
        },
        yAxis: {
          type: 'category',
          data: y,
          axisLabel: {
            color: t.ink2,
            fontWeight: '800',
            fontSize: 11,
            margin: 14
          },
          axisTick: { show: false },
          axisLine: { lineStyle: { color: t.border } },
          splitLine: { show: true, lineStyle: { color: t.border, type: 'dashed' } }
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
          textStyle: { color: t.ink2, fontWeight: '700', fontSize: 11 }
        },
        series: [
          {
            type: 'scatter',
            data: (p.data || []).map((d) => [Number(d?.[0]), Number(d?.[1]), Number(d?.[2] || 0), Number(d?.[3] || 0)]),
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
            }
          }
        ]
      },
      true
    )
  }

  /**
   * 根据当前选中的深度图类型，按需渲染对应图表。
   *
   * 关键分支：
   * - 仅当抽屉可见时渲染（避免不可见状态下初始化 ECharts 导致尺寸为 0）；
   * - 每种图表只有在 payload 已加载时才渲染；
   * - 渲染后立即 resize 一次，适配抽屉打开后的布局尺寸。
   */
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
    }
  }

  /**
   * 拉取 pipeline 产物并按类型解析（图片/JSON blob）。
   *
   * 返回值说明：
   * - 返回 true 表示存在可用的历史产物，可用于“首次进入页面时直接展示上次结果”；
   * - 返回 false 则上层可选择触发一次新的 pipeline 运行。
   *
   * @returns {Promise<boolean>}
   */
  const loadPipelineArtifacts = async () => {
    const data = await pipelineApi.getPipelineArtifactsData()
    pipelineSummary.value = data.summary
    pipelineErrors.value = data.errors

    if (data.artifacts?.cluster_scatter_svg) {
      const blobRes = await pipelineApi.getPipelineFile('cluster_scatter_svg')
      if (blobRes?.data) {
        const raw = await blobRes.data.text()
        const cleaned = raw
          .replace(/^<\?xml[\s\S]*?\?>\s*/i, '')
          .replace(/<!DOCTYPE[\s\S]*?>\s*/i, '')
        pipelineClusterSvg.value = cleaned
      }
    }

    if (data.artifacts?.job_skill_heatmap_json) {
      const blobRes = await pipelineApi.getPipelineFile('job_skill_heatmap_json')
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
      const blobRes = await pipelineApi.getPipelineFile('skill_timeline_json')
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
      const blobRes = await pipelineApi.getPipelineFile('company_size_salary_json')
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
      const blobRes = await pipelineApi.getPipelineFile('edu_exp_salary_bubble_json')
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

  /**
   * 启动 Dashboard pipeline（聚类图 + 多种深度特征产物）。
   *
   * 关键分支：
   * - 若后端返回 cached 且非 force，则直接复用缓存结果并拉取产物；
   * - 否则进入轮询：通过 /pipeline/status 读取状态，直到 status !== running。
   *
   * @param {boolean} [force=false] 是否强制重新计算（忽略缓存）
   * @returns {Promise<void>}
   */
  const startPipeline = async (force = false) => {
    pipelineRunning.value = true
    pipelineProgress.value = 0
    pipelineEstimatedTotalSeconds.value = 120
    pipelineEstimatedEndMs.value = Date.now() + pipelineEstimatedTotalSeconds.value * 1000
    pipelineStatus.value = 'running'
    revokePipelineUrls()
    if (pipelinePollTimer.value) clearInterval(pipelinePollTimer.value)
    try {
      const runData = force ? await pipelineApi.runDashboardPipelineForceData() : await pipelineApi.runDashboardPipelineData()
      const cached = runData?.cached === true
      if (!force && cached) {
        pipelineRunning.value = false
        pipelineProgress.value = 100
        pipelineStatus.value = 'idle'
        pipelineMessage.value = runData?.message || '已使用缓存结果'
        await loadPipelineArtifacts()
        return
      }

      try {
        const s0 = await pipelineApi.getPipelineStatusData()
        if (s0?.estimatedEndMs) pipelineEstimatedEndMs.value = Number(s0.estimatedEndMs)
        if (s0?.estimatedTotalSeconds) pipelineEstimatedTotalSeconds.value = Number(s0.estimatedTotalSeconds)
      } catch {
      }

      pipelinePollTimer.value = setInterval(async () => {
        const s = await pipelineApi.getPipelineStatusData()
        pipelineStatus.value = s.status
        pipelineMessage.value = s.message
        if (s?.estimatedEndMs) pipelineEstimatedEndMs.value = Number(s.estimatedEndMs)
        if (s?.estimatedTotalSeconds) pipelineEstimatedTotalSeconds.value = Number(s.estimatedTotalSeconds)
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

  /**
   * 启动 Stats pipeline（主要用于刷新 Top Tokens）。
   *
   * 关键分支与策略与 startPipeline 一致，但轮询间隔更短。
   *
   * @param {boolean} [force=false] 是否强制重新计算
   * @returns {Promise<void>}
   */
  const startStatsPipeline = async (force = false) => {
    topTokensRunning.value = true
    pipelineStatus.value = 'running'
    pipelineMessage.value = '正在提取 Top Tokens'
    if (pipelinePollTimer.value) clearInterval(pipelinePollTimer.value)
    try {
      const runData = force ? await pipelineApi.runStatsPipelineForceData() : await pipelineApi.runStatsPipelineData()
      const cached = runData?.cached === true
      if (!force && cached) {
        topTokensRunning.value = false
        pipelineStatus.value = 'idle'
        pipelineMessage.value = runData?.message || '已使用缓存结果'
        await loadPipelineArtifacts()
        return
      }
      pipelinePollTimer.value = setInterval(async () => {
        const s = await pipelineApi.getPipelineStatusData()
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

  /**
   * 深度图表 resize（用于窗口大小变化/抽屉布局变化时适配）。
   *
   * @returns {void}
   */
  const resizeDeepCharts = () => {
    ;[jobSkillHeatmapChart, skillTimelineChart, companySizeSalaryChart, eduExpSalaryBubbleChart].forEach((c) => c?.resize())
  }

  watch(aiDrawerVisible, async (v) => {
    if (!v) return
    await renderSelectedDeepChart()
  })

  watch(selectedDeepChart, async () => {
    await renderSelectedDeepChart()
  })

  onMounted(() => {
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
  })

  onUnmounted(() => {
    if (pipelinePollTimer.value) clearInterval(pipelinePollTimer.value)
    revokePipelineUrls()
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

  return {
    aiDrawerVisible,
    pipelineRunning,
    topTokensRunning,
    pipelineStatus,
    pipelineMessage,
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
  }
}
