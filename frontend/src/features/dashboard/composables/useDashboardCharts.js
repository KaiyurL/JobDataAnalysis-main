import { nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import 'echarts-wordcloud'

/**
 * 仪表盘基础图表管理（Dashboard Base Charts）
 *
 * 覆盖范围：
 * - ECharts 实例创建/更新/resize/销毁（城市柱状图、学历雷达图、经验曲线、行业饼图、词云、Top10 柱状图）
 * - Tab 切换后的延迟 resize（避免 v-show 切换导致容器尺寸为 0）
 * - 城市图表点击跳转到岗位分析页（携带 city query）
 *
 * 设计目标：
 * - 将 Dashboard.vue 中与“基础图表渲染”相关的代码聚合，降低页面脚本体积；
 * - 仅处理图表层逻辑，不包含数据请求与融合计算；
 * - 与现有页面保持一致的视觉与交互行为（图表 option 结构不变）。
 *
 * @param {{
 *  theme: () => Record<string, string>,
 *  leftTab: import('vue').Ref<string>,
 *  rightTab: import('vue').Ref<string>,
 *  refs: {
 *    cityChartRef: import('vue').Ref<any>,
 *    eduChartRef: import('vue').Ref<any>,
 *    expChartRef: import('vue').Ref<any>,
 *    industryChartRef: import('vue').Ref<any>,
 *    wordCloudRef: import('vue').Ref<any>,
 *    skillBarRef: import('vue').Ref<any>,
 *  },
 *  data: {
 *    cityData: import('vue').Ref<Array<any>>,
 *    eduData: import('vue').Ref<Array<any>>,
 *    expData: import('vue').Ref<Array<any>>,
 *    industryData: import('vue').Ref<Array<any>>,
 *    keywordData: import('vue').Ref<Array<any>>,
 *  }
 * }} params
 * @returns {{
 *  initBaseCharts: () => void,
 *  updateBaseCharts: () => void,
 *  resizeBaseCharts: () => void,
 *  disposeBaseCharts: () => void,
 * }}
 */
export function useDashboardCharts(params) {
  const router = useRouter()
  const theme = typeof params?.theme === 'function' ? params.theme : () => ({})

  const leftTab = params?.leftTab
  const rightTab = params?.rightTab
  const refs = params?.refs || {}
  const data = params?.data || {}

  const cityData = data.cityData
  const eduData = data.eduData
  const expData = data.expData
  const industryData = data.industryData
  const keywordData = data.keywordData

  let cityChart = null
  let eduChart = null
  let expChart = null
  let industryChart = null
  let wordCloudChart = null
  let skillBarChart = null

  /**
   * 创建基础图表实例，并注册必要的交互事件。
   *
   * 关键点：
   * - 仅在 ref 对应 DOM 已挂载后调用；
   * - 城市图表注册 click 事件，用于跳转岗位分析页并携带城市筛选参数。
   */
  const initBaseCharts = () => {
    const cityEl = refs.cityChartRef?.value
    const eduEl = refs.eduChartRef?.value
    const expEl = refs.expChartRef?.value
    const industryEl = refs.industryChartRef?.value
    const wordCloudEl = refs.wordCloudRef?.value
    const skillBarEl = refs.skillBarRef?.value

    if (!cityEl || !eduEl || !expEl || !industryEl || !wordCloudEl || !skillBarEl) return

    cityChart = echarts.init(cityEl)
    eduChart = echarts.init(eduEl)
    expChart = echarts.init(expEl)
    industryChart = echarts.init(industryEl)
    wordCloudChart = echarts.init(wordCloudEl)
    skillBarChart = echarts.init(skillBarEl)

    cityChart.on('click', (p) => router.push({ path: '/job-analysis', query: { city: p.name } }))
  }

  /**
   * 根据当前数据刷新所有基础图表的 option。
   *
   * 约束：
   * - 若图表实例未初始化，则直接返回；
   * - option 结构与旧实现保持一致，确保视觉与交互不发生变化。
   */
  const updateBaseCharts = () => {
    if (!cityChart || !eduChart || !expChart || !industryChart || !wordCloudChart || !skillBarChart) return
    const t = theme()

    const chartConfigs = [
      {
        instance: cityChart,
        option: {
          tooltip: { trigger: 'axis', appendToBody: true, confine: true, extraCssText: 'z-index: 99999;' },
          dataZoom: [{ type: 'inside' }, { type: 'slider', height: 12, bottom: 0, showDetail: false }],
          grid: { bottom: 86, left: 40, right: 20, top: 40 },
          xAxis: {
            data: (cityData?.value || []).map((d) => d.city),
            axisLabel: { rotate: 45, interval: 0, hideOverlap: false },
            axisTick: { alignWithLabel: true }
          },
          yAxis: { name: 'K' },
          series: [
            {
              type: 'bar',
              data: (cityData?.value || []).map((d) => d.avgSalary),
              itemStyle: {
                color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                  { offset: 0, color: t.primary2 },
                  { offset: 1, color: t.info }
                ]),
                borderRadius: [6, 6, 0, 0]
              }
            }
          ]
        }
      },
      {
        instance: eduChart,
        option: {
          tooltip: { trigger: 'item', appendToBody: true, confine: true, extraCssText: 'z-index: 99999;' },
          radar: {
            indicator: (eduData?.value || []).map((d) => ({
              name: d.education,
              max: Math.max(1, ...(eduData?.value || []).map((x) => Number(x.avgSalary || 0))) * 1.2
            })),
            radius: '60%',
            center: ['50%', '55%'],
            splitNumber: 4,
            axisName: { color: t.ink2, fontSize: 10, fontWeight: 'bold' },
            splitArea: { areaStyle: { color: ['rgba(255,255,255,0.55)', 'rgba(255,255,255,0.30)'] } },
            splitLine: { lineStyle: { color: t.border } },
            axisLine: { lineStyle: { color: t.border } }
          },
          series: [
            {
              type: 'radar',
              data: [{ value: (eduData?.value || []).map((d) => d.avgSalary), name: '平均薪资(K)' }],
              itemStyle: { color: t.accent, borderWidth: 2 },
              lineStyle: { width: 2, color: t.accent },
              areaStyle: { color: 'rgba(255, 107, 74, 0.28)' },
              symbolSize: 6
            }
          ]
        }
      },
      {
        instance: expChart,
        option: {
          tooltip: { trigger: 'axis', appendToBody: true, confine: true, extraCssText: 'z-index: 99999;' },
          xAxis: { type: 'category', data: (expData?.value || []).map((d) => d.experience), boundaryGap: false },
          yAxis: { type: 'value' },
          series: [
            {
              type: 'line',
              smooth: true,
              data: (expData?.value || []).map((d) => d.avgSalary),
              lineStyle: { width: 4, color: t.success },
              areaStyle: {
                color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                  { offset: 0, color: 'rgba(22, 163, 74, 0.28)' },
                  { offset: 1, color: 'transparent' }
                ])
              }
            }
          ]
        }
      },
      {
        instance: industryChart,
        option: {
          tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)', appendToBody: true, confine: true, extraCssText: 'z-index: 99999;' },
          color: [
            t.primary,
            t.accent,
            t.info,
            t.success,
            t.warning,
            t.danger,
            t.primary2,
            'rgba(15, 118, 110, 0.55)',
            'rgba(255, 107, 74, 0.55)',
            'rgba(37, 99, 235, 0.55)'
          ],
          legend: {
            type: 'scroll',
            orient: 'vertical',
            right: 0,
            top: 'middle',
            textStyle: { fontSize: 11, color: t.ink2 },
            itemWidth: 10,
            itemHeight: 10,
            formatter: function (name) {
              return name.length > 6 ? name.substring(0, 6) + '...' : name
            }
          },
          series: [
            {
              type: 'pie',
              radius: ['48%', '74%'],
              center: ['34%', '50%'],
              itemStyle: { borderRadius: 6, borderColor: 'rgba(255,255,255,0.92)', borderWidth: 2 },
              label: { show: false },
              labelLine: { show: false },
              data: (industryData?.value || []).map((d) => ({ name: d.industry, value: d.count }))
            }
          ]
        }
      },
      {
        instance: wordCloudChart,
        option: {
          series: [
            {
              type: 'wordCloud',
              sizeRange: [12, 45],
              rotationRange: [-45, 45],
              gridSize: 8,
              textStyle: {
                fontFamily: 'IBM Plex Sans, sans-serif',
                fontWeight: 'bold',
                color: () => `hsl(${Math.random() * 360}, 64%, 54%)`
              },
              data: (keywordData?.value || []).map((d) => ({ name: d.keyword, value: d.count }))
            }
          ]
        }
      },
      {
        instance: skillBarChart,
        option: {
          tooltip: { trigger: 'axis', appendToBody: true, confine: true, extraCssText: 'z-index: 99999;' },
          grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
          xAxis: { type: 'value' },
          yAxis: {
            type: 'category',
            data: (keywordData?.value || [])
              .slice(0, 10)
              .reverse()
              .map((d) => d.keyword)
          },
          series: [
            {
              type: 'bar',
              data: (keywordData?.value || [])
                .slice(0, 10)
                .reverse()
                .map((d) => d.count),
              itemStyle: {
                color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
                  { offset: 0, color: t.primary },
                  { offset: 1, color: t.accent }
                ])
              },
              barWidth: 14,
              emphasis: { focus: 'series' }
            }
          ]
        }
      }
    ]

    chartConfigs.forEach((c) =>
      c.instance.setOption(c.option, {
        notMerge: false,
        replaceMerge: ['series', 'xAxis', 'yAxis']
      })
    )
  }

  /**
   * 统一触发基础图表 resize，用于窗口尺寸变化或布局变化。
   *
   * @returns {void}
   */
  const resizeBaseCharts = () => {
    ;[cityChart, eduChart, expChart, industryChart, wordCloudChart, skillBarChart].forEach((c) => c?.resize())
  }

  /**
   * 销毁基础图表实例，释放事件与 DOM 绑定资源。
   *
   * @returns {void}
   */
  const disposeBaseCharts = () => {
    ;[cityChart, eduChart, expChart, industryChart, wordCloudChart, skillBarChart].forEach((c) => c?.dispose())
    cityChart = null
    eduChart = null
    expChart = null
    industryChart = null
    wordCloudChart = null
    skillBarChart = null
  }

  watch(leftTab, async () => {
    await nextTick()
    eduChart?.resize()
    industryChart?.resize()
  })

  watch(rightTab, async () => {
    await nextTick()
    wordCloudChart?.resize()
    skillBarChart?.resize()
  })

  return {
    initBaseCharts,
    updateBaseCharts,
    resizeBaseCharts,
    disposeBaseCharts
  }
}
