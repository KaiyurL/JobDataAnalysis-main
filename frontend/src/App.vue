<template>
  <router-view v-if="isLoginPage" />
  
  <div class="jd-shell" v-else>
    <header class="jd-nav">
      <div class="u-container jd-nav__inner">
        <button class="jd-brand" type="button" @click="router.push('/')">
          <span class="jd-brand__mark" aria-hidden="true">📊</span>
          <span class="jd-brand__text">
            <span class="jd-brand__name">JobData</span>
            <span class="jd-brand__tag">Pro</span>
          </span>
        </button>

        <nav class="jd-nav__menu" aria-label="主导航">
          <router-link
            v-for="item in visibleMenuItems"
            :key="item.path"
            :to="item.path"
            class="jd-nav__item"
            :class="{ 'is-active': currentRootPath === item.path }"
          >
            <el-icon class="jd-nav__icon"><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </router-link>
        </nav>

        <div class="jd-nav__right">
          <div class="jd-clock" aria-label="当前时间">
            <span class="jd-clock__dot" aria-hidden="true"></span>
            <span>{{ updateTime }}</span>
          </div>

          <el-dropdown trigger="click">
            <button class="jd-user" type="button">
              <span class="jd-user__avatar">{{ userInfo?.username?.charAt(0).toUpperCase() || 'U' }}</span>
              <span class="jd-user__name">{{ userInfo?.username || 'User' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon>
                  <span>退出登录</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </header>

    <main class="jd-main">
      <div class="u-container">
        <router-view v-slot="{ Component, route: r }">
          <keep-alive include="Dashboard">
            <component :is="Component" v-if="r.meta.keepAlive" />
          </keep-alive>
          <transition name="jd-fade" mode="out-in">
            <component :is="Component" v-if="!r.meta.keepAlive" />
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
  { path: '/data-management', title: '系统设置', icon: Setting, roles: ['admin'] },
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

const visibleMenuItems = computed(() => {
  const role = String(userInfo.value?.role || 'user').toLowerCase()
  return menuItems.filter(item => {
    if (!Array.isArray(item.roles) || item.roles.length === 0) return true
    return item.roles.map(r => String(r).toLowerCase()).includes(role)
  })
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
