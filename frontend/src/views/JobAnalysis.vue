<template>
  <div class="job-analysis" id="job-analysis-content">
    <div class="page-header">
      <div class="header-left">
        <h1>💼 岗位分析</h1>
        <p>详细展示所有招聘岗位信息，支持筛选、搜索和导出</p>
      </div>
      <el-button type="primary" @click="handleExportPDF" :loading="exportingPdf">
        <el-icon><Document /></el-icon> 导出PDF
      </el-button>
    </div>

    <el-card class="filter-card" shadow="hover">
      <el-form :inline="true" :model="filters" class="filter-form">
        <el-form-item label="岗位关键词">
          <el-input v-model="filters.keyword" placeholder="请输入岗位或公司关键词" clearable />
        </el-form-item>
        <el-form-item label="城市">
          <el-select v-model="filters.selectedCities" multiple placeholder="请选择城市" collapse-tags collapse-tags-tooltip style="width: 250px">
            <el-option v-for="city in cityOptions" :key="city" :label="city" :value="city" />
          </el-select>
        </el-form-item>
        <el-form-item label="学历">
          <el-select v-model="filters.education" placeholder="请选择学历" clearable style="width: 150px">
            <el-option label="不限" value="" />
            <el-option v-for="edu in educationOptions" :key="edu" :label="edu" :value="edu" />
          </el-select>
        </el-form-item>
        <el-form-item label="经验">
          <el-select v-model="filters.experience" placeholder="请选择经验" clearable style="width: 150px">
            <el-option label="不限" value="" />
            <el-option v-for="exp in experienceOptions" :key="exp" :label="exp" :value="exp" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch" :loading="loading">
            <el-icon><Search /></el-icon> 查询
          </el-button>
          <el-button @click="handleReset">
            <el-icon><RefreshLeft /></el-icon> 重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span>📋 岗位列表 (共 {{ total }} 条)</span>
          <div class="table-tools">
            <el-input v-model="tableSearch" placeholder="搜索岗位/公司" prefix-icon="Search" style="width: 250px" clearable />
            <el-button type="primary" @click="handleExport" :loading="exporting" style="margin-left: 10px">
              <el-icon><Download /></el-icon> 导出 CSV
            </el-button>
          </div>
        </div>
      </template>
      
      <el-table 
        v-loading="loading"
        :data="filteredJobs" 
        style="width: 100%" 
        :default-sort="{ prop: 'salaryAvg', order: 'descending' }"
        stripe
        border
      >
        <el-table-column prop="jobName" label="岗位名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="companyName" label="公司名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="city" label="城市" width="100" />
        <el-table-column 
          prop="salaryAvg" 
          label="薪资" 
          width="150" 
          sortable
          :sort-method="(a, b) => a.salaryAvg - b.salaryAvg"
        >
          <template #default="{ row }">
            <span class="salary-tag">{{ row.salaryMin }}K - {{ row.salaryMax }}K</span>
          </template>
        </el-table-column>
        <el-table-column prop="education" label="学历" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.education" size="small" type="info">{{ row.education }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="experience" label="经验" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.experience" size="small" type="success">{{ row.experience }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="companyIndustry" label="行业" min-width="150" show-overflow-tooltip />
        <el-table-column prop="companySize" label="公司规模" width="120" />
      </el-table>
      
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Search, RefreshLeft, Download, Document } from '@element-plus/icons-vue'
import api from '../api.js'
import { ElMessage } from 'element-plus'
import { exportToPDFMultiPage } from '../utils/exportPdf.js'

const route = useRoute()

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

const cityOptions = ref([])
const educationOptions = ref(['大专', '本科', '硕士', '博士'])
const experienceOptions = ref(['应届生', '1-3年', '3-5年', '5-10年', '10年以上'])

const jobs = ref([])
const allJobs = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

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
    const res = await api.getJobPage(currentPage.value, pageSize.value, getApiFilters())
    if (res.data.code === 200) {
      jobs.value = res.data.data.records || []
      total.value = res.data.data.total || 0
    }
  } catch (e) {
    console.error('加载岗位列表失败', e)
    ElMessage.error('加载岗位列表失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const loadCityOptions = async () => {
  try {
    const res = await api.getOverview()
    if (res.data.code === 200 && res.data.data.citySalary) {
      cityOptions.value = [...new Set(res.data.data.citySalary.map(d => d.city))].sort()
    }
  } catch (e) {
    console.error('加载城市选项失败', e)
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
  tableSearch.value = ''
  currentPage.value = 1
  loadJobs()
}

const handleSizeChange = (val) => {
  pageSize.value = val
  loadJobs()
}

const handleCurrentChange = (val) => {
  currentPage.value = val
  loadJobs()
}

const handleExport = async () => {
  exporting.value = true
  try {
    const res = await api.getJobPage(1, 10000, getApiFilters())
    if (res.data.code === 200) {
      const dataToExport = res.data.data.records || []
      exportToCSV(dataToExport)
      ElMessage.success('导出成功！')
    }
  } catch (e) {
    console.error('导出失败', e)
    ElMessage.error('导出失败，请稍后重试')
  } finally {
    exporting.value = false
  }
}

const exportToCSV = (data) => {
  const headers = ['岗位名称', '公司名称', '城市', '最低薪资(K)', '最高薪资(K)', '学历', '经验', '行业', '公司规模']
  const rows = data.map(job => [
    job.jobName || '',
    job.companyName || '',
    job.city || '',
    job.salaryMin || '',
    job.salaryMax || '',
    job.education || '',
    job.experience || '',
    job.companyIndustry || '',
    job.companySize || ''
  ])

  let csvContent = '\uFEFF'
  csvContent += headers.join(',') + '\n'
  rows.forEach(row => {
    const escapedRow = row.map(field => {
      const str = String(field || '')
      if (str.includes(',') || str.includes('"') || str.includes('\n')) {
        return '"' + str.replace(/"/g, '""') + '"'
      }
      return str
    })
    csvContent += escapedRow.join(',') + '\n'
  })

  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  const url = URL.createObjectURL(blob)
  link.setAttribute('href', url)
  link.setAttribute('download', `招聘数据_${new Date().toISOString().slice(0,10)}.csv`)
  link.style.visibility = 'hidden'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

const handleExportPDF = async () => {
  exportingPdf.value = true
  try {
    const filename = `岗位分析报告_${new Date().toISOString().slice(0,10)}.pdf`
    await exportToPDFMultiPage('job-analysis-content', filename)
    ElMessage.success('PDF导出成功！')
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error('PDF导出失败，请重试')
  } finally {
    exportingPdf.value = false
  }
}

onMounted(() => {
  if (route.query.city) {
    filters.value.selectedCities = [route.query.city]
  }
  loadCityOptions()
  loadJobs()
})
</script>

<style scoped>
.job-analysis {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 20px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.header-left h1 {
  margin: 0 0 8px 0;
  font-size: 28px;
  color: #333;
}

.header-left p {
  margin: 0;
  color: #666;
  font-size: 14px;
}

.filter-card {
  margin-bottom: 20px;
  border-radius: 8px;
}

.filter-form {
  margin-bottom: 0;
}

.table-card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.table-tools {
  display: flex;
  gap: 10px;
}

.salary-tag {
  font-weight: bold;
  color: #f5576c;
}

:deep(.el-pagination) {
  display: flex;
}
</style>
