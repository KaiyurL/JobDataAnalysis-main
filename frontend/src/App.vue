<template>
  <router-view v-if="isLoginPage" />

  <div v-else class="jd-shell">
    <header class="jd-nav">
      <div class="u-container jd-nav__inner">
        <button class="jd-brand" type="button" @click="router.push('/')">
          <span class="jd-brand__mark" aria-hidden="true">📊</span>
          <span class="jd-brand__text">
            <span class="jd-brand__name">JobData</span>
            <span class="jd-brand__tag">Pro</span>
          </span>
        </button>

        <AppNavMenu :items="visibleMenuItems" :active-path="currentRootPath" />

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
                <el-dropdown-item @click="openFavorites">
                  <el-icon><Star /></el-icon>
                  <span>收藏</span>
                </el-dropdown-item>
                <el-dropdown-item @click="openHistory">
                  <el-icon><Clock /></el-icon>
                  <span>历史</span>
                </el-dropdown-item>
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

    <el-dialog v-model="favoritesVisible" width="920px" title="⭐ 我的收藏" :append-to-body="true">
      <div v-if="favoritesLoading" style="padding: 12px 0">
        <el-skeleton :rows="6" animated />
      </div>
      <el-empty v-else-if="!favoriteList.length" description="暂无收藏" />
      <div v-else class="jd-jobs-grid">
        <el-card v-for="c in favoriteList" :key="c.key" shadow="hover" class="jd-job-card">
          <div class="jd-job-card__head">
            <div class="jd-job-card__title">{{ c.job.jobName }}</div>
            <div v-if="c.job.salaryMin != null && c.job.salaryMax != null" class="jd-job-card__salary">
              {{ c.job.salaryMin }}K-{{ c.job.salaryMax }}K
            </div>
            <div v-else class="jd-job-card__salary is-muted">面议</div>
          </div>
          <div class="jd-job-card__sub">
            {{ c.job.companyName }}<span v-if="c.job.city"> · {{ c.job.city }}</span>
          </div>
          <div class="jd-job-card__tags">
            <el-tag size="small" type="info" effect="plain">{{ c.job.experience || '经验不限' }}</el-tag>
            <el-tag size="small" type="info" effect="plain">{{ c.job.education || '学历不限' }}</el-tag>
            <el-tag size="small" :type="sourceTagType(c.sourceTable)" effect="plain">{{ sourceLabel(c.sourceTable) }}</el-tag>
          </div>
          <div class="jd-job-card__actions">
            <el-button
              size="small"
              type="warning"
              plain
              :loading="favoriteBusyKey === favoriteKeyOfCard(c)"
              @click.stop="toggleFavorite(c)"
            >
              <el-icon><StarFilled /></el-icon> 取消收藏
            </el-button>
            <el-button size="small" type="primary" plain @click.stop="openJobUrl(c.job.jobUrl)">
              打开 <el-icon><TopRight /></el-icon>
            </el-button>
          </div>
        </el-card>
      </div>
    </el-dialog>

    <el-dialog v-model="historyVisible" width="980px" title="🕘 历史" :append-to-body="true">
      <el-tabs v-model="historyTab" class="jd-tabs">
        <el-tab-pane name="view" label="浏览历史">
          <div v-if="jobHistoryLoading" style="padding: 12px 0">
            <el-skeleton :rows="6" animated />
          </div>
          <el-empty v-else-if="!jobHistoryList.length" description="暂无浏览历史" />
          <div v-else class="jd-jobs-grid">
            <el-card v-for="c in jobHistoryList" :key="c.key" shadow="hover" class="jd-job-card">
              <div class="jd-job-card__head">
                <div class="jd-job-card__title">{{ c.job.jobName }}</div>
                <div v-if="c.job.salaryMin != null && c.job.salaryMax != null" class="jd-job-card__salary">
                  {{ c.job.salaryMin }}K-{{ c.job.salaryMax }}K
                </div>
                <div v-else class="jd-job-card__salary is-muted">面议</div>
              </div>
              <div class="jd-job-card__sub">
                {{ c.job.companyName }}<span v-if="c.job.city"> · {{ c.job.city }}</span>
              </div>
              <div class="jd-job-card__tags">
                <el-tag size="small" type="info" effect="plain">{{ c.job.experience || '经验不限' }}</el-tag>
                <el-tag size="small" type="info" effect="plain">{{ c.job.education || '学历不限' }}</el-tag>
                <el-tag size="small" :type="sourceTagType(c.sourceTable)" effect="plain">{{ sourceLabel(c.sourceTable) }}</el-tag>
              </div>
              <div class="jd-job-card__meta">最近：{{ formatTimeDisplay(c.updatedAt) }}</div>
              <div class="jd-job-card__actions">
                <el-button
                  size="small"
                  :type="isFavorite(c.sourceTable, c.job.jobUrl) ? 'warning' : 'default'"
                  plain
                  :loading="favoriteBusyKey === favoriteKeyOfCard(c)"
                  @click.stop="toggleFavorite(c)"
                >
                  <el-icon><StarFilled v-if="isFavorite(c.sourceTable, c.job.jobUrl)" /><Star v-else /></el-icon>
                  {{ isFavorite(c.sourceTable, c.job.jobUrl) ? '已收藏' : '收藏' }}
                </el-button>
                <el-button size="small" type="primary" plain @click.stop="openJobUrl(c.job.jobUrl)">
                  打开 <el-icon><TopRight /></el-icon>
                </el-button>
              </div>
            </el-card>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, provide } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { SwitchButton, ArrowDown, Star, StarFilled, Clock, TopRight } from '@element-plus/icons-vue'
import { useUserDataStore } from '@/composables/useUserDataStore.js'
import { filterNavItemsByRole } from '@/app/navConfig.js'
import AppNavMenu from '@/components/AppNavMenu.vue'
import { clearAuth, getUserInfo } from '@/shared/authStorage.js'

const router = useRouter()
const route = useRoute()
const updateTime = ref('')

const isLoginPage = computed(() => route.path === '/login')

const currentRootPath = computed(() => {
  const path = route.path
  if (path.startsWith('/job-analysis')) return '/job-analysis'
  return path
})

const userInfo = computed(() => {
  return getUserInfo()
})

const visibleMenuItems = computed(() => {
  const role = String(userInfo.value?.role || 'user').toLowerCase()
  return filterNavItemsByRole(role)
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
    clearAuth()
    ElMessage.success('退出登录成功')
    router.push('/login')
  } catch {}
}

onMounted(async () => {
  formatTime()
})

// 用户数据（收藏/历史）下沉到 composable，并通过 provide 继续对页面暴露跨页能力
const {
  favoritesVisible,
  favoritesLoading,
  favoriteBusyKey,
  favoriteKeyOfCard,
  favoriteList,
  historyVisible,
  historyTab,
  jobHistoryLoading,
  jobHistoryList,
  openFavorites,
  openHistory,
  toggleFavorite,
  openJobUrl,
  sourceLabel,
  sourceTagType,
  formatTimeDisplay,
  providePayload
} = useUserDataStore({ isLoginPage })

provide('userDataStore', providePayload)

// 页面时钟（顶部显示）使用 interval 实时更新，组件卸载时需清理，避免潜在的内存泄漏
let clockTimer = null
onMounted(() => {
  clockTimer = setInterval(formatTime, 1000)
})

onUnmounted(() => {
  if (clockTimer) clearInterval(clockTimer)
  clockTimer = null
})
</script>

<style scoped>
.jd-jobs-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.jd-job-card {
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.1);
}

.jd-job-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 6px;
}

.jd-job-card__title {
  font-weight: 800;
  color: var(--c-ink);
  line-height: 1.2;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.jd-job-card__salary {
  font-weight: 800;
  color: var(--c-primary-700);
  white-space: nowrap;
}

.jd-job-card__salary.is-muted {
  color: var(--c-ink-3);
}

.jd-job-card__sub {
  color: var(--c-ink-2);
  font-weight: 600;
  margin-bottom: 10px;
}

.jd-job-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}

.jd-job-card__meta {
  font-size: 12px;
  color: var(--c-ink-3);
  margin-bottom: 10px;
}

.jd-job-card__actions {
  display: flex;
  gap: 8px;
}

@media (max-width: 980px) {
  .jd-jobs-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .jd-jobs-grid {
    grid-template-columns: 1fr;
  }
}
</style>
