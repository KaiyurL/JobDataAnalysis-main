<template>
  <nav class="jd-nav__menu" aria-label="主导航">
    <router-link
      v-for="item in items"
      :key="item.path"
      :to="item.path"
      class="jd-nav__item"
      :class="{ 'is-active': activePath === item.path }"
    >
      <el-icon class="jd-nav__icon"><component :is="item.icon" /></el-icon>
      <span>{{ item.title }}</span>
    </router-link>
  </nav>
</template>

<script setup>
/**
 * 顶部导航菜单（App Header Nav）
 *
 * 设计目标：
 * - 将 App.vue 中导航渲染逻辑组件化，减少根组件模板体积；
 * - 仅接收“已过滤后的可见菜单项”，不在组件内部做权限判断；
 * - activePath 由上层计算（例如将 /job-analysis/boss 归一为 /job-analysis）。
 */
defineProps({
  items: {
    type: Array,
    required: true
  },
  activePath: {
    type: String,
    required: true
  }
})
</script>
