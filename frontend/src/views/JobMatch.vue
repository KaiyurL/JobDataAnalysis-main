<template>
  <div class="job-match">
    <div class="page-header">
      <h1>🤖 智能求职助手</h1>
      <p>基于阿里云百炼大模型，为你生成定制化求职建议</p>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :sm="24" :md="9" :lg="8">
        <el-card class="profile-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>🧾 求职画像</span>
              <el-button size="small" @click="resetProfile">重置</el-button>
            </div>
          </template>

          <el-form label-position="top" :model="profile">
            <el-form-item label="目标岗位">
              <el-input v-model="profile.targetRole" placeholder="例如：Java后端 / 前端 / 数据分析" clearable />
            </el-form-item>
            <el-form-item label="城市">
              <el-input v-model="profile.city" placeholder="例如：福州/北京/远程" clearable />
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
                :rows="3"
                placeholder="例如：Java, Spring Boot, MySQL, Redis, Vue"
              />
            </el-form-item>
            <el-form-item label="补充说明">
              <el-input
                v-model="profile.notes"
                type="textarea"
                :rows="4"
                placeholder="例如：希望找实习/校招；简历项目偏少；面试薄弱点等"
              />
            </el-form-item>
          </el-form>

          <div class="quick-prompts">
            <el-button size="small" @click="usePrompt('请帮我做岗位定位，并给出 30 天行动计划')">岗位定位</el-button>
            <el-button size="small" @click="usePrompt('请帮我优化简历要点，给出可以直接写到简历上的 bullet')">简历优化</el-button>
            <el-button size="small" @click="usePrompt('请列出面试复习清单，并给出项目讲述结构')">面试清单</el-button>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="24" :md="15" :lg="16">
        <el-card class="chat-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>💬 对话</span>
              <div class="chat-actions">
                <el-button size="small" @click="clearChat" :disabled="sending || messages.length <= 1">清空对话</el-button>
              </div>
            </div>
          </template>

          <div ref="chatBodyRef" class="chat-body">
            <div v-for="m in messages" :key="m.id" class="chat-line" :class="m.role">
              <div class="chat-bubble">
                <div class="chat-role">{{ m.role === 'user' ? '我' : '助手' }}</div>
                <div class="chat-content">{{ m.content }}</div>
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
    </el-row>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import api from '../api.js'
import { ElMessage } from 'element-plus'

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
    content: '把你的目标岗位、城市、学历、经验、技能补充一下，然后告诉我你最想解决的问题。我会给你一份可执行的求职建议。'
  }
])

const input = ref('')
const sending = ref(false)
const chatBodyRef = ref(null)

const scrollToBottom = async () => {
  await nextTick()
  const el = chatBodyRef.value
  if (!el) return
  el.scrollTop = el.scrollHeight
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

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.profile-card,
.chat-card {
  border-radius: 8px;
}

.quick-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chat-actions {
  display: flex;
  gap: 8px;
}

.chat-body {
  height: 520px;
  overflow-y: auto;
  padding: 6px 4px;
}

.chat-line {
  display: flex;
  margin: 10px 0;
}

.chat-line.user {
  justify-content: flex-end;
}

.chat-line.assistant {
  justify-content: flex-start;
}

.chat-bubble {
  max-width: 92%;
  border-radius: 10px;
  padding: 10px 12px;
  background: #f5f7fa;
}

.chat-line.user .chat-bubble {
  background: #ecf5ff;
}

.chat-role {
  font-size: 12px;
  color: #606266;
  margin-bottom: 6px;
}

.chat-content {
  white-space: pre-wrap;
  word-break: break-word;
  color: #303133;
  line-height: 1.6;
}

.chat-input {
  border-top: 1px solid #ebeef5;
  padding-top: 12px;
}

.chat-buttons {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}
</style>
