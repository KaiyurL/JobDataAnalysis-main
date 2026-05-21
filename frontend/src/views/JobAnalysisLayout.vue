<template>
  <div class="job-analysis-layout">
    <!-- 统一的子导航栏 -->
    <div class="sub-nav">
      <div class="sub-nav-container">
        <div class="nav-group">
          <span class="group-label">数据源</span>
          <router-link to="/job-analysis/boss" class="sub-nav-item" :class="{ active: currentPath.includes('/boss') }">
            BOSS直聘
          </router-link>
          <router-link to="/job-analysis/51job" class="sub-nav-item" :class="{ active: currentPath.includes('/51job') }">
            前程无忧
          </router-link>
        </div>
        <div class="divider"></div>
        <div class="nav-group">
          <span class="group-label">专项分析</span>
          <router-link to="/job-analysis/skills" class="sub-nav-item" :class="{ active: currentPath.includes('/skills') }">
            技能雷达
          </router-link>
          <router-link to="/job-analysis/salary" class="sub-nav-item" :class="{ active: currentPath.includes('/salary') }">
            薪资预测
          </router-link>
          <router-link to="/job-analysis/insight" class="sub-nav-item" :class="{ active: currentPath.includes('/insight') }">
            公司洞察
          </router-link>
        </div>
      </div>
    </div>

    <!-- 子页面内容 -->
    <div class="analysis-view-container">
      <router-view v-slot="{ Component }">
        <transition name="fade-slide" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const currentPath = computed(() => route.path)
</script>

<style scoped>
.job-analysis-layout {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* 子导航栏样式 */
.sub-nav {
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(12px);
  padding: 12px 0;
  border-radius: 16px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.03);
  border: 1px solid rgba(255,255,255,0.5);
  position: sticky;
  top: 0;
  z-index: 100;
}

.sub-nav-container {
  display: flex;
  align-items: center;
  padding: 0 24px;
  gap: 20px;
}

.nav-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.group-label {
  font-size: 11px;
  color: #94a3b8;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-right: 8px;
}

.sub-nav-item {
  padding: 8px 18px;
  border-radius: 10px;
  color: #64748b;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  text-decoration: none;
}

.sub-nav-item:hover {
  background: #f1f5f9;
  color: #4f46e5;
  transform: translateY(-1px);
}

.sub-nav-item.active {
  background: #4f46e5;
  color: #ffffff;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.25);
}

.divider {
  width: 1px;
  height: 20px;
  background: #e2e8f0;
}

.analysis-view-container {
  min-height: calc(100vh - 200px);
}

/* 过渡动画 */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateX(10px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateX(-10px);
}
</style>
