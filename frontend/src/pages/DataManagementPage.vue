<template>
  <DataManagement />
</template>

<script setup>
/**
 * 页面容器（Route Entry）
 *
 * 说明：
 * - pages 层只做路由入口与页面组装；
 * - 数据管理的状态/请求逻辑已下沉到 feature 内的 composables/useDataManagement。
 */
import DataManagement from '@/features/data-management/DataManagementView.vue'
</script>
