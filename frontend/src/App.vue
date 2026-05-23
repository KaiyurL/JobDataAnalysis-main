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
      <div v-if="favoritesLoading" style="padding: 12px 0;">
        <el-skeleton :rows="6" animated />
      </div>
      <el-empty v-else-if="!favoriteList.length" description="暂无收藏" />
      <div v-else class="jd-jobs-grid">
        <el-card v-for="c in favoriteList" :key="c.key" shadow="hover" class="jd-job-card">
          <div class="jd-job-card__head">
            <div class="jd-job-card__title">{{ c.job.jobName }}</div>
            <div class="jd-job-card__salary" v-if="c.job.salaryMin != null && c.job.salaryMax != null">
              {{ c.job.salaryMin }}K-{{ c.job.salaryMax }}K
            </div>
            <div class="jd-job-card__salary is-muted" v-else>面议</div>
          </div>
          <div class="jd-job-card__sub">{{ c.job.companyName }}<span v-if="c.job.city"> · {{ c.job.city }}</span></div>
          <div class="jd-job-card__tags">
            <el-tag size="small" type="info" effect="plain">{{ c.job.experience || '经验不限' }}</el-tag>
            <el-tag size="small" type="info" effect="plain">{{ c.job.education || '学历不限' }}</el-tag>
            <el-tag size="small" :type="sourceTagType(c.sourceTable)" effect="plain">{{ sourceLabel(c.sourceTable) }}</el-tag>
          </div>
          <div class="jd-job-card__actions">
            <el-button size="small" type="warning" plain @click.stop="toggleFavorite(c)" :loading="favoriteBusyKey === favoriteKeyOfCard(c)">
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
          <div v-if="jobHistoryLoading" style="padding: 12px 0;">
            <el-skeleton :rows="6" animated />
          </div>
          <el-empty v-else-if="!jobHistoryList.length" description="暂无浏览历史" />
          <div v-else class="jd-jobs-grid">
            <el-card v-for="c in jobHistoryList" :key="c.key" shadow="hover" class="jd-job-card">
              <div class="jd-job-card__head">
                <div class="jd-job-card__title">{{ c.job.jobName }}</div>
                <div class="jd-job-card__salary" v-if="c.job.salaryMin != null && c.job.salaryMax != null">
                  {{ c.job.salaryMin }}K-{{ c.job.salaryMax }}K
                </div>
                <div class="jd-job-card__salary is-muted" v-else>面议</div>
              </div>
              <div class="jd-job-card__sub">{{ c.job.companyName }}<span v-if="c.job.city"> · {{ c.job.city }}</span></div>
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
                  @click.stop="toggleFavorite(c)"
                  :loading="favoriteBusyKey === favoriteKeyOfCard(c)"
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
        <el-tab-pane name="match" label="匹配历史">
          <div v-if="matchHistoryLoading" style="padding: 12px 0;">
            <el-skeleton :rows="6" animated />
          </div>
          <el-empty v-else-if="!matchHistory.length" description="暂无匹配历史" />
          <div v-else>
            <el-table :data="matchHistory" style="width: 100%">
              <el-table-column prop="targetRole" label="目标岗位" min-width="160" />
              <el-table-column prop="city" label="城市" min-width="120" />
              <el-table-column label="时间" min-width="180">
                <template #default="{ row }">
                  {{ formatTimeDisplay(row.createdAt) }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120">
                <template #default="{ row }">
                  <el-button size="small" type="primary" plain @click="openMatchHistoryDetail(row.id)">查看</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

    <el-dialog v-model="matchHistoryDetailVisible" width="920px" title="匹配历史详情" :append-to-body="true">
      <div v-if="matchHistoryDetailLoading" style="padding: 12px 0;">
        <el-skeleton :rows="6" animated />
      </div>
      <el-empty v-else-if="!matchHistoryDetailCards.length" description="无可展示的匹配结果" />
      <div v-else class="jd-jobs-grid">
        <el-card v-for="c in matchHistoryDetailCards" :key="c.key" shadow="hover" class="jd-job-card">
          <div class="jd-job-card__head">
            <div class="jd-job-card__title">{{ c.job.jobName }}</div>
            <div class="jd-job-card__salary" v-if="c.job.salaryMin != null && c.job.salaryMax != null">
              {{ c.job.salaryMin }}K-{{ c.job.salaryMax }}K
            </div>
            <div class="jd-job-card__salary is-muted" v-else>面议</div>
          </div>
          <div class="jd-job-card__sub">{{ c.job.companyName }}<span v-if="c.job.city"> · {{ c.job.city }}</span></div>
          <div class="jd-job-card__tags">
            <el-tag size="small" type="info" effect="plain">{{ c.job.experience || '经验不限' }}</el-tag>
            <el-tag size="small" type="info" effect="plain">{{ c.job.education || '学历不限' }}</el-tag>
            <el-tag size="small" :type="sourceTagType(c.sourceTable)" effect="plain">{{ sourceLabel(c.sourceTable) }}</el-tag>
          </div>
          <div class="jd-job-card__meta" v-if="c.matchScore != null">匹配度：{{ Math.round((Number(c.matchScore) || 0) * 10) / 10 }}%</div>
          <div class="jd-job-card__actions">
            <el-button
              size="small"
              :type="isFavorite(c.sourceTable, c.job.jobUrl) ? 'warning' : 'default'"
              plain
              @click.stop="toggleFavorite(c)"
              :loading="favoriteBusyKey === favoriteKeyOfCard(c)"
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
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, provide, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from './api.js'
import { Odometer, Briefcase, User, Setting, SwitchButton, ArrowDown, Star, StarFilled, Clock, TopRight } from '@element-plus/icons-vue'

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

onMounted(async () => {
  formatTime()
  setInterval(formatTime, 1000)
  if (!isLoginPage.value) {
    await refreshFavorites(true)
  }
})

const favoritesVisible = ref(false)
const favoritesLoading = ref(false)
const favoritesRaw = ref([])
const favoriteBusyKey = ref('')
const favoriteMap = ref({})

const historyVisible = ref(false)
const historyTab = ref('view')
const jobHistoryLoading = ref(false)
const jobHistoryRaw = ref([])
const matchHistoryLoading = ref(false)
const matchHistory = ref([])
const matchHistoryDetailVisible = ref(false)
const matchHistoryDetailLoading = ref(false)
const matchHistoryDetailCards = ref([])

const favoriteKeyOf = (sourceTable, jobUrl) => `${String(sourceTable || '').trim()}::${String(jobUrl || '').trim()}`
const favoriteKeyOfCard = (card) => favoriteKeyOf(card?.sourceTable, card?.job?.jobUrl)

const safeJsonParse = (v) => {
  if (!v) return null
  try {
    return JSON.parse(v)
  } catch {
    return null
  }
}

const normalizeJobUrl = (url) => {
  if (!url) return '#'
  const t = String(url).trim()
  if (!t) return '#'
  if (t.startsWith('http://') || t.startsWith('https://')) return t
  if (t.startsWith('//')) return `https:${t}`
  if (t.startsWith('/job_detail/')) return `https://www.zhipin.com${t}`
  if (t.startsWith('/')) return `https://jobs.51job.com${t}`
  return `https://${t}`
}

const openJobUrl = (url) => {
  const u = normalizeJobUrl(url)
  if (!u || u === '#') return
  window.open(u, '_blank', 'noopener,noreferrer')
}

const formatTimeDisplay = (v) => {
  if (!v) return '—'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return String(v)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const sourceLabel = (sourceTable) => {
  if (sourceTable === 'job_info_51job') return '51job'
  if (sourceTable === 'job_info') return 'Boss'
  return '未知'
}

const sourceTagType = (sourceTable) => {
  if (sourceTable === 'job_info_51job') return 'warning'
  if (sourceTable === 'job_info') return 'success'
  return 'info'
}

const isFavorite = (sourceTable, jobUrl) => {
  const key = favoriteKeyOf(sourceTable, jobUrl)
  if (!key || key === '::') return false
  return !!favoriteMap.value[key]
}

const refreshFavorites = async (silent) => {
  favoritesLoading.value = true
  try {
    const res = await api.listFavorites()
    if (res.data.code !== 200) throw new Error(res.data.message || '加载收藏失败')
    const list = res.data.data || []
    favoritesRaw.value = list
    const m = {}
    for (const f of list) {
      m[favoriteKeyOf(f.sourceTable, f.jobUrl)] = true
    }
    favoriteMap.value = m
  } catch (e) {
    if (!silent) ElMessage.error(String(e?.message || '加载收藏失败'))
  } finally {
    favoritesLoading.value = false
  }
}

const favoriteList = computed(() => {
  const out = []
  for (const f of favoritesRaw.value || []) {
    let job = safeJsonParse(f.jobJson)
    if (!job) {
      job = {
        id: f.jobId,
        jobName: f.jobName,
        companyName: f.companyName,
        city: f.city,
        jobUrl: f.jobUrl,
        salaryMin: f.salaryMin,
        salaryMax: f.salaryMax,
        experience: f.experience,
        education: f.education
      }
    }
    out.push({
      key: `${f.sourceTable || ''}::${f.jobUrl || ''}::fav::${f.id || ''}`,
      sourceTable: f.sourceTable,
      job
    })
  }
  return out
})

const refreshJobHistory = async (silent) => {
  jobHistoryLoading.value = true
  try {
    const res = await api.listJobHistory({ size: 60 })
    if (res.data.code !== 200) throw new Error(res.data.message || '加载历史失败')
    jobHistoryRaw.value = res.data.data || []
  } catch (e) {
    if (!silent) ElMessage.error(String(e?.message || '加载历史失败'))
  } finally {
    jobHistoryLoading.value = false
  }
}

const jobHistoryList = computed(() => {
  const out = []
  for (const h of jobHistoryRaw.value || []) {
    let job = safeJsonParse(h.jobJson)
    if (!job) {
      job = {
        jobName: h.jobName,
        companyName: h.companyName,
        city: h.city,
        jobUrl: h.jobUrl,
        salaryMin: h.salaryMin,
        salaryMax: h.salaryMax,
        experience: h.experience,
        education: h.education
      }
    }
    out.push({
      key: `${h.sourceTable || ''}::${h.jobUrl || ''}::his::${h.id || ''}`,
      sourceTable: h.sourceTable,
      job,
      updatedAt: h.updatedAt || h.createdAt
    })
  }
  return out
})

const refreshMatchHistory = async (silent) => {
  matchHistoryLoading.value = true
  try {
    const res = await api.listMatchHistory({ size: 50 })
    if (res.data.code !== 200) throw new Error(res.data.message || '加载匹配历史失败')
    matchHistory.value = res.data.data || []
  } catch (e) {
    if (!silent) ElMessage.error(String(e?.message || '加载匹配历史失败'))
  } finally {
    matchHistoryLoading.value = false
  }
}

const buildCandidateCards = (list, limit = 12) => {
  const top = (list || []).slice(0, limit)
  const out = []
  for (let i = 0; i < top.length; i++) {
    const m = top[i]
    const job = m?.job || {}
    out.push({
      key: `${m?.sourceTable || ''}::${job?.jobUrl || job?.id || ''}::hist::${i}`,
      job,
      sourceTable: m?.sourceTable || '',
      matchScore: m?.matchScore
    })
  }
  return out
}

const openMatchHistoryDetail = async (id) => {
  matchHistoryDetailVisible.value = true
  matchHistoryDetailLoading.value = true
  matchHistoryDetailCards.value = []
  try {
    const res = await api.getMatchHistoryDetail(id)
    if (res.data.code !== 200) throw new Error(res.data.message || '加载详情失败')
    const result = res.data.data?.result
    const list = Array.isArray(result) ? result : []
    matchHistoryDetailCards.value = buildCandidateCards(list, 12)
  } catch (e) {
    ElMessage.error(String(e?.message || '加载详情失败'))
  } finally {
    matchHistoryDetailLoading.value = false
  }
}

const toggleFavorite = async (cardLike) => {
  const sourceTable = String(cardLike?.sourceTable || '').trim()
  const jobUrl = String(cardLike?.job?.jobUrl || '').trim()
  if (!sourceTable || !jobUrl) return
  const key = favoriteKeyOf(sourceTable, jobUrl)
  favoriteBusyKey.value = key
  try {
    if (isFavorite(sourceTable, jobUrl)) {
      const res = await api.removeFavorite({ sourceTable, jobUrl })
      if (res.data.code !== 200) throw new Error(res.data.message || '取消收藏失败')
      const m = { ...favoriteMap.value }
      delete m[key]
      favoriteMap.value = m
      favoritesRaw.value = (favoritesRaw.value || []).filter(x => favoriteKeyOf(x.sourceTable, x.jobUrl) !== key)
      ElMessage.success('已取消收藏')
    } else {
      const res = await api.addFavorite({ sourceTable, job: cardLike.job })
      if (res.data.code !== 200) throw new Error(res.data.message || '收藏失败')
      await refreshFavorites(true)
      ElMessage.success('已收藏')
    }
  } catch (e) {
    ElMessage.error(String(e?.message || '操作失败'))
  } finally {
    favoriteBusyKey.value = ''
  }
}

const recordJobHistory = async (sourceTable, job) => {
  const st = String(sourceTable || '').trim()
  const jobUrl = String(job?.jobUrl || '').trim()
  if (!st || !jobUrl) return
  try {
    await api.recordJobHistory({ sourceTable: st, job })
  } catch { }
}

const openFavorites = async () => {
  favoritesVisible.value = true
  await refreshFavorites(true)
}

const openHistory = async () => {
  historyVisible.value = true
  historyTab.value = 'view'
  await Promise.all([refreshJobHistory(true), refreshMatchHistory(true)])
}

watch(isLoginPage, async (v) => {
  if (!v) {
    await refreshFavorites(true)
  }
})

provide('userDataStore', {
  isFavorite,
  toggleFavorite,
  favoriteBusyKey,
  openFavorites,
  openHistory,
  recordJobHistory,
  refreshFavorites
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
  border: 1px solid rgba(15, 23, 42, 0.10);
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
