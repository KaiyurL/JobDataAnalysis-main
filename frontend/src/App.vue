<template>
  <router-view v-if="isLoginPage" />
  
  <el-container class="app-container" v-else>
    <el-aside width="220px" class="sidebar">
      <div class="logo">
        <h2>📊 招聘数据</h2>
      </div>
      <el-menu
      :default-active="activeMenu"
      router
      background-color="#304156"
      text-color="#bfcbd9"
      active-text-color="#409EFF"
    >
      <el-menu-item index="/dashboard">
        <el-icon><Odometer /></el-icon>
        <span>仪表盘</span>
      </el-menu-item>
      <el-menu-item index="/job-analysis">
        <el-icon><Briefcase /></el-icon>
        <span>岗位分析</span>
      </el-menu-item>
      <el-menu-item index="/skill-analysis">
        <el-icon><TrendCharts /></el-icon>
        <span>技能分析</span>
      </el-menu-item>
      <el-menu-item index="/salary-predict">
        <el-icon><Money /></el-icon>
        <span>薪资预测</span>
      </el-menu-item>
      <el-menu-item index="/job-match">
        <el-icon><User /></el-icon>
        <span>岗位匹配</span>
      </el-menu-item>
      <el-menu-item index="/company-insight">
        <el-icon><OfficeBuilding /></el-icon>
        <span>公司洞察</span>
      </el-menu-item>
      <el-menu-item index="/data-management">
        <el-icon><Setting /></el-icon>
        <span>数据管理</span>
      </el-menu-item>
    </el-menu>
    </el-aside>
    
    <el-container class="main-container">
      <el-header class="header">
        <div class="header-title">{{ currentTitle }}</div>
        <div class="header-right">
          <span class="update-time">数据更新时间：{{ updateTime }}</span>
          <div class="user-section">
            <el-dropdown>
              <span class="user-info">
                <el-icon><User /></el-icon>
                <span class="username">{{ userInfo?.username || '用户' }}</span>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </el-header>
      
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Odometer, Briefcase, TrendCharts, Money, User, OfficeBuilding, Setting, SwitchButton } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const activeMenu = computed(() => route.path)
const currentTitle = computed(() => route.meta.title || '招聘数据可视化')
const updateTime = ref('')

const isLoginPage = computed(() => route.path === '/login')

const userInfo = computed(() => {
  const stored = localStorage.getItem('userInfo') || sessionStorage.getItem('userInfo')
  return stored ? JSON.parse(stored) : null
})

const formatTime = () => {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  const hours = String(now.getHours()).padStart(2, '0')
  const minutes = String(now.getMinutes()).padStart(2, '0')
  const seconds = String(now.getSeconds()).padStart(2, '0')
  updateTime.value = `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗?', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    localStorage.removeItem('token')
    sessionStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    sessionStorage.removeItem('userInfo')
    
    ElMessage.success('退出登录成功')
    router.push('/login')
  } catch {
    // 取消
  }
}

onMounted(() => {
  formatTime()
  setInterval(formatTime, 1000)
})
</script>

<style scoped>
.app-container {
  height: 100vh;
  background-color: #f0f2f5;
}

.sidebar {
  background-color: #304156;
  box-shadow: 2px 0 6px rgba(0, 21, 41, 0.35);
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #263445;
}

.logo h2 {
  color: #fff;
  font-size: 18px;
  margin: 0;
}

.main-container {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.header {
  background-color: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.header-title {
  font-size: 20px;
  font-weight: bold;
  color: #333;
}

.header-right {
  color: #666;
  font-size: 14px;
}

.update-time {
  margin-right: 10px;
}

.main-content {
  padding: 20px;
  overflow-y: auto;
  background-color: #f0f2f5;
}
</style>
