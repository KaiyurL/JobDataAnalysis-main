<template>
  <Dashboard />
</template>

<script setup>
/**
 * 页面容器（Route Entry）
 *
 * 说明：
 * - pages 只负责作为路由入口与页面组装层；
 * - 具体业务实现下沉到 features（feature-first），便于按领域收口维护。
 */
import Dashboard from '@/features/dashboard/DashboardView.vue'
</script>
