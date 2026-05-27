<template>
  <JobAnalysisLayout />
</template>

<script setup>
/**
 * 页面容器（Route Entry）
 *
 * 说明：
 * - 岗位分析是一个带子路由的布局页；
 * - 子路由仍由 views/JobAnalysisLayout 内部的 <router-view> 承载。
 */
import JobAnalysisLayout from '@/features/job-analysis/JobAnalysisLayoutView.vue'
</script>
