<template>
  <div class="u-stack" id="job-analysis-content">
    <div class="u-row u-row-start">
      <div class="jd-page-head">
        <div class="jd-page-head__title jd-page-head__title-text u-title">💼 岗位分析</div>
        <div class="jd-page-head__desc">多维筛选、实时搜索，深度洞察 {{ currentSourceText }} 招聘市场</div>
      </div>
      <div class="u-inline header-actions">
        <el-button type="primary" plain @click="handleExportPDF" :loading="exportingPdf">
          <el-icon><Document /></el-icon> 导出PDF
        </el-button>
        <el-button type="primary" @click="handleExport" :loading="exporting">
          <el-icon><Download /></el-icon> 导出CSV
        </el-button>
      </div>
    </div>

    <div class="main-grid">
      <el-card class="filter-card" shadow="never">
        <el-form :model="filters" label-position="top">
            <el-form-item label="岗位关键词">
              <el-input v-model="filters.keyword" placeholder="Java, Python, 前端..." prefix-icon="Search" clearable />
            </el-form-item>
            <el-form-item label="目标城市">
              <el-select v-model="filters.selectedCities" multiple placeholder="选择城市" collapse-tags collapse-tags-tooltip>
                <el-option v-for="city in cityOptions" :key="city" :label="city" :value="city" />
              </el-select>
            </el-form-item>
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="学历要求">
                  <el-select v-model="filters.education" placeholder="不限" clearable>
                    <el-option v-for="edu in educationOptions" :key="edu" :label="edu" :value="edu" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="经验要求">
                  <el-select v-model="filters.experience" placeholder="不限" clearable>
                    <el-option v-for="exp in experienceOptions" :key="exp" :label="exp" :value="exp" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <div class="filter-actions">
              <el-button type="primary" class="search-btn" @click="handleSearch" :loading="loading">立即查询</el-button>
              <el-button class="reset-btn" @click="handleReset">重置</el-button>
            </div>
          </el-form>
      </el-card>

      <el-card class="table-card" shadow="never">
          <div class="table-header">
            <div class="table-title">
              <span>📋 岗位列表</span>
              <el-tag size="small" effect="plain" round>{{ total }} 条结果</el-tag>
            </div>
            <el-input v-model="tableSearch" placeholder="在当前结果中搜索公司或岗位..." style="width: 280px" prefix-icon="Search" clearable />
          </div>
          
          <el-table 
            v-loading="loading"
            :data="filteredJobs" 
            style="width: 100%" 
            :default-sort="{ prop: 'salaryAvg', order: 'descending' }"
            class="custom-table"
          >
            <el-table-column label="职位信息" min-width="240">
              <template #default="{ row }">
                <div class="job-info-cell">
                  <div class="job-name">{{ row.jobName }}</div>
                  <div class="company-name">{{ row.companyName }}</div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="city" label="城市" width="100">
               <template #default="{ row }">
                <div class="city-tag"><el-icon><Location /></el-icon> {{ row.city }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="salaryAvg" label="薪资标准" width="160" sortable>
              <template #default="{ row }">
                <span class="salary-range">{{ row.salaryMin }}K - {{ row.salaryMax }}K</span>
              </template>
            </el-table-column>
            <el-table-column label="基本要求" width="180">
              <template #default="{ row }">
                <div class="tags-cell">
                  <el-tag size="small" type="info" v-if="row.education">{{ row.education }}</el-tag>
                  <el-tag size="small" type="success" v-if="row.experience">{{ row.experience }}</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button
                  :type="isFavoriteRow(row) ? 'warning' : 'default'"
                  link
                  @click="toggleFavoriteRow(row)"
                  :loading="favoriteBusyKey === favoriteKeyOfRow(row)"
                >
                  <el-icon><StarFilled v-if="isFavoriteRow(row)" /><Star v-else /></el-icon>
                  {{ isFavoriteRow(row) ? '已收藏' : '收藏' }}
                </el-button>
                <el-button type="primary" link @click="openDesc(row)">查看详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          
          <div class="pagination-container">
            <el-pagination
              v-model:current-page="currentPage"
              v-model:page-size="pageSize"
              :page-sizes="[10, 20, 50]"
              :total="total"
              layout="total, sizes, prev, pager, next"
              @size-change="handleSizeChange"
              @current-change="handleCurrentChange"
            />
          </div>
      </el-card>
    </div>

    <el-dialog v-model="descDialogVisible" :title="descDialogTitle" width="720px" custom-class="job-detail-dialog">
      <div class="dialog-inner">
        <div class="dialog-section">
          <h3>职位描述</h3>
          <p class="desc-text">{{ descDialogContent }}</p>
        </div>
        <div class="dialog-footer-actions">
          <el-button type="primary" @click="copyUrl(descDialogUrl)">复制招聘链接</el-button>
          <el-link :href="normalizeJobUrl(descDialogUrl)" target="_blank" type="primary" class="external-link">
            在原平台查看 <el-icon><TopRight /></el-icon>
          </el-link>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, inject } from 'vue'
import { useRoute } from 'vue-router'
import { Search, RefreshLeft, Download, Document, Location, TopRight, Star, StarFilled } from '@element-plus/icons-vue'
import api from '../api.js'
import { ElMessage } from 'element-plus'
import { exportToPDFMultiPage } from '../utils/exportPdf.js'

const route = useRoute()
const currentSource = computed(() => route.meta?.source || 'boss')
const currentSourceText = computed(() => currentSource.value === '51job' ? '前程无忧' : 'BOSS直聘')
const sourceTable = computed(() => currentSource.value === '51job' ? 'job_info_51job' : 'job_info')

const userDataStore = inject('userDataStore', null)
const favoriteBusyKey = userDataStore?.favoriteBusyKey || ref('')
const favoriteKeyOfRow = (row) => `${String(sourceTable.value || '').trim()}::${String(row?.jobUrl || '').trim()}`
const isFavoriteRow = (row) => userDataStore ? userDataStore.isFavorite(sourceTable.value, row?.jobUrl) : false
const toggleFavoriteRow = (row) => {
  if (!userDataStore) return
  userDataStore.toggleFavorite({ sourceTable: sourceTable.value, job: row })
}

const loading = ref(false)
const exporting = ref(false)
const exportingPdf = ref(false)
const tableSearch = ref('')

const filters = ref({
  keyword: '',
  selectedCities: [],
  education: '',
  experience: ''
})

const cityOptions = ref(['北京', '上海', '广州', '深圳', '杭州', '成都', '武汉', '西安', '南京', '苏州'])
const educationOptions = ref(['大专', '本科', '硕士', '博士'])
const experienceOptions = ref(['应届生', '1-3年', '3-5年', '5-10年', '10年以上'])

const jobs = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const descDialogVisible = ref(false)
const descDialogTitle = ref('')
const descDialogContent = ref('')
const descDialogUrl = ref('')

const filteredJobs = computed(() => {
  if (!tableSearch.value) return jobs.value
  const search = tableSearch.value.toLowerCase()
  return jobs.value.filter(job => 
    job.jobName?.toLowerCase().includes(search) || 
    job.companyName?.toLowerCase().includes(search)
  )
})

const getApiFilters = () => {
  return {
    keyword: filters.value.keyword || undefined,
    city: filters.value.selectedCities.length > 0 ? filters.value.selectedCities.join(',') : undefined,
    education: filters.value.education || undefined,
    experience: filters.value.experience || undefined
  }
}

const loadJobs = async () => {
  loading.value = true
  try {
    const res = currentSource.value === '51job'
      ? await api.getJobPage51(currentPage.value, pageSize.value, getApiFilters())
      : await api.getJobPage(currentPage.value, pageSize.value, getApiFilters())
    if (res.data.code === 200) {
      jobs.value = res.data.data.records || []
      total.value = res.data.data.total || 0
    }
  } catch (e) {
    ElMessage.error('加载岗位列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  loadJobs()
}

const handleReset = () => {
  filters.value = {
    keyword: '',
    selectedCities: [],
    education: '',
    experience: ''
  }
  handleSearch()
}

const handleSizeChange = (val) => {
  pageSize.value = val
  loadJobs()
}

const handleCurrentChange = (val) => {
  currentPage.value = val
  loadJobs()
}

const openDesc = (row) => {
  if (userDataStore && row) {
    userDataStore.recordJobHistory(sourceTable.value, row)
  }
  descDialogTitle.value = row.jobName
  descDialogContent.value = row.jobDesc || '暂无详细描述'
  descDialogUrl.value = row.jobUrl
  descDialogVisible.value = true
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

const copyUrl = (url) => {
  if (!url) return
  navigator.clipboard.writeText(normalizeJobUrl(url))
  ElMessage.success('链接已复制到剪贴板')
}

const handleExport = async () => {
  ElMessage.info('导出功能准备中...')
}

const handleExportPDF = async () => {
  exportingPdf.value = true
  try {
    await exportToPDFMultiPage('job-analysis-content', '岗位分析报告.pdf')
    ElMessage.success('PDF导出成功')
  } catch (e) {
    ElMessage.error('导出失败')
  } finally {
    exportingPdf.value = false
  }
}

watch(() => route.path, () => {
  if (route.path.includes('/boss') || route.path.includes('/51job')) {
    loadJobs()
  }
})

onMounted(() => {
  loadJobs()
})
</script>

<style scoped>
.header-actions {
  display: flex;
  gap: 12px;
}

.main-grid {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 24px;
  align-items: start;
}

.filter-card {
  position: sticky;
  top: 98px;
}

.filter-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 20px;
}

.search-btn {
  width: 100%;
  height: 42px;
}

.reset-btn {
  width: 100%;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.table-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 18px;
  font-weight: 700;
}

.job-info-cell .job-name {
  font-weight: 700;
  color: var(--c-ink);
  margin-bottom: 4px;
}

.job-info-cell .company-name {
  font-size: 13px;
  color: var(--c-ink-3);
}

.salary-range {
  font-weight: 800;
  color: var(--c-primary-700);
}

.city-tag {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--c-ink-3);
}

.tags-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.pagination-container {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
}

.dialog-inner {
  padding: 0 10px;
}

.desc-text {
  line-height: 1.8;
  color: var(--c-ink-2);
  white-space: pre-wrap;
  margin-top: 12px;
}

.dialog-footer-actions {
  margin-top: 32px;
  display: flex;
  align-items: center;
  gap: 20px;
}

.external-link {
  font-weight: 600;
}

@media (max-width: 960px) {
  .main-grid { grid-template-columns: 1fr; }
  .filter-card { position: relative; top: 0; }
}
</style>
