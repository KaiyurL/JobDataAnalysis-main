<template>
  <div class="job-match">
    <div class="page-header">
      <h1>🤖 智能求职助手</h1>
      <p>基于阿里云百炼大模型，为你生成定制化求职建议（支持上传简历智能解析，并自动推荐匹配岗位）</p>
    </div>

    <el-row :gutter="16">
      <!-- 左侧：求职画像 -->
      <el-col :xs="24" :sm="24" :md="7" :lg="6">
        <el-card class="profile-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>🧾 求职画像</span>
              <div style="display: flex; gap: 8px;">
                <el-upload
                  action="#"
                  :auto-upload="false"
                  :show-file-list="false"
                  :on-change="handleResumeUpload"
                  accept=".pdf,.doc,.docx,.txt"
                >
                  <el-button type="primary" size="small" :loading="parsingResume" title="上传简历解析">
                    <el-icon><Upload /></el-icon>
                  </el-button>
                </el-upload>
                <el-button size="small" @click="resetProfile" title="重置"><el-icon><RefreshLeft /></el-icon></el-button>
              </div>
            </div>
          </template>

          <el-form label-position="top" :model="profile">
            <el-form-item label="目标岗位">
              <el-input v-model="profile.targetRole" placeholder="例如：Java后端 / 前端" clearable />
            </el-form-item>
            <el-form-item label="城市">
              <el-input v-model="profile.city" placeholder="例如：北京/远程" clearable />
            </el-form-item>
            <el-form-item label="学历">
              <el-select v-model="profile.education" placeholder="选择学历" clearable style="width: 100%">
                <el-option label="大专" value="大专" />
                <el-option label="本科" value="本科" />
                <el-option label="硕士" value="硕士" />
                <el-option label="博士" value="博士" />
              </el-select>
            </el-form-item>
            <el-form-item label="经验">
              <el-select v-model="profile.experience" placeholder="选择经验" clearable style="width: 100%">
                <el-option label="应届生" value="应届生" />
                <el-option label="1-3年" value="1-3年" />
                <el-option label="3-5年" value="3-5年" />
                <el-option label="5-10年" value="5-10年" />
                <el-option label="10年以上" value="10年以上" />
              </el-select>
            </el-form-item>
            <el-form-item label="技能（用逗号分隔）">
              <el-input
                v-model="profile.skills"
                type="textarea"
                :rows="2"
                placeholder="例如：Java, Spring Boot, MySQL"
              />
            </el-form-item>
            <el-form-item label="补充说明">
              <el-input
                v-model="profile.notes"
                type="textarea"
                :rows="2"
                placeholder="希望找实习；面试薄弱等"
              />
            </el-form-item>
          </el-form>
          
          <div style="margin-top: 10px;">
            <el-button type="success" plain size="small" style="width: 100%;" @click="fetchMatchedJobs" :loading="matchingJobs">
              <el-icon style="margin-right: 4px;"><Search /></el-icon> 匹配岗位
            </el-button>
          </div>

          <div class="quick-prompts">
            <el-button size="small" @click="usePrompt('帮我做一份30天学习和投递计划')">行动计划</el-button>
            <el-button size="small" @click="usePrompt('帮我优化简历要点，给出可以直接写到简历上的 bullet')">简历优化</el-button>
            <el-button size="small" @click="usePrompt('列出我这种背景去面这个岗位，最容易被问到的5个面试题')">模拟面试</el-button>
          </div>
        </el-card>
      </el-col>

      <!-- 中间：对话 -->
      <el-col :xs="24" :sm="24" :md="10" :lg="11">
        <el-card class="chat-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>💬 AI 求职辅导</span>
              <div class="chat-actions">
                <el-button size="small" @click="clearChat" :disabled="sending || messages.length <= 1">清空对话</el-button>
              </div>
            </div>
          </template>

          <div ref="chatBodyRef" class="chat-body">
            <div v-for="m in messages" :key="m.id" class="chat-line" :class="m.role">
              <div class="chat-bubble">
                <div class="chat-role">{{ m.role === 'user' ? '我' : 'AI 顾问' }}</div>
                <div class="chat-content" v-html="formatContent(m.content)"></div>
              </div>
            </div>
          </div>

          <div class="chat-input">
            <el-input
              v-model="input"
              type="textarea"
              :rows="3"
              placeholder="输入你的问题，例如：我现在是应届生，目标 Java 后端，帮我规划投递与面试准备"
              :disabled="sending"
              @keydown.enter.exact.prevent="handleSend"
            />
            <div class="chat-buttons">
              <el-button type="primary" @click="handleSend" :loading="sending" :disabled="!input.trim()">
                发送
              </el-button>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：匹配岗位推荐 -->
      <el-col :xs="24" :sm="24" :md="7" :lg="7">
        <el-card class="match-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>🎯 推荐岗位</span>
              <div style="display: flex; gap: 8px; align-items: center;">
                <el-button size="small" @click="refreshMatches" :disabled="matchingJobs || !profile.targetRole" title="刷一刷">
                  <el-icon><Refresh /></el-icon>
                </el-button>
                <el-tag size="small" type="success" v-if="matchPool.length">{{ shownJobs.length }} 个</el-tag>
              </div>
            </div>
          </template>

          <div class="match-list" v-loading="matchingJobs">
            <div v-if="!matchPool.length && !matchingJobs" class="empty-match">
              <el-empty description="完善左侧画像后，点击「匹配岗位」获取精准推荐" :image-size="80" />
            </div>
            
            <div v-for="(match, idx) in shownJobs" :key="idx" class="match-item">
              <div class="match-header">
                <span class="match-title">{{ match.job.jobName }}</span>
                <span class="match-salary">{{ match.job.salaryMin }}K - {{ match.job.salaryMax }}K</span>
              </div>
              <div class="match-company">{{ match.job.companyName }}</div>
              <div class="match-tags">
                <el-tag size="small" type="info">{{ match.job.city }}</el-tag>
                <el-tag size="small" type="info">{{ match.job.experience }}</el-tag>
                <el-tag size="small" type="info">{{ match.job.education }}</el-tag>
                <el-tag size="small" :type="sourceTagType(match.sourceTable)" effect="plain">{{ sourceLabel(match.sourceTable) }}</el-tag>
              </div>
              <div class="match-score-row">
                <el-progress :percentage="match.matchScore" :stroke-width="8" :show-text="false" :color="scoreColor(match.matchScore)" />
                <span class="score-text">{{ match.matchScore }}% 匹配</span>
              </div>
              <div class="match-reason">{{ match.matchReason }}</div>
              <div class="match-actions">
                <el-button type="primary" size="small" @click="openJobDetail(match)">
                  详情 <el-icon><View /></el-icon>
                </el-button>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="jobDetailVisible" width="860px" :show-close="true" top="8vh">
      <template #header>
        <div class="job-detail-title">
          <div class="job-detail-name">{{ selectedMatch?.job?.jobName || '岗位详情' }}</div>
          <div class="job-detail-sub">
            {{ selectedMatch?.job?.companyName || '' }}
            <span v-if="selectedMatch?.job?.city"> · {{ selectedMatch.job.city }}</span>
          </div>
        </div>
      </template>

      <div class="job-detail-body" v-if="selectedMatch?.job">
        <div class="job-detail-meta">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="薪资">
              {{ selectedMatch.job.salaryMin }}K - {{ selectedMatch.job.salaryMax }}K
            </el-descriptions-item>
            <el-descriptions-item label="经验 / 学历">
              {{ selectedMatch.job.experience || '不限' }} / {{ selectedMatch.job.education || '不限' }}
            </el-descriptions-item>
            <el-descriptions-item label="行业">
              {{ selectedMatch.job.companyIndustry || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="公司规模">
              {{ selectedMatch.job.companySize || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="发布时间">
              {{ selectedMatch.job.publishDate || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="来源">
              {{ sourceLabel(selectedMatch.sourceTable) }}
            </el-descriptions-item>
            <el-descriptions-item label="匹配度">
              <div class="job-detail-score">
                <el-progress :percentage="selectedMatch.matchScore" :stroke-width="8" :show-text="false" :color="scoreColor(selectedMatch.matchScore)" />
                <span class="score-text">{{ selectedMatch.matchScore }}% 匹配</span>
              </div>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="job-detail-section" v-if="selectedMatch.job.jobKeywords">
          <div class="job-detail-section-title">技能关键词</div>
          <div class="job-detail-tags">
            <el-tag
              v-for="kw in splitKeywords(selectedMatch.job.jobKeywords)"
              :key="kw"
              size="small"
              type="info"
              effect="plain"
            >
              {{ kw }}
            </el-tag>
          </div>
        </div>

        <div class="job-detail-section" v-if="selectedMatch.job.companyWelfare">
          <div class="job-detail-section-title">福利待遇</div>
          <div class="job-detail-text">{{ selectedMatch.job.companyWelfare }}</div>
        </div>

        <div class="job-detail-section" v-if="selectedMatch.job.jobDesc">
          <div class="job-detail-section-title">岗位描述</div>
          <div class="job-detail-desc">{{ selectedMatch.job.jobDesc }}</div>
        </div>

        <div class="job-detail-section">
          <div class="job-detail-section-title">招聘链接</div>
          <div class="job-detail-url-row">
            <el-input :model-value="normalizeJobUrl(selectedMatch.job.jobUrl)" readonly />
            <el-button @click="copyJobUrl(selectedMatch.job.jobUrl)" :disabled="!normalizeJobUrl(selectedMatch.job.jobUrl) || normalizeJobUrl(selectedMatch.job.jobUrl) === '#'" title="复制链接">
              <el-icon><CopyDocument /></el-icon>
            </el-button>
            <el-button type="primary" plain @click="openJobUrl(selectedMatch.job.jobUrl)" :disabled="!normalizeJobUrl(selectedMatch.job.jobUrl) || normalizeJobUrl(selectedMatch.job.jobUrl) === '#'" title="新窗口打开">
              <el-icon><TopRight /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import api from '../api.js'
import { ElMessage } from 'element-plus'
import { Upload, RefreshLeft, Refresh, Search, TopRight, View, CopyDocument } from '@element-plus/icons-vue'

const profile = ref({
  targetRole: '',
  city: '',
  education: '',
  experience: '',
  skills: '',
  notes: ''
})

const messages = ref([
  {
    id: `m_${Date.now()}`,
    role: 'assistant',
    content: '你好！我是你的 AI 求职顾问。你可以手动填写左侧的求职画像，或者点击“上传简历”自动提取。确认画像后，可以点击“匹配岗位”看推荐，或者在下方问我任何面试、简历相关的问题。'
  }
])

const input = ref('')
const sending = ref(false)
const parsingResume = ref(false)
const matchingJobs = ref(false)
const chatBodyRef = ref(null)
const matchPool = ref([])
const shownJobs = ref([])
const jobDetailVisible = ref(false)
const selectedMatch = ref(null)

const scrollToBottom = async () => {
  await nextTick()
  const el = chatBodyRef.value
  if (!el) return
  el.scrollTop = el.scrollHeight
}

const formatContent = (text) => {
  if (!text) return ''
  return text.replace(/\n/g, '<br/>').replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
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

const scoreColor = (score) => {
  if (score >= 80) return '#10b981'
  if (score >= 60) return '#3b82f6'
  return '#f59e0b'
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

const openJobDetail = (match) => {
  selectedMatch.value = match || null
  jobDetailVisible.value = true
}

const splitKeywords = (s) => {
  if (!s) return []
  const raw = String(s).split(/[,，\s]+/).map(x => x.trim()).filter(Boolean)
  const seen = new Set()
  const out = []
  for (const kw of raw) {
    if (!seen.has(kw)) {
      seen.add(kw)
      out.push(kw)
    }
  }
  return out.slice(0, 40)
}

const copyJobUrl = async (url) => {
  const u = normalizeJobUrl(url)
  if (!u || u === '#') return
  try {
    await navigator.clipboard.writeText(u)
    ElMessage.success('已复制招聘链接')
  } catch (e) {
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
  const u = normalizeJobUrl(url)
  if (!u || u === '#') return
  window.open(u, '_blank', 'noopener,noreferrer')
}

const getJobKey = (job) => {
  if (!job) return ''
  if (job.jobUrl) return String(job.jobUrl)
  if (job.id != null) return `id:${job.id}`
  return `${job.jobName || ''}::${job.companyName || ''}::${job.city || ''}`
}

const shuffleArray = (arr) => {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    const t = arr[i]
    arr[i] = arr[j]
    arr[j] = t
  }
  return arr
}

const pickShownJobs = () => {
  const pool = matchPool.value || []
  const n = Math.min(5, pool.length)
  if (n === 0) {
    shownJobs.value = []
    return
  }

  const prevKeys = new Set((shownJobs.value || []).map(m => getJobKey(m.job)))
  const indices = shuffleArray([...Array(pool.length).keys()])

  const picked = []
  const used = new Set()
  for (const idx of indices) {
    const m = pool[idx]
    const key = getJobKey(m?.job)
    if (!key || used.has(key)) continue
    used.add(key)
    picked.push(m)
    if (picked.length >= n) break
  }

  if (pool.length > n && picked.length === n) {
    const sameSet = picked.every(m => prevKeys.has(getJobKey(m.job))) && prevKeys.size === n
    if (sameSet) {
      const indices2 = shuffleArray([...Array(pool.length).keys()])
      const picked2 = []
      const used2 = new Set()
      for (const idx of indices2) {
        const m = pool[idx]
        const key = getJobKey(m?.job)
        if (!key || used2.has(key)) continue
        used2.add(key)
        picked2.push(m)
        if (picked2.length >= n) break
      }
      shownJobs.value = picked2
      return
    }
  }

  shownJobs.value = picked
}

const refreshMatches = async () => {
  if (matchingJobs.value) return
  if (!profile.value.targetRole) {
    ElMessage.warning('请先填写目标岗位')
    return
  }
  if (matchPool.value.length > 5) {
    pickShownJobs()
    return
  }
  await fetchMatchedJobs()
}

const handleResumeUpload = async (file) => {
  if (!file || !file.raw) return
  
  const isValidType = ['application/pdf', 'application/msword', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'text/plain'].includes(file.raw.type)
  const isExtensionValid = /\.(pdf|doc|docx|txt)$/i.test(file.name)
  
  if (!isValidType && !isExtensionValid) {
    ElMessage.error('仅支持 PDF, DOC, DOCX, TXT 格式的文件')
    return
  }
  if (file.raw.size > 10 * 1024 * 1024) {
    ElMessage.error('简历文件大小不能超过 10MB')
    return
  }

  parsingResume.value = true
  ElMessage.info('正在解析简历并提取信息，请稍候...')
  
  try {
    const res = await api.parseResume(file.raw)
    if (res.data.code !== 200) {
      throw new Error(res.data.message || '解析失败')
    }
    const data = res.data.data
    profile.value = {
      targetRole: data.targetRole || profile.value.targetRole,
      city: data.city || profile.value.city,
      education: data.education || profile.value.education,
      experience: data.experience || profile.value.experience,
      skills: data.skills || profile.value.skills,
      notes: data.notes || profile.value.notes
    }
    matchPool.value = []
    shownJobs.value = []
    ElMessage.success('简历解析成功，已自动填充求职画像')
    
    // 自动追加一条提示消息
    messages.value.push({
      id: `a_${Date.now()}`,
      role: 'assistant',
      content: '我已经提取了你简历中的关键信息并填入左侧画像，你可以检查或修改它。建议你现在点击“匹配岗位”看看市场上有哪些合适的机会，或者让我帮你优化一下简历。'
    })
    await scrollToBottom()
  } catch (e) {
    console.error(e)
    ElMessage.error('简历解析异常: ' + (e.message || '网络或服务端错误'))
  } finally {
    parsingResume.value = false
  }
}

const fetchMatchedJobs = async () => {
  if (!profile.value.targetRole) {
    ElMessage.warning('请先填写目标岗位')
    return
  }
  matchingJobs.value = true
  try {
    const res = await api.matchJobs({
      targetRole: profile.value.targetRole,
      city: profile.value.city,
      education: profile.value.education,
      experience: profile.value.experience,
      skills: profile.value.skills
    })
    if (res.data.code === 200) {
      matchPool.value = res.data.data || []
      pickShownJobs()
      if (matchPool.value.length === 0) {
        ElMessage.info('没有找到特别匹配的岗位，建议放宽城市或岗位词限制')
      } else {
        ElMessage.success(`为您准备了 ${matchPool.value.length} 个岗位，可刷一刷换一批`)
      }
    } else {
      throw new Error(res.data.message)
    }
  } catch (e) {
    ElMessage.error('获取匹配岗位失败')
  } finally {
    matchingJobs.value = false
  }
}

const resetProfile = () => {
  profile.value = {
    targetRole: '',
    city: '',
    education: '',
    experience: '',
    skills: '',
    notes: ''
  }
  matchPool.value = []
  shownJobs.value = []
}

const clearChat = () => {
  messages.value = [
    {
      id: `m_${Date.now()}`,
      role: 'assistant',
      content: '已清空对话。你可以重新描述你的目标与当前情况，我会继续给出建议。'
    }
  ]
  input.value = ''
  scrollToBottom()
}

const usePrompt = (text) => {
  input.value = text
}

const buildHistoryPayload = () => {
  const maxHistory = 12
  const tail = messages.value.slice(Math.max(0, messages.value.length - maxHistory))
  return tail
    .filter(m => m.role === 'user' || m.role === 'assistant')
    .map(m => ({ role: m.role, content: m.content }))
}

const handleSend = async () => {
  const content = input.value.trim()
  if (!content) return

  const userMsg = { id: `u_${Date.now()}`, role: 'user', content }
  messages.value.push(userMsg)
  input.value = ''
  await scrollToBottom()

  sending.value = true
  try {
    const res = await api.careerChat({
      profile: { ...profile.value },
      message: content,
      history: buildHistoryPayload()
    })
    if (res.data.code !== 200) {
      throw new Error(res.data.message || '请求失败')
    }
    const reply = res.data.data?.reply || ''
    messages.value.push({ id: `a_${Date.now()}`, role: 'assistant', content: reply || '未返回内容' })
    await scrollToBottom()
  } catch (e) {
    console.error(e)
    const backendMessage = e?.response?.data?.message
    const errorMessage = backendMessage || e?.message || 'AI 生成失败，请检查后端和百炼配置'
    ElMessage.error(String(errorMessage))
    messages.value.push({ id: `a_${Date.now()}`, role: 'assistant', content: '当前无法生成建议，请稍后重试。' })
    await scrollToBottom()
  } finally {
    sending.value = false
  }
}
</script>

<style scoped>
.job-match {
  max-width: 1600px;
  margin: 0 auto;
  padding: 20px;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h1 {
  margin: 0 0 8px 0;
  font-size: 28px;
  color: #1e293b;
  font-weight: 800;
}

.page-header p {
  margin: 0;
  color: #64748b;
  font-size: 15px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  color: #1e293b;
}

.profile-card,
.chat-card,
.match-card {
  border-radius: 12px;
  border: none;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03);
  height: calc(100vh - 150px);
  display: flex;
  flex-direction: column;
}

:deep(.el-card__body) {
  flex: 1;
  overflow-y: auto;
}

:deep(.chat-card .el-card__body) {
  padding: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.quick-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f1f5f9;
}

.chat-actions {
  display: flex;
  gap: 8px;
}

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background-color: #f8fafc;
}

.chat-line {
  display: flex;
  margin-bottom: 20px;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.chat-line.user {
  justify-content: flex-end;
}

.chat-line.assistant {
  justify-content: flex-start;
}

.chat-bubble {
  max-width: 85%;
  border-radius: 16px;
  padding: 14px 18px;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
}

.chat-line.assistant .chat-bubble {
  background: #ffffff;
  border-top-left-radius: 4px;
}

.chat-line.user .chat-bubble {
  background: #4f46e5;
  color: #ffffff;
  border-top-right-radius: 4px;
}

.chat-role {
  font-size: 12px;
  margin-bottom: 6px;
  opacity: 0.8;
  font-weight: 600;
}

.chat-line.user .chat-role {
  color: #e0e7ff;
  text-align: right;
}

.chat-line.assistant .chat-role {
  color: #64748b;
}

.chat-content {
  word-break: break-word;
  line-height: 1.6;
  font-size: 14px;
}

.chat-line.user .chat-content {
  color: #ffffff;
}

.chat-line.assistant .chat-content {
  color: #334155;
}

.chat-input {
  padding: 16px 20px;
  background: #ffffff;
  border-top: 1px solid #e2e8f0;
}

.chat-buttons {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

/* 推荐岗位样式 */
.match-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.empty-match {
  padding-top: 40px;
}

.match-item {
  padding: 16px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  transition: all 0.2s;
}

.match-item:hover {
  border-color: #cbd5e1;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}

.match-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.match-title {
  font-weight: 700;
  font-size: 15px;
  color: #1e293b;
}

.match-salary {
  font-weight: 800;
  color: #4f46e5;
  font-size: 14px;
}

.match-company {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 10px;
}

.match-tags {
  display: flex;
  gap: 6px;
  margin-bottom: 12px;
}

.match-score-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.score-text {
  font-size: 12px;
  font-weight: 600;
  color: #475569;
}

.match-reason {
  font-size: 12px;
  color: #64748b;
  background: #ffffff;
  padding: 8px;
  border-radius: 6px;
  margin-bottom: 10px;
}

.match-actions {
  display: flex;
  justify-content: flex-end;
}

.job-detail-title {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.job-detail-name {
  font-weight: 800;
  font-size: 18px;
  color: #0f172a;
}

.job-detail-sub {
  font-size: 13px;
  color: #64748b;
}

.job-detail-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.job-detail-score {
  display: flex;
  align-items: center;
  gap: 8px;
}

.job-detail-section-title {
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 8px;
}

.job-detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.job-detail-text {
  font-size: 13px;
  color: #334155;
  line-height: 1.6;
  white-space: pre-wrap;
}

.job-detail-desc {
  font-size: 13px;
  color: #334155;
  line-height: 1.6;
  white-space: pre-wrap;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px;
  max-height: 300px;
  overflow: auto;
}

.job-detail-url-row {
  display: flex;
  gap: 10px;
  align-items: center;
}
</style>
