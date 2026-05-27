<template>
  <JobMatch />
</template>

<script setup>
/**
 * 页面容器（Route Entry）
 *
 * 说明：
 * - pages 作为路由入口层，避免业务页面与路由强耦合；
 * - 后续若引入布局/埋点/权限包装，可优先放在 pages 层完成。
 */
import JobMatch from '@/features/job-match/JobMatchView.vue'
</script>
