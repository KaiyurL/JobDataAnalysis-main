<template>
  <div class="salary-predict">
    <div class="page-header">
      <h1>💰 薪资预测</h1>
      <p>根据您的条件，预测您的期望薪资范围</p>
    </div>

    <el-row :gutter="20">
      <el-col :xs="24" :lg="10">
        <el-card class="form-card" shadow="hover">
          <template #header>
            <span>📝 输入条件</span>
          </template>
          
          <el-form :model="form" label-width="80px" label-position="top">
            <el-form-item label="城市">
              <el-select v-model="form.city" placeholder="请选择城市" clearable style="width: 100%">
                <el-option v-for="city in cityOptions" :key="city" :label="city" :value="city" />
              </el-select>
            </el-form-item>
            
            <el-form-item label="学历">
              <el-select v-model="form.education" placeholder="请选择学历" clearable style="width: 100%">
                <el-option v-for="edu in educationOptions" :key="edu" :label="edu" :value="edu" />
              </el-select>
            </el-form-item>
            
            <el-form-item label="经验">
              <el-select v-model="form.experience" placeholder="请选择经验" clearable style="width: 100%">
                <el-option v-for="exp in experienceOptions" :key="exp" :label="exp" :value="exp" />
              </el-select>
            </el-form-item>
            
            <el-form-item label="岗位关键词">
              <el-input v-model="form.keyword" placeholder="例如：Java、前端、产品" clearable />
            </el-form-item>
            
            <el-form-item>
              <el-button type="primary" @click="handlePredict" :loading="loading" style="width: 100%">
                <el-icon><MagicStick /></el-icon> 开始预测
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="14">
        <el-card v-if="result" class="result-card" shadow="hover">
          <template #header>
            <span>📊 预测结果</span>
          </template>
          
          <div class="salary-range">
            <div class="range-item">
              <span class="label">预测最低薪资</span>
              <span class="value min">{{ result.salaryMinPredicted }} K</span>
            </div>
            <div class="range-divider">~</div>
            <div class="range-item">
              <span class="label">预测最高薪资</span>
              <span class="value max">{{ result.salaryMaxPredicted }} K</span>
            </div>
          </div>
          
          <el-progress 
            :percentage="90" 
            :color="['#67C23A', '#E6A23C', '#F56C6C']"
            :stroke-width="20"
            class="confidence-bar"
          />
          <p class="confidence-text">基于 {{ result.similarJobs?.length || 0 }} 个相似岗位的统计分析</p>
          
          <el-divider content-position="left">💼 相似岗位推荐</el-divider>
          
          <el-table 
            :data="result.similarJobs || []" 
            style="width: 100%"
            stripe
          >
            <el-table-column prop="jobName" label="岗位名称" show-overflow-tooltip />
            <el-table-column prop="companyName" label="公司名称" show-overflow-tooltip />
            <el-table-column prop="city" label="城市" width="100" />
            <el-table-column label="薪资" width="140">
              <template #default="{ row }">
                <span class="salary-tag">{{ row.salaryMin }} - {{ row.salaryMax }} K</span>
              </template>
            </el-table-column>
            <el-table-column prop="experience" label="经验" width="100" />
          </el-table>
        </el-card>
        
        <el-empty v-else description="请输入条件并点击预测" style="margin-top: 80px" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { MagicStick } from '@element-plus/icons-vue'
import api from '../api.js'
import { ElMessage } from 'element-plus'

const form = ref({
  city: '',
  education: '',
  experience: '',
  keyword: ''
})

const loading = ref(false)
const result = ref(null)
const cityOptions = ref([])
const educationOptions = ref(['大专', '本科', '硕士', '博士'])
const experienceOptions = ref(['应届生', '1-3年', '3-5年', '5-10年', '10年以上'])

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

const handlePredict = async () => {
  if (!form.value.city && !form.value.education && !form.value.experience && !form.value.keyword) {
    ElMessage.warning('请至少填写一个条件')
    return
  }

  loading.value = true
  try {
    const res = await api.predictSalary(form.value)
    if (res.data.code === 200) {
      result.value = res.data.data
      if (!result.value.similarJobs || result.value.similarJobs.length === 0) {
        ElMessage.warning('没有找到匹配的岗位，预测结果仅供参考')
      }
    }
  } catch (e) {
    console.error('预测失败', e)
    ElMessage.error('预测失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadCityOptions()
})
</script>

<style scoped>
.salary-predict {
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

.form-card,
.result-card {
  border-radius: 8px;
}

.salary-range {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 30px 0;
  gap: 30px;
}

.range-item {
  text-align: center;
}

.range-item .label {
  display: block;
  font-size: 14px;
  color: #999;
  margin-bottom: 10px;
}

.range-item .value {
  font-size: 36px;
  font-weight: bold;
}

.range-item .value.min {
  color: #67C23A;
}

.range-item .value.max {
  color: #F56C6C;
}

.range-divider {
  font-size: 32px;
  color: #999;
  font-weight: bold;
}

.confidence-bar {
  margin: 20px 0;
}

.confidence-text {
  text-align: center;
  color: #999;
  font-size: 14px;
  margin: 0 0 10px 0;
}

.salary-tag {
  color: #F56C6C;
  font-weight: bold;
}
</style>
