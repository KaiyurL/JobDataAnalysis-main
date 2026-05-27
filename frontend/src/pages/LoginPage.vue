<template>
  <Login />
</template>

<script setup>
/**
 * 页面容器（Route Entry）
 *
 * 说明：
 * - 登录页作为独立入口，避免与 App 布局耦合；
 * - 具体表单校验/登录逻辑仍由 views/Login 承担。
 */
import Login from '@/features/auth/LoginView.vue'
</script>
