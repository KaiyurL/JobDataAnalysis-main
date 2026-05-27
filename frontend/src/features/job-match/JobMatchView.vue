<template>
  <div class="u-stack">
    <el-row :gutter="16">
      <el-col :xs="24" :sm="24" :md="24" :lg="24">
        <JobMatchChatPanel
          :messages="messages"
          :input="input"
          :sending="sending"
          :profile-summary="profile"
          :set-chat-body-el="setChatBodyEl"
          :format-content="formatContent"
          :favorite-busy-key="favoriteBusyKey"
          :is-favorite-card="isFavoriteCard"
          :favorite-key-of-card="favoriteKeyOfCard"
          :source-label="sourceLabel"
          :source-tag-type="sourceTagType"
          :score-color="scoreColor"
          @open-profile="profileDialogVisible = true"
          @clear-chat="clearChat"
          @use-prompt="usePrompt"
          @send="handleSend"
          @update:input="input = $event"
          @open-job-detail="openJobDetail"
          @toggle-favorite="toggleFavoriteCard"
        />
      </el-col>
    </el-row>

    <JobMatchProfileDialog
      :visible="profileDialogVisible"
      :profile="profile"
      :resume-meta="resumeMeta"
      :profile-extra="profileExtra"
      :saving-profile="savingProfile"
      :parsing-resume="parsingResume"
      @update:visible="profileDialogVisible = $event"
      @save="saveProfile(false)"
      @resume-upload="handleResumeUploadWithHint"
      @reset="resetProfile"
    />

    <JobMatchJobDetailDialog
      :visible="jobDetailVisible"
      :selected-job="selectedJob"
      :source-label="sourceLabel"
      :split-keywords="splitKeywords"
      :normalize-job-url="normalizeJobUrl"
      :can-open-url="canOpenUrl"
      @update:visible="jobDetailVisible = $event"
    />
  </div>
</template>

<script setup>
import { inject, ref } from 'vue'
import { baseTheme } from '@/shared/theme.js'
import JobMatchChatPanel from '@/features/job-match/components/JobMatchChatPanel.vue'
import JobMatchJobDetailDialog from '@/features/job-match/components/JobMatchJobDetailDialog.vue'
import JobMatchProfileDialog from '@/features/job-match/components/JobMatchProfileDialog.vue'
import { useJobMatchChat } from '@/features/job-match/composables/useJobMatchChat.js'
import { useJobMatchProfile } from '@/features/job-match/composables/useJobMatchProfile.js'

const jobDetailVisible = ref(false)
const selectedJob = ref(null)

const {
  profile,
  resumeMeta,
  profileExtra,
  profileDialogVisible,
  savingProfile,
  parsingResume,
  saveProfile,
  resetProfile,
  handleResumeUpload
} = useJobMatchProfile()
const { messages, input, sending, setChatBodyEl, formatContent, pushAssistantText, clearChat, usePrompt, handleSend } = useJobMatchChat({
  profile
})

const userDataStore = inject('userDataStore', null)
const favoriteBusyKey = userDataStore?.favoriteBusyKey || ref('')
const favoriteKeyOfCard = (card) => `${String(card?.sourceTable || '').trim()}::${String(card?.job?.jobUrl || '').trim()}`
const isFavoriteCard = (card) => (userDataStore ? userDataStore.isFavorite(card?.sourceTable, card?.job?.jobUrl) : false)
const toggleFavoriteCard = (card) => {
  if (!userDataStore) return
  userDataStore.toggleFavorite({ sourceTable: card?.sourceTable, job: card?.job })
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

const canOpenUrl = (url) => {
  const u = normalizeJobUrl(url)
  return u && u !== '#'
}

const scoreColor = (score) => {
  const t = baseTheme()
  if (score >= 80) return t.success
  if (score >= 60) return t.info
  return t.warning
}

const sourceLabel = (sourceTable) => {
  if (sourceTable === 'job_info_51job' || sourceTable === '51job') return '51job'
  if (sourceTable === 'job_info' || sourceTable === 'boss') return 'Boss'
  return '未知'
}

const sourceTagType = (sourceTable) => {
  if (sourceTable === 'job_info_51job' || sourceTable === '51job') return 'warning'
  if (sourceTable === 'job_info' || sourceTable === 'boss') return 'success'
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
  const raw = String(s)
    .split(/[,，\s]+/)
    .map((x) => x.trim())
    .filter(Boolean)
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

const handleResumeUploadWithHint = async (file) => {
  const ok = await handleResumeUpload(file)
  if (!ok) return
  await pushAssistantText(
    '我已经提取了你简历中的关键信息并填入左侧画像，你可以检查或修改它。建议你现在点击“匹配岗位”让我按你的画像推荐岗位。'
  )
}
</script>

<style>
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
  box-shadow:
    0 4px 6px -1px rgba(0, 0, 0, 0.05),
    0 2px 4px -1px rgba(0, 0, 0, 0.03);
  height: calc(100vh - 104px);
  display: flex;
  flex-direction: column;
}

.el-card__body {
  flex: 1;
  overflow-y: auto;
}

.chat-card .el-card__body {
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

.profile-dialog .el-dialog {
  width: min(760px, 94vw) !important;
  border-radius: 18px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  background: #ffffff;
  overflow: hidden;
}

.profile-dialog .el-dialog__body {
  padding-top: 4px;
}

.profile-dialog .el-dialog__header {
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

.chat-tools .el-button {
  height: 34px;
  padding: 0 12px;
  font-weight: 700;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.14);
  background: transparent;
  color: var(--c-ink);
}

.chat-tools .el-button:hover {
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

  .composer-input .el-textarea__inner {
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
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
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
  background: rgba(255, 255, 255, 0.9);
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
  background: rgba(255, 255, 255, 0.9);
  cursor: pointer;
  transition:
    transform 0.12s ease,
    box-shadow 0.12s ease,
    border-color 0.12s ease;
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
  background: rgba(15, 118, 110, 0.1);
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

.composer-input .el-textarea__inner {
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
  transition:
    border-color 180ms var(--ease-out),
    box-shadow 180ms var(--ease-out),
    transform 180ms var(--ease-out);
}

.composer-input .el-textarea__inner::placeholder {
  color: rgba(15, 23, 42, 0.45);
}

.composer-input .el-textarea__inner:focus {
  border-color: rgba(37, 99, 235, 0.45);
  box-shadow:
    0 0 0 4px rgba(37, 99, 235, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
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

.composer-inline .chat-tools .el-button {
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
