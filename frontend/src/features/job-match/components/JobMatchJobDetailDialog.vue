<template>
  <el-dialog class="job-detail-dialog" :model-value="visible" width="860px" :show-close="true" top="8vh" @update:model-value="(v) => emit('update:visible', v)">
    <template #header>
      <div class="job-detail-title">
        <div class="job-detail-name">{{ selectedJob?.job?.jobName || '岗位详情' }}</div>
        <div class="job-detail-sub">
          {{ selectedJob?.job?.companyName || '' }}
          <span v-if="selectedJob?.job?.city"> · {{ selectedJob.job.city }}</span>
          <span v-if="selectedJob?.sourceTable"> · {{ sourceLabel(selectedJob.sourceTable) }}</span>
        </div>
      </div>
    </template>

    <div v-if="selectedJob?.job" class="job-detail-body">
      <div class="job-detail-meta">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="薪资">
            <span v-if="selectedJob.job.salaryMin != null && selectedJob.job.salaryMax != null">
              {{ selectedJob.job.salaryMin }}K - {{ selectedJob.job.salaryMax }}K
            </span>
            <span v-else>面议</span>
          </el-descriptions-item>
          <el-descriptions-item label="经验 / 学历">
            {{ selectedJob.job.experience || '不限' }} / {{ selectedJob.job.education || '不限' }}
          </el-descriptions-item>
          <el-descriptions-item label="行业">
            {{ selectedJob.job.companyIndustry || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="公司规模">
            {{ selectedJob.job.companySize || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="发布时间">
            {{ selectedJob.job.publishDate || '—' }}
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <div v-if="selectedJob.job.jobKeywords" class="job-detail-section">
        <div class="job-detail-section-title">技能关键词</div>
        <div class="job-detail-tags">
          <el-tag v-for="kw in splitKeywords(selectedJob.job.jobKeywords)" :key="kw" size="small" type="info" effect="plain">
            {{ kw }}
          </el-tag>
        </div>
      </div>

      <div v-if="selectedJob.job.companyWelfare" class="job-detail-section">
        <div class="job-detail-section-title">福利待遇</div>
        <div class="job-detail-text">{{ selectedJob.job.companyWelfare }}</div>
      </div>

      <div v-if="selectedJob.job.jobDesc" class="job-detail-section">
        <div class="job-detail-section-title">岗位描述</div>
        <div class="job-detail-desc">{{ selectedJob.job.jobDesc }}</div>
      </div>

      <div v-if="selectedJob.aiReason" class="job-detail-section job-detail-reason">
        <div class="job-detail-section-title">推荐理由</div>
        <div class="job-detail-reason-text">{{ selectedJob.aiReason }}</div>
      </div>

      <div class="job-detail-section">
        <div class="job-detail-section-title">招聘链接</div>
        <div class="job-detail-url-row">
          <el-input class="job-detail-url-input" :model-value="normalizeJobUrl(selectedJob.job.jobUrl)" readonly />
          <el-button circle :disabled="!canOpenUrl(selectedJob.job.jobUrl)" title="复制链接" @click="copyJobUrl(selectedJob.job.jobUrl)">
            <el-icon><CopyDocument /></el-icon>
          </el-button>
          <el-button
            circle
            type="primary"
            plain
            :disabled="!canOpenUrl(selectedJob.job.jobUrl)"
            title="新窗口打开"
            @click="openJobUrl(selectedJob.job.jobUrl)"
          >
            <el-icon><TopRight /></el-icon>
          </el-button>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { CopyDocument, TopRight } from '@element-plus/icons-vue'

const props = defineProps({
  visible: {
    type: Boolean,
    required: true
  },
  selectedJob: {
    type: Object,
    required: false,
    default: null
  },
  sourceLabel: {
    type: Function,
    required: true
  },
  splitKeywords: {
    type: Function,
    required: true
  },
  normalizeJobUrl: {
    type: Function,
    required: true
  },
  canOpenUrl: {
    type: Function,
    required: true
  }
})

const emit = defineEmits(['update:visible'])

const copyJobUrl = async (url) => {
  const u = props.normalizeJobUrl(url)
  if (!u || u === '#') return
  try {
    await navigator.clipboard.writeText(u)
    ElMessage.success('已复制招聘链接')
  } catch {
    const ta = document.createElement('textarea')
    ta.value = u
    ta.style.position = 'fixed'
    ta.style.left = '-9999px'
    document.body.appendChild(ta)
    ta.select()
    try {
      document.execCommand('copy')
      ElMessage.success('已复制招聘链接')
    } finally {
      document.body.removeChild(ta)
    }
  }
}

const openJobUrl = (url) => {
  const u = props.normalizeJobUrl(url)
  if (!u || u === '#') return
  window.open(u, '_blank', 'noopener,noreferrer')
}
</script>

<style>
.job-detail-dialog .el-dialog__body {
  padding-top: 10px;
}

.job-detail-url-input {
  flex: 1;
}

.job-detail-reason {
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.09), rgba(16, 185, 129, 0.08));
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 12px;
  padding: 12px;
}

.job-detail-reason-text {
  font-size: 13px;
  line-height: 1.7;
  color: #0f172a;
  white-space: pre-wrap;
}
</style>
