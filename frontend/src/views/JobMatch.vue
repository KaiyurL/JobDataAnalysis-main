
<template>
  <div class="job-match">
    <div class="page-header">
      <h1>🎯 岗位匹配</h1>
      <p>选择您的技能，找到最适合您的岗位</p>
    </div>

    <el-card class="skills-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span>🏷️ 选择您的技能</span>
          <el-button type="primary" @click="handleMatch" :loading="loading" :disabled="selectedSkills.length === 0">
            <el-icon><Odometer /></el-icon> 开始匹配
          </el-button>
        </div>
      </template>
      
      <div class="search-area">
        <el-input 
          v-model="searchQuery" 
          placeholder="搜索技能..." 
          clearable
          prefix-icon="Search"
        />
      </div>
      
      <div class="skills-grid">
        <el-checkbox-group v-model="selectedSkills">
          <el-checkbox 
            v-for="skill in filteredSkills" 
            :key="skill" 
            :label="skill"
            class="skill-checkbox"
          />
        </el-checkbox-group>
      </div>
      
      <div class="selected-info" v-if="selectedSkills.length > 0">
        已选择 <el-tag type="primary">{{ selectedSkills.length }}</el-tag> 个技能
        <el-button size="small" type="danger" @click="clearSelection" style="margin-left: 10px">
          清空选择
        </el-button>
      </div>
    </el-card>

    <el-card v-if="matchResults.length > 0" class="result-card" shadow="hover">
      <template #header>
        <span>📋 匹配结果（前 {{ matchResults.length }} 个岗位）</span>
      </template>
      
      <el-table 
        :data="matchResults" 
        style="width: 100%"
        stripe
      >
        <el-table-column prop="jobName" label="岗位名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="companyName" label="公司名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="city" label="城市" width="100" />
        <el-table-column label="薪资" width="140">
          <template #default="{ row }">
            <span class="salary-tag">{{ row.salaryMin }} - {{ row.salaryMax }} K</span>
          </template>
        </el-table-column>
        <el-table-column prop="matchScore" label="匹配度" width="160">
          <template #default="{ row }">
            <el-progress 
              :percentage="Math.round(row.matchScore * 100)" 
              :color="getMatchColor(row.matchScore)"
              :stroke-width="12"
            />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-empty v-else-if="hasMatched" description="没有找到匹配的岗位" style="margin-top: 40px" />
    <el-empty v-else description="选择技能并点击开始匹配" style="margin-top: 40px" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Odometer } from '@element-plus/icons-vue'
import api from '../api.js'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const allSkills = ref([])
const selectedSkills = ref([])
const matchResults = ref([])
const hasMatched = ref(false)
const searchQuery = ref('')

const filteredSkills = computed(() => {
  if (!searchQuery.value) {
    return allSkills.value
  }
  const q = searchQuery.value.toLowerCase()
  return allSkills.value.filter(skill => 
    skill.toLowerCase().includes(q)
  )
})

const loadSkills = async () => {
  try {
    const res = await api.getAllSkillsSorted()
    if (res.data.code === 200) {
      allSkills.value = res.data.data
    }
  } catch (e) {
    console.error('加载技能失败', e)
  }
}

const handleMatch = async () => {
  if (selectedSkills.value.length === 0) {
    ElMessage.warning('请至少选择一个技能')
    return
  }

  loading.value = true
  try {
    const res = await api.matchJobs({ skills: selectedSkills.value })
    if (res.data.code === 200) {
      matchResults.value = res.data.data
      hasMatched.value = true
      if (matchResults.value.length === 0) {
        ElMessage.warning('没有找到匹配的岗位')
      } else {
        ElMessage.success(`找到 ${matchResults.value.length} 个匹配岗位`)
      }
    }
  } catch (e) {
    console.error('匹配失败', e)
    ElMessage.error('匹配失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const clearSelection = () => {
  selectedSkills.value = []
}

const getMatchColor = (score) => {
  if (score >= 0.8) return '#67C23A'
  if (score >= 0.6) return '#E6A23C'
  return '#F56C6C'
}

onMounted(() => {
  loadSkills()
})
</script>

<style scoped>
.job-match {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h1 {
  margin: 0 0 8px 0;
  font-size: 28px;
  color: #333;
}

.page-header p {
  margin: 0;
  color: #666;
  font-size: 14px;
}

.skills-card,
.result-card {
  border-radius: 8px;
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-area {
  margin-bottom: 20px;
}

.skills-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 15px;
  padding: 10px 0;
  max-height: 400px;
  overflow-y: auto;
}

.skill-checkbox {
  margin-right: 0;
}

.selected-info {
  margin-top: 15px;
  padding-top: 15px;
  border-top: 1px solid #EBEEF5;
  color: #666;
}

.salary-tag {
  color: #F56C6C;
  font-weight: bold;
}
</style>
