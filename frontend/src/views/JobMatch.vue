<template>
  <div class="u-stack">
    <el-row :gutter="16">
      <el-col :xs="24" :sm="24" :md="24" :lg="24">
        <el-card class="chat-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <div class="chat-head-left">
                <span>💬 AI 求职辅导</span>
                <el-button class="profile-head-btn" size="small" @click="profileDrawerVisible = true">
                  🧾 求职画像
                  <span class="profile-head-meta">
                    {{ profile.targetRole || '未设置目标岗位' }}<span v-if="profile.city"> · {{ profile.city }}</span>
                  </span>
                </el-button>
              </div>
              <div class="chat-actions">
                <el-button size="small" @click="clearChat" :disabled="sending || messages.length <= 1">清空对话</el-button>
              </div>
            </div>
          </template>

          <div ref="chatBodyRef" class="chat-body">
            <div v-for="m in messages" :key="m.id" class="chat-line" :class="m.role">
              <div class="chat-bubble" :class="{ 'job-cards-bubble': m.kind === 'job_cards' }">
                <div class="chat-role">{{ m.role === 'user' ? '我' : 'AI 顾问' }}</div>
                <div v-if="m.kind === 'job_cards'" class="job-cards">
                  <div class="job-cards-header">{{ m.title || '推荐岗位（可点开查看详情）' }}</div>
                  <el-row :gutter="12">
                    <el-col v-for="c in m.cards" :key="c.key" :xs="24" :sm="12" :md="12" :lg="8">
                      <el-card class="job-card" shadow="hover" :body-style="{ padding: '14px' }" @click="openJobDetail(c)">
                        <div class="job-card-head">
                          <div class="job-card-title">{{ c.job.jobName }}</div>
                          <div class="job-card-salary-pill" v-if="c.job.salaryMin != null && c.job.salaryMax != null">
                            {{ c.job.salaryMin }}K-{{ c.job.salaryMax }}K
                          </div>
                          <div class="job-card-salary-pill muted" v-else>面议</div>
                        </div>
                        <div class="job-card-sub">{{ c.job.companyName }} · {{ c.job.city }}</div>
                        <div class="job-card-tags">
                          <el-tag size="small" type="info" effect="plain">{{ c.job.experience || '经验不限' }}</el-tag>
                          <el-tag size="small" type="info" effect="plain">{{ c.job.education || '学历不限' }}</el-tag>
                          <el-tag size="small" :type="sourceTagType(c.sourceTable)" effect="plain">{{ sourceLabel(c.sourceTable) }}</el-tag>
                        </div>
                        <div class="job-card-score" v-if="c.matchScore != null">
                          <el-progress :percentage="Number(c.matchScore) || 0" :stroke-width="8" :show-text="false" :color="scoreColor(Number(c.matchScore) || 0)" />
                          <div class="job-card-score-text">{{ Math.round((Number(c.matchScore) || 0) * 10) / 10 }}% 匹配</div>
                        </div>
                        <div class="job-card-reason" v-if="c.aiReason">{{ c.aiReason }}</div>
                        <div class="job-card-actions">
                          <el-button
                            size="small"
                            :type="isFavoriteCard(c) ? 'warning' : 'default'"
                            plain
                            @click.stop="toggleFavoriteCard(c)"
                            :loading="favoriteBusyKey === favoriteKeyOfCard(c)"
                          >
                            <el-icon><StarFilled v-if="isFavoriteCard(c)" /><Star v-else /></el-icon>
                            {{ isFavoriteCard(c) ? '已收藏' : '收藏' }}
                          </el-button>
                          <el-button type="primary" size="small" @click.stop="openJobDetail(c)">详情 <el-icon><View /></el-icon></el-button>
                        </div>
                      </el-card>
                    </el-col>
                  </el-row>
                </div>
                <div v-else-if="m.kind === 'citations'" class="citations">
                  <div class="citations-header">{{ m.title || '参考资料（RAG）' }}</div>
                  <div v-if="!m.citations || !m.citations.length" class="citations-empty">暂无</div>
                  <div v-else class="citations-list">
                    <div v-for="(c, i) in m.citations" :key="`c_${m.id}_${i}`" class="citation-item">
                      <div class="citation-title-row">
                        <div class="citation-title">
                          {{ (c && (c.title || c.job_name || c.jobName)) || `参考片段 ${i + 1}` }}
                        </div>
                        <a
                          v-if="citationUrlOf(c)"
                          class="citation-link"
                          :href="citationUrlOf(c)"
                          target="_blank"
                          rel="noopener noreferrer"
                        >
                          打开
                        </a>
                      </div>
                      <div class="citation-meta">
                        <span v-if="c && (c.source || c.source_table || c.sourceTable)">{{ c.source || c.source_table || c.sourceTable }}</span>
                        <span v-if="c && (c.job_id || c.jobId)"> · {{ c.job_id || c.jobId }}</span>
                      </div>
                      <div v-if="c && (c.snippet || c.text)" class="citation-snippet">
                        {{ c.snippet || c.text }}
                      </div>
                    </div>
                  </div>
                </div>
                <div v-else class="chat-content" v-html="formatContent(m.content)"></div>
              </div>
            </div>
          </div>

          <div class="composer-shell">
            <div class="composer">
              <div class="composer-top">
                <div class="composer-brand">          
                </div>
              </div>

              <div class="composer-mid">
                <el-input
                  v-model="input"
                  class="composer-input"
                  type="textarea"
                  :autosize="{ minRows: 1, maxRows: 4 }"
                  placeholder="输入你的问题，例如：我现在是应届生，目标 Java 后端，帮我规划投递与面试准备"
                  :disabled="sending"
                  @keydown.enter.exact.prevent="handleSend"
                />
                <div class="composer-inline">
                  <div class="chat-tools">
                    <el-button type="success" plain size="small" @click="handleMatchJobs" :loading="matchingJobs" :disabled="sending">
                      <el-icon><Search /></el-icon> 匹配岗位
                    </el-button>
                    <el-button size="small" @click="usePrompt('帮我优化简历要点，给出可以直接写到简历上的 bullet')">简历优化</el-button>
                    <el-button size="small" @click="usePrompt('列出我这种背景去面这个岗位，最容易被问到的5个面试题')">模拟面试</el-button>
                  </div>
                  <div class="composer-actions">
                    <el-button
                      class="composer-send-btn"
                      circle
                      type="primary"
                      @click="handleSend"
                      :loading="sending"
                      :disabled="!input.trim()"
                      title="发送"
                    >
                      <el-icon><ArrowUp /></el-icon>
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

    </el-row>

    <el-dialog
      v-model="profileDrawerVisible"
      class="profile-dialog"
      width="760px"
      :append-to-body="true"
      :destroy-on-close="false"
      align-center
    >
      <template #header>
        <div class="card-header">
          <span>🧾 求职画像</span>
          <div class="u-inline u-gap-2">
            <el-button type="success" size="small" :loading="savingProfile" @click="saveProfile(false)">保存</el-button>
            <el-upload
              action="#"
              :auto-upload="false"
              :show-file-list="false"
              :on-change="handleResumeUpload"
              accept=".pdf,.doc,.docx,.txt"
            >
              <el-button type="primary" size="small" :loading="parsingResume">
                <el-icon><Upload /></el-icon> 上传简历
              </el-button>
            </el-upload>
            <el-button size="small" @click="resetProfile" title="重置"><el-icon><RefreshLeft /></el-icon></el-button>
          </div>
        </div>
      </template>

      <div class="profile-dialog-body">
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
          <el-input v-model="profile.skills" type="textarea" :rows="2" placeholder="例如：Java, Spring Boot, MySQL" />
        </el-form-item>
        <el-form-item label="补充说明">
          <el-input v-model="profile.notes" type="textarea" :rows="2" placeholder="希望找实习；面试薄弱等" />
        </el-form-item>
      </el-form>

      <div v-if="resumeMeta" class="resume-meta-row">
        <el-tag size="small" type="success" effect="plain">已解析：{{ resumeMeta.fileType?.toUpperCase?.() || resumeMeta.fileType }}</el-tag>
        <el-tag v-if="resumeMeta.textLength != null" size="small" type="info" effect="plain">文本 {{ resumeMeta.textLength }} 字</el-tag>
      </div>

      <el-collapse v-if="resumeMeta && resumeMeta.rich" class="resume-collapse" accordion>
        <el-collapse-item title="简历内容预览（节选）" name="preview">
          <div class="resume-preview">{{ resumeMeta.textPreview }}</div>
        </el-collapse-item>
        <el-collapse-item v-if="profileExtra.highlights?.length" title="结构化亮点" name="highlights">
          <div class="resume-list">
            <div v-for="(x, i) in profileExtra.highlights" :key="i" class="resume-list-item">• {{ x }}</div>
          </div>
        </el-collapse-item>
        <el-collapse-item v-if="profileExtra.projects?.length" title="项目经历" name="projects">
          <div class="resume-cards">
            <el-card v-for="(p, i) in profileExtra.projects" :key="i" class="resume-mini-card" shadow="never">
              <div class="resume-mini-title">{{ p.name || '项目' }}<span v-if="p.role"> · {{ p.role }}</span></div>
              <div v-if="p.tech?.length" class="resume-mini-tags">
                <el-tag v-for="t in p.tech" :key="t" size="small" type="info" effect="plain">{{ t }}</el-tag>
              </div>
              <div v-if="p.highlights?.length" class="resume-mini-text">
                <div v-for="(h, j) in p.highlights" :key="j">• {{ h }}</div>
              </div>
            </el-card>
          </div>
        </el-collapse-item>
        <el-collapse-item v-if="profileExtra.workExperiences?.length" title="工作经历" name="work">
          <div class="resume-cards">
            <el-card v-for="(w, i) in profileExtra.workExperiences" :key="i" class="resume-mini-card" shadow="never">
              <div class="resume-mini-title">
                {{ w.company || '公司' }}<span v-if="w.title"> · {{ w.title }}</span>
              </div>
              <div v-if="w.start || w.end" class="resume-mini-sub">{{ w.start || '' }} - {{ w.end || '' }}</div>
              <div v-if="w.tech?.length" class="resume-mini-tags">
                <el-tag v-for="t in w.tech" :key="t" size="small" type="info" effect="plain">{{ t }}</el-tag>
              </div>
              <div v-if="w.highlights?.length" class="resume-mini-text">
                <div v-for="(h, j) in w.highlights" :key="j">• {{ h }}</div>
              </div>
            </el-card>
          </div>
        </el-collapse-item>
      </el-collapse>
      </div>
    </el-dialog>

    <el-dialog v-model="jobDetailVisible" width="860px" :show-close="true" top="8vh">
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

      <div class="job-detail-body" v-if="selectedJob?.job">
        <div class="job-detail-meta">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="薪资">
              <span v-if="selectedJob.job.salaryMin != null && selectedJob.job.salaryMax != null">{{ selectedJob.job.salaryMin }}K - {{ selectedJob.job.salaryMax }}K</span>
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
            <el-descriptions-item label="推荐理由">
              {{ selectedJob.aiReason || '—' }}
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="job-detail-section" v-if="selectedJob.job.jobKeywords">
          <div class="job-detail-section-title">技能关键词</div>
          <div class="job-detail-tags">
            <el-tag
              v-for="kw in splitKeywords(selectedJob.job.jobKeywords)"
              :key="kw"
              size="small"
              type="info"
              effect="plain"
            >
              {{ kw }}
            </el-tag>
          </div>
        </div>

        <div class="job-detail-section" v-if="selectedJob.job.companyWelfare">
          <div class="job-detail-section-title">福利待遇</div>
          <div class="job-detail-text">{{ selectedJob.job.companyWelfare }}</div>
        </div>

        <div class="job-detail-section" v-if="selectedJob.job.jobDesc">
          <div class="job-detail-section-title">岗位描述</div>
          <div class="job-detail-desc">{{ selectedJob.job.jobDesc }}</div>
        </div>

        <div class="job-detail-section">
          <div class="job-detail-section-title">招聘链接</div>
          <div class="job-detail-url-row">
            <el-input :model-value="normalizeJobUrl(selectedJob.job.jobUrl)" readonly />
            <el-button @click="copyJobUrl(selectedJob.job.jobUrl)" :disabled="!canOpenUrl(selectedJob.job.jobUrl)" title="复制链接">
              <el-icon><CopyDocument /></el-icon>
            </el-button>
            <el-button type="primary" plain @click="openJobUrl(selectedJob.job.jobUrl)" :disabled="!canOpenUrl(selectedJob.job.jobUrl)" title="新窗口打开">
              <el-icon><TopRight /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, watch, inject } from 'vue'
import api from '../api.js'
import { ElMessage } from 'element-plus'
import { Upload, RefreshLeft, Search, View, CopyDocument, TopRight, ArrowUp, Star, StarFilled } from '@element-plus/icons-vue'

const cssVar = (name, fallback) => {
  if (typeof window === 'undefined') return fallback
  try {
    const v = getComputedStyle(document.documentElement).getPropertyValue(name)
    const t = String(v || '').trim()
    return t || fallback
  } catch {
    return fallback
  }
}

const theme = () => ({
  success: cssVar('--c-success', '#16a34a'),
  info: cssVar('--c-info', '#2563eb'),
  warning: cssVar('--c-warning', '#f59e0b')
})

const profile = ref({
  targetRole: '',
  city: '',
  education: '',
  experience: '',
  skills: '',
  notes: ''
})
const resumeMeta = ref(null)
const profileExtra = ref({
  highlights: [],
  workExperiences: [],
  projects: [],
  certifications: [],
  links: []
})
const profileDrawerVisible = ref(false)
const savingProfile = ref(false)

const messages = ref([
  {
    id: `m_${Date.now()}`,
    role: 'assistant',
    kind: 'text',
    content: '你好！我是你的 AI 求职顾问。你可以点击上方的“求职画像”完善信息，或点击“上传简历”自动提取。确认画像后，点击下方“匹配岗位”即可获取推荐，也可以随时向我提问简历与面试相关的问题。'
  }
])

const input = ref('')
const sending = ref(false)
const parsingResume = ref(false)
const matchingJobs = ref(false)
const chatBodyRef = ref(null)
const jobDetailVisible = ref(false)
const selectedJob = ref(null)

const userDataStore = inject('userDataStore', null)
const favoriteBusyKey = userDataStore?.favoriteBusyKey || ref('')
const favoriteKeyOfCard = (card) => `${String(card?.sourceTable || '').trim()}::${String(card?.job?.jobUrl || '').trim()}`
const isFavoriteCard = (card) => userDataStore ? userDataStore.isFavorite(card?.sourceTable, card?.job?.jobUrl) : false
const toggleFavoriteCard = (card) => {
  if (!userDataStore) return
  userDataStore.toggleFavorite({ sourceTable: card?.sourceTable, job: card?.job })
}

const scrollToBottom = async () => {
  await nextTick()
  const el = chatBodyRef.value
  if (!el) return
  el.scrollTop = el.scrollHeight
}

const formatContent = (text) => {
  if (!text) return ''
  const raw = String(text)
  const withBold = raw.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
  const withLinks = withBold.replace(/(https?:\/\/[^\s<]+)/g, '<a href="$1" target="_blank" rel="noopener noreferrer">$1</a>')
  return withLinks.replace(/\n/g, '<br/>')
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

const citationUrlOf = (c) => {
  if (!c || typeof c !== 'object') return ''
  const u = c.job_url || c.jobUrl || c.url || c.link || ''
  const normalized = normalizeJobUrl(u)
  return normalized && normalized !== '#' ? normalized : ''
}

const canOpenUrl = (url) => {
  const u = normalizeJobUrl(url)
  return u && u !== '#'
}

const scoreColor = (score) => {
  const t = theme()
  if (score >= 80) return t.success
  if (score >= 60) return t.info
  return t.warning
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

const openJobDetail = (jobCard) => {
  selectedJob.value = jobCard || null
  if (userDataStore && jobCard?.job) {
    userDataStore.recordJobHistory(jobCard?.sourceTable, jobCard.job)
  }
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

const buildHistoryPayload = () => {
  const maxHistory = 12
  const tail = messages.value.slice(Math.max(0, messages.value.length - maxHistory))
  return tail
    .filter(m => (m.role === 'user' || m.role === 'assistant') && m.kind !== 'job_cards')
    .map(m => ({ role: m.role, content: m.content }))
}

const callAgentChatStream = async (userText, onDelta, onEnd) => {
  const token = localStorage.getItem('token') || sessionStorage.getItem('token') || ''
  const res = await fetch('/api/agent/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify({
      profile: { ...profile.value },
      message: userText,
      history: buildHistoryPayload()
    })
  })
  if (!res.ok) {
    throw new Error(`请求失败(${res.status})`)
  }
  if (!res.body) {
    throw new Error('当前浏览器不支持流式响应')
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  const flushBlock = (block) => {
    const lines = block.split('\n')
    const dataLines = []
    for (const line of lines) {
      if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trim())
      }
    }
    if (dataLines.length === 0) return
    const dataStr = dataLines.join('\n')
    let obj = null
    try {
      obj = JSON.parse(dataStr)
    } catch {
      return
    }
    if (!obj || typeof obj !== 'object') return
    if (obj.type === 'delta') {
      onDelta?.(String(obj.text || ''))
    } else if (obj.type === 'end') {
      onEnd?.(obj.payload || {})
    }
  }
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let idx
    while ((idx = buffer.indexOf('\n\n')) >= 0) {
      const block = buffer.slice(0, idx)
      buffer = buffer.slice(idx + 2)
      if (block.trim()) flushBlock(block)
    }
  }
  if (buffer.trim()) flushBlock(buffer)
}

const mapAgentJobCards = (cards) => {
  if (!Array.isArray(cards) || cards.length === 0) return []
  const out = []
  for (let i = 0; i < cards.length; i++) {
    const c = cards[i] || {}
    const source = String(c.source || '').toLowerCase()
    const sourceTable = source === '51job' ? 'job_info_51job' : 'job_info'
    const job = {
      id: c.id,
      jobName: c.jobName,
      companyName: c.companyName,
      city: c.city,
      jobUrl: c.jobUrl,
      salaryMin: c.salaryMin,
      salaryMax: c.salaryMax,
      salaryAvg: c.salaryAvg,
      experience: c.experience,
      education: c.education
    }
    out.push({
      index: i + 1,
      key: `${sourceTable}::${job.jobUrl || job.id || i}`,
      job,
      sourceTable
    })
  }
  return out
}

const pushUserText = async (text) => {
  const userMsg = { id: `u_${Date.now()}`, role: 'user', kind: 'text', content: text }
  messages.value.push(userMsg)
  await scrollToBottom()
}

const pushAssistantText = async (text) => {
  messages.value.push({ id: `a_${Date.now()}`, role: 'assistant', kind: 'text', content: text || '未返回内容' })
  await scrollToBottom()
}

const pushAssistantJobCards = async (title, cards) => {
  messages.value.push({
    id: `c_${Date.now()}`,
    role: 'assistant',
    kind: 'job_cards',
    title,
    cards: cards || []
  })
  await scrollToBottom()
}

const pushAssistantCitations = async (title, citations) => {
  if (!Array.isArray(citations) || citations.length === 0) return
  messages.value.push({
    id: `s_${Date.now()}`,
    role: 'assistant',
    kind: 'citations',
    title,
    citations: citations.slice(0, 8)
  })
  await scrollToBottom()
}

const buildCandidateLines = (list, limit = 8) => {
  const out = []
  const top = (list || []).slice(0, limit)
  for (let i = 0; i < top.length; i++) {
    const m = top[i]
    const j = m?.job || {}
    const src = m?.sourceTable === 'job_info_51job' ? '51job' : 'Boss'
    const salary = (j.salaryMin != null && j.salaryMax != null) ? `${j.salaryMin}-${j.salaryMax}K` : '薪资面议'
    const url = normalizeJobUrl(j.jobUrl)
    out.push(`${i + 1}. [${src}] ${j.jobName || ''} @ ${j.companyName || ''} | ${j.city || ''} | ${salary} | ${j.experience || '经验不限'} | ${j.education || '学历不限'} | ${url}`)
  }
  return out.join('\n')
}

const buildCandidateCards = (list, limit = 10) => {
  const top = (list || []).slice(0, limit)
  const out = []
  for (let i = 0; i < top.length; i++) {
    const m = top[i]
    const job = m?.job || {}
    out.push({
      index: i + 1,
      key: `${m?.sourceTable || ''}::${job?.jobUrl || job?.id || ''}::${i}`,
      job,
      sourceTable: m?.sourceTable || '',
      matchScore: m?.matchScore
    })
  }
  return out
}

const handleMatchJobs = async () => {
  if (!profile.value.targetRole) {
    ElMessage.warning('请先填写目标岗位')
    return
  }
  if (sending.value) return
  matchingJobs.value = true
  try {
    const res = await api.matchJobs({
      targetRole: profile.value.targetRole,
      city: profile.value.city,
      education: profile.value.education,
      experience: profile.value.experience,
      skills: profile.value.skills,
      notes: profile.value.notes,
      highlights: Array.isArray(profileExtra.value?.highlights) ? profileExtra.value.highlights : [],
      projects: Array.isArray(profileExtra.value?.projects)
        ? profileExtra.value.projects.slice(0, 2).map(p => ({
            name: p?.name || '',
            role: p?.role || '',
            tech: Array.isArray(p?.tech) ? p.tech : [],
            highlights: Array.isArray(p?.highlights) ? p.highlights : []
          }))
        : []
    })
    if (res.data.code !== 200) {
      throw new Error(res.data.message || '获取匹配岗位失败')
    }
    const list = res.data.data || []
    if (!list.length) {
      await pushAssistantText('没有找到特别匹配的岗位。建议放宽城市或把目标岗位写得更泛一些（例如“后端开发/Java”）。')
      return
    }

    const displayText = '匹配岗位：请从候选中推荐最适合的岗位并说明理由'
    await pushUserText(displayText)

    const candidatesText = buildCandidateLines(list, 8)
    const candidateCards = buildCandidateCards(list, 8)
    await pushAssistantJobCards('匹配岗位（数据库候选）', candidateCards)

    const payloadText =
      `我刚做了岗位匹配，请你从候选岗位中做精排推荐，并给出我该怎么投递与准备。\n\n` +
      `候选岗位（来自数据库匹配结果）：\n${candidatesText}\n\n` +
      `请你完成：\n` +
      `1) 从候选中挑选最适合我的 5 个岗位（按优先级排序）\n` +
      `2) 每个岗位给出匹配点、差距点、以及我下一步该补什么\n` +
      `3) 给出投递策略（简历怎么改/投递话术/面试准备重点）`

    sending.value = true
    try {
      const msgId = `a_${Date.now()}`
      const msg = { id: msgId, role: 'assistant', kind: 'text', content: '' }
      messages.value.push(msg)
      await scrollToBottom()

      let finalPayload = null
      await callAgentChatStream(
        payloadText,
        (delta) => {
          msg.content = (msg.content || '') + (delta || '')
          scrollToBottom()
        },
        (payload) => {
          finalPayload = payload || null
        }
      )
      if (!msg.content) {
        msg.content = '已完成岗位匹配推荐。'
      }
      const cards = mapAgentJobCards(finalPayload?.jobCards)
      if (cards.length > 0) {
        await pushAssistantJobCards('检索岗位（卡片可点开详情）', cards)
      }
      await pushAssistantCitations('参考资料（RAG）', finalPayload?.citations)
    } finally {
      sending.value = false
    }
  } catch (e) {
    console.error(e)
    const backendMessage = e?.response?.data?.message
    const isTimeout = e?.code === 'ECONNABORTED' || String(e?.message || '').includes('timeout')
    ElMessage.error('获取匹配岗位失败: ' + (backendMessage || (isTimeout ? '请求超时，请稍后再试' : (e?.message || '网络或服务端错误'))))
  } finally {
    matchingJobs.value = false
  }
}

const handleResumeUpload = async (file) => {
  if (!file || !file.raw) return
  
  const isValidType = ['application/pdf', 'application/msword', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'text/plain'].includes(file.raw.type)
  const isExtensionValid = /\.(pdf|doc|docx|txt)$/i.test(file.name)
  
  if (!isValidType && !isExtensionValid) {
    ElMessage.error('仅支持 PDF, DOC, DOCX, TXT 格式的文件')
    return
  }
  if (file.raw.size > 20 * 1024 * 1024) {
    ElMessage.error('简历文件大小不能超过 20MB')
    return
  }

  parsingResume.value = true
  ElMessage.info('正在解析简历并提取信息，请稍候...')
  
  try {
    const res = await api.parseResume(file.raw)
    if (res.data.code !== 200) {
      throw new Error(res.data.message || '解析失败')
    }
    const data = res.data.data || {}
    resumeMeta.value = data._resume || null
    profileExtra.value = {
      highlights: Array.isArray(data.highlights) ? data.highlights : [],
      workExperiences: Array.isArray(data.workExperiences) ? data.workExperiences : [],
      projects: Array.isArray(data.projects) ? data.projects : [],
      certifications: Array.isArray(data.certifications) ? data.certifications : [],
      links: Array.isArray(data.links) ? data.links : []
    }
    profile.value = {
      targetRole: data.targetRole || profile.value.targetRole,
      city: data.city || profile.value.city,
      education: data.education || profile.value.education,
      experience: data.experience || profile.value.experience,
      skills: data.skills || profile.value.skills,
      notes: data.notes || profile.value.notes
    }
    ElMessage.success('简历解析成功，已自动填充求职画像')
    await saveProfile(true)
    
    // 自动追加一条提示消息
    await pushAssistantText('我已经提取了你简历中的关键信息并填入左侧画像，你可以检查或修改它。建议你现在点击“匹配岗位”让我按你的画像推荐岗位。')
  } catch (e) {
    console.error(e)
    const backendMessage = e?.response?.data?.message
    ElMessage.error('简历解析异常: ' + (backendMessage || e.message || '网络或服务端错误'))
  } finally {
    parsingResume.value = false
  }
}

const loadProfile = async (silent) => {
  try {
    const res = await api.getUserProfile()
    if (res.data.code !== 200) throw new Error(res.data.message || '加载画像失败')
    const data = res.data.data || {}
    const p = data.profile || {}
    profile.value = {
      targetRole: p.targetRole || '',
      city: p.city || '',
      education: p.education || '',
      experience: p.experience || '',
      skills: p.skills || '',
      notes: p.notes || ''
    }
    resumeMeta.value = data.resumeMeta || null
    const extra = data.profileExtra || {}
    profileExtra.value = {
      highlights: Array.isArray(extra.highlights) ? extra.highlights : [],
      workExperiences: Array.isArray(extra.workExperiences) ? extra.workExperiences : [],
      projects: Array.isArray(extra.projects) ? extra.projects : [],
      certifications: Array.isArray(extra.certifications) ? extra.certifications : [],
      links: Array.isArray(extra.links) ? extra.links : []
    }
  } catch (e) {
    if (!silent) ElMessage.error(String(e?.message || '加载画像失败'))
  }
}

const saveProfile = async (silent) => {
  savingProfile.value = true
  try {
    const res = await api.saveUserProfile({
      profile: { ...profile.value },
      resumeMeta: resumeMeta.value,
      profileExtra: profileExtra.value
    })
    if (res.data.code !== 200) throw new Error(res.data.message || '保存失败')
    if (!silent) ElMessage.success('已保存')
  } catch (e) {
    if (!silent) ElMessage.error(String(e?.message || '保存失败'))
  } finally {
    savingProfile.value = false
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
  resumeMeta.value = null
  profileExtra.value = { highlights: [], workExperiences: [], projects: [], certifications: [], links: [] }
}

const clearChat = () => {
  messages.value = [
    {
      id: `m_${Date.now()}`,
      role: 'assistant',
      kind: 'text',
      content: '已清空对话。你可以重新描述你的目标与当前情况，我会继续给出建议。'
    }
  ]
  input.value = ''
  scrollToBottom()
}

const usePrompt = (text) => {
  input.value = text
}

const handleSend = async () => {
  const content = input.value.trim()
  if (!content) return

  await pushUserText(content)
  input.value = ''

  sending.value = true
  try {
    const msgId = `a_${Date.now()}`
    const msg = { id: msgId, role: 'assistant', kind: 'text', content: '' }
    messages.value.push(msg)
    await scrollToBottom()

    let finalPayload = null
    await callAgentChatStream(
      content,
      (delta) => {
        msg.content = (msg.content || '') + (delta || '')
        scrollToBottom()
      },
      (payload) => {
        finalPayload = payload || null
      }
    )
    if (!msg.content) {
      msg.content = '未返回内容'
    }
    const cards = mapAgentJobCards(finalPayload?.jobCards)
    if (cards.length > 0) {
      await pushAssistantJobCards('检索岗位（卡片可点开详情）', cards)
    }
    await pushAssistantCitations('参考资料（RAG）', finalPayload?.citations)
  } catch (e) {
    console.error(e)
    const backendMessage = e?.response?.data?.message
    const errorMessage = backendMessage || e?.message || 'AI 生成失败，请检查后端和百炼配置'
    ElMessage.error(String(errorMessage))
    await pushAssistantText('当前无法生成建议，请稍后重试。')
  } finally {
    sending.value = false
  }
}

onMounted(async () => {
  await loadProfile(true)
})

watch(profileDrawerVisible, async (v) => {
  if (v) {
    await loadProfile(true)
  }
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  color: var(--c-ink);
}

.profile-card,
.chat-card {
  border-radius: var(--radius-lg);
  border: none;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03);
  height: calc(100vh - 104px);
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

.chat-head-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.profile-head-btn {
  border-radius: 999px;
  border: 1px solid var(--c-border);
  background: rgba(255, 255, 255, 0.8);
  color: var(--c-ink);
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.profile-head-meta {
  font-weight: 600;
  color: var(--c-ink-3);
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-dialog :deep(.el-dialog) {
  width: min(760px, 94vw) !important;
  border-radius: 18px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  background: #ffffff;
  overflow: hidden;
}

.citations {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.citations-header {
  font-weight: 800;
  color: var(--c-ink);
}

.citations-empty {
  color: var(--c-ink-3);
  font-size: 13px;
}

.citations-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.citation-item {
  border: 1px solid var(--c-border);
  border-radius: 12px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.75);
}

.citation-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.citation-title {
  font-weight: 750;
  color: var(--c-ink);
  font-size: 14px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.citation-link {
  flex: none;
  font-weight: 700;
  font-size: 12px;
  color: var(--c-primary);
  text-decoration: none;
  border: 1px solid rgba(59, 130, 246, 0.35);
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.08);
}

.citation-meta {
  margin-top: 6px;
  color: var(--c-ink-3);
  font-size: 12px;
}

.citation-snippet {
  margin-top: 8px;
  color: var(--c-ink-2);
  font-size: 13px;
  line-height: 1.55;
  white-space: pre-wrap;
}

.profile-dialog :deep(.el-dialog__body) {
  padding-top: 4px;
}

.profile-dialog :deep(.el-dialog__header) {
  background: #ffffff;
}

.profile-dialog-body {
  max-height: min(72vh, 720px);
  overflow: auto;
}

.chat-tools {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
  justify-content: flex-start;
}

:deep(.chat-tools .el-button) {
  height: 34px;
  padding: 0 12px;
  font-weight: 700;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.14);
  background: transparent;
  color: var(--c-ink);
}

.chat-tools :deep(.el-button:hover) {
  background: rgba(15, 118, 110, 0.08);
  border-color: rgba(15, 118, 110, 0.28);
  color: var(--c-primary-700);
}

@media (max-width: 768px) {
  .chat-body {
    padding: 14px;
  }

  .chat-bubble {
    max-width: 94%;
  }

  .profile-head-meta {
    display: none;
  }

  :deep(.composer-input .el-textarea__inner) {
    min-height: 54px !important;
  }
}

.resume-meta-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.resume-collapse {
  margin-top: 10px;
}

.resume-preview {
  white-space: pre-wrap;
  font-size: 12px;
  color: #334155;
  line-height: 1.6;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 10px;
  max-height: 220px;
  overflow: auto;
}

.resume-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 12px;
  color: #334155;
}

.resume-cards {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
}

.resume-mini-card {
  border-radius: 12px;
  border: 1px solid #e2e8f0;
}

.resume-mini-title {
  font-weight: 800;
  font-size: 13px;
  color: #0f172a;
  margin-bottom: 6px;
}

.resume-mini-sub {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 8px;
}

.resume-mini-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.resume-mini-text {
  font-size: 12px;
  color: #334155;
  line-height: 1.6;
  white-space: pre-wrap;
}

.chat-actions {
  display: flex;
  gap: 8px;
}

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 18px;
  background-color: #f8fafc;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.chat-line {
  display: flex;
  width: min(1000px, 100%);

  margin-bottom: 14px;
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
  padding: 12px 16px;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
}

.chat-line.assistant .chat-bubble {
  background: rgba(255, 255, 255, 0.90);
  border-top-left-radius: 4px;
}

.chat-line.user .chat-bubble {
  background: var(--c-primary-600);
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
  color: rgba(255, 255, 255, 0.88);
  text-align: right;
}

.chat-line.assistant .chat-role {
  color: var(--c-ink-3);
}

.chat-content {
  word-break: break-word;
  line-height: 1.6;
  font-size: 13.5px;
}

.job-cards {
  width: 100%;
}

.job-cards-bubble {
  width: 100%;
  max-width: 100%;
}

.job-cards-header {
  font-size: 13px;
  font-weight: 700;
  color: var(--c-ink);
  margin-bottom: 10px;
}

.job-card {
  border-radius: 12px;
  border: 1px solid var(--c-border);
  background: rgba(255, 255, 255, 0.90);
  cursor: pointer;
  transition: transform 0.12s ease, box-shadow 0.12s ease, border-color 0.12s ease;
}

.job-card:hover {
  transform: translateY(-1px);
  border-color: var(--c-border-2);
  box-shadow: 0 6px 14px rgba(15, 23, 42, 0.08);
}

.job-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 6px;
}

.job-card-title {
  font-weight: 800;
  color: var(--c-ink);
  font-size: 14px;
  line-height: 1.3;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.job-card-sub {
  font-size: 12px;
  color: var(--c-ink-3);
  margin-bottom: 10px;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
  overflow: hidden;
}

.job-card-salary-pill {
  flex: none;
  font-weight: 800;
  color: var(--c-primary-700);
  background: rgba(15, 118, 110, 0.10);
  border: 1px solid rgba(15, 118, 110, 0.18);
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 18px;
}

.job-card-salary-pill.muted {
  color: var(--c-ink-3);
  background: rgba(255, 255, 255, 0.65);
  border-color: var(--c-border);
}

.job-card-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.job-card-score {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.job-card-score-text {
  font-size: 12px;
  font-weight: 700;
  color: var(--c-ink-2);
  flex: none;
  white-space: nowrap;
}

.job-card-reason {
  font-size: 12px;
  color: var(--c-ink-2);
  line-height: 1.6;
  background: rgba(255, 255, 255, 0.55);
  border: 1px solid var(--c-border);
  border-radius: 10px;
  padding: 10px;
  margin-bottom: 10px;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  overflow: hidden;
}

.job-card-actions {
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
  max-height: 320px;
  overflow: auto;
}

.job-detail-url-row {
  display: flex;
  gap: 10px;
  align-items: center;
}

.chat-line.user .chat-content {
  color: #ffffff;
}

.chat-line.assistant .chat-content {
  color: #334155;
}

.composer-shell {
  padding: 12px 14px;
  background-color: #f8fafc;
  display: flex;
  justify-content: center;
}

.composer {
  width: min(680px, 100%);
  border-radius: 20px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.08);
  padding: 12px 12px 10px;
}

.composer-shell .composer {
  margin: 0 auto;
}

.composer-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px 8px;
}

.composer-brand {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: rgba(15, 23, 42, 0.58);
  font-weight: 700;
  font-size: 13px;
}



.composer-mid {
  position: relative;
  padding: 0 2px;
}

:deep(.composer-input .el-textarea__inner) {
  min-height: 56px !important;
  border-radius: 18px;
  padding: 12px 14px;
  padding-bottom: 100px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  background: rgba(248, 250, 252, 0.85);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8);
  color: var(--c-ink);
  line-height: 1.7;
  resize: none;
  transition: border-color 180ms var(--ease-out), box-shadow 180ms var(--ease-out), transform 180ms var(--ease-out);
}

:deep(.composer-input .el-textarea__inner::placeholder) {
  color: rgba(15, 23, 42, 0.45);
}

:deep(.composer-input .el-textarea__inner:focus) {
  border-color: rgba(37, 99, 235, 0.45);
  box-shadow: 0 0 0 4px rgba(37, 99, 235, 0.12), inset 0 1px 0 rgba(255, 255, 255, 0.8);
  transform: translateY(-1px);
}

.composer-actions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex: none;
}

.composer-inline {
  position: absolute;
  left: 14px;
  right: 14px;
  bottom: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.composer-inline .chat-tools {
  margin-bottom: 0;
}

.composer-inline .chat-tools :deep(.el-button) {
  height: 30px;
  padding: 0 10px;
  font-weight: 700;
  font-size: 12px;
  background: rgba(255, 255, 255, 0.85);
}

.composer-icon-btn {
  width: 40px;
  height: 40px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.14);
  background: rgba(255, 255, 255, 0.94);
  color: rgba(15, 23, 42, 0.72);
}

.composer-send-btn {
  width: 40px;
  height: 40px;
  border-radius: 999px;
  box-shadow: 0 14px 30px rgba(37, 99, 235, 0.26);
}

.composer-bottom {
  padding: 10px 2px 0;
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
  color: var(--c-ink);
}

.match-salary {
  font-weight: 800;
  color: var(--c-primary-700);
  font-size: 14px;
}

.match-company {
  font-size: 13px;
  color: var(--c-ink-3);
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
  color: var(--c-ink-2);
}

.match-reason {
  font-size: 12px;
  color: var(--c-ink-3);
  background: rgba(255, 255, 255, 0.55);
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
