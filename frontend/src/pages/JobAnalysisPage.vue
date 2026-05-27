<template>
  <JobAnalysis />
</template>

<script setup>
/**
 * 页面容器（Route Entry）
 *
 * 说明：
 * - JobAnalysis 作为 /job-analysis 下的子路由页面（BOSS / 51job 共用组件）；
 * - pages 层仅作为路由入口，便于后续在入口处统一做埋点/权限包装等扩展。
 */
import JobAnalysis from '@/features/job-analysis/JobAnalysisView.vue'
</script>
