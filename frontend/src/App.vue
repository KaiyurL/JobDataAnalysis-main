<template>
  <router-view v-if="isLoginPage" />
  
  <div class="app-layout" v-else>
    <!-- 顶部导航栏 -->
    <header class="navbar">
      <div class="navbar-container">
        <div class="brand" @click="router.push('/')">
          <div class="brand-icon">📊</div>
          <span class="brand-text">JobData <span class="highlight">Pro</span></span>
        </div>

        <nav class="nav-links">
          <router-link 
            v-for="item in menuItems" 
            :key="item.path" 
            :to="item.path"
            class="nav-item"
            :class="{ active: currentRootPath === item.path }"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
            <div class="active-dot"></div>
          </router-link>
        </nav>

        <div class="navbar-right">
          <div class="update-info">
            <span class="dot"></span>
            {{ updateTime }}
          </div>
          
          <el-dropdown trigger="click">
            <div class="user-profile">
              <div class="avatar">{{ userInfo?.username?.charAt(0).toUpperCase() || 'U' }}</div>
              <span class="username">{{ userInfo?.username || 'User' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu class="user-dropdown">
                <el-dropdown-item @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </header>

    <!-- 主内容区 -->
    <main class="main-viewport">
      <div class="content-wrapper">
        <router-view v-slot="{ Component }">
          <transition name="fade-transform" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Odometer, Briefcase, User, Setting, SwitchButton, ArrowDown } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const updateTime = ref('')

const isLoginPage = computed(() => route.path === '/login')

const menuItems = [
  { path: '/dashboard', title: '数据仪表盘', icon: Odometer },
  { path: '/job-analysis', title: '岗位分析', icon: Briefcase },
  { path: '/job-match', title: '智能助手', icon: User },
  { path: '/data-management', title: '系统设置', icon: Setting },
]

const currentRootPath = computed(() => {
  const path = route.path
  if (path.startsWith('/job-analysis')) return '/job-analysis'
  return path
})

const userInfo = computed(() => {
  const stored = localStorage.getItem('userInfo') || sessionStorage.getItem('userInfo')
  return stored ? JSON.parse(stored) : null
})

const formatTime = () => {
  const now = new Date()
  updateTime.value = now.toLocaleTimeString()
}

const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗?', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
      customClass: 'custom-message-box'
    })
    localStorage.removeItem('token')
    sessionStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    sessionStorage.removeItem('userInfo')
    ElMessage.success('退出登录成功')
    router.push('/login')
  } catch { }
}

onMounted(() => {
  formatTime()
  setInterval(formatTime, 1000)
})
</script>

<style scoped>
.app-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: #f8fafc;
}

/* 顶部导航栏样式 - 非对称/动态感设计 */
.navbar {
  position: sticky;
  top: 0;
  z-index: 1000;
  height: 72px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(226, 232, 240, 0.8);
  padding: 0 40px;
}

.navbar-container {
  max-width: 1440px;
  margin: 0 auto;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}

.brand-icon {
  font-size: 24px;
  background: #f1f5f9;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
}

.brand-text {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -0.5px;
}

.highlight {
  color: #4f46e5;
}

.nav-links {
  display: flex;
  gap: 8px;
  background: #f1f5f9;
  padding: 6px;
  border-radius: 14px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 10px;
  color: #64748b;
  font-weight: 600;
  font-size: 14px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
}

.nav-item:hover {
  color: #4f46e5;
  background: rgba(255, 255, 255, 0.5);
}

.nav-item.active {
  background: #ffffff;
  color: #4f46e5;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.active-dot {
  position: absolute;
  bottom: 4px;
  left: 50%;
  transform: translateX(-50%);
  width: 4px;
  height: 4px;
  background: #4f46e5;
  border-radius: 50%;
  opacity: 0;
  transition: opacity 0.3s;
}

.nav-item.active .active-dot {
  opacity: 1;
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: 24px;
}

.update-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #94a3b8;
  background: #fff;
  padding: 6px 12px;
  border-radius: 20px;
  border: 1px solid #e2e8f0;
}

.dot {
  width: 6px;
  height: 6px;
  background: #22c55e;
  border-radius: 50%;
  box-shadow: 0 0 0 2px rgba(34, 197, 94, 0.2);
}

.user-profile {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 12px;
  transition: background 0.3s;
}

.user-profile:hover {
  background: #f1f5f9;
}

.avatar {
  width: 32px;
  height: 32px;
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
  color: white;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
}

.username {
  font-weight: 600;
  color: #334155;
}

.main-viewport {
  flex: 1;
  padding: 32px 40px;
  overflow-y: auto;
}

.content-wrapper {
  max-width: 1440px;
  margin: 0 auto;
}

/* 页面切换动画 */
.fade-transform-enter-active,
.fade-transform-leave-active {
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
