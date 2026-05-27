<template>
  <CompanyInsight />
</template>

<script setup>
/**
 * 页面容器（Route Entry）
 *
 * 说明：
 * - 公司洞察作为岗位分析模块下的子页面，由 views/CompanyInsight 负责实际渲染；
 * - pages 层只承担路由入口职责，减少路由与业务实现的耦合。
 */
import CompanyInsight from '@/features/job-analysis/CompanyInsightView.vue'
</script>
