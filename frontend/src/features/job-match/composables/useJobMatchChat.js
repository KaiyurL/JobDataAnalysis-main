import { nextTick, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { clearAuth, getToken } from '@/shared/authStorage.js'

export function useJobMatchChat(deps) {
  const profileRef = deps?.profile

  const messages = ref([
    {
      id: `m_${Date.now()}`,
      role: 'assistant',
      kind: 'text',
      content:
        '你好！我是你的 AI 求职顾问。你可以点击上方的“求职画像”完善信息，或点击“上传简历”自动提取。你也可以点击下方“匹配岗位/简历优化/模拟面试”快速生成提问内容，然后点击发送获取建议。'
    }
  ])
  const input = ref('')
  const sending = ref(false)

  const chatBodyEl = ref(null)

  const setChatBodyEl = (el) => {
    chatBodyEl.value = el
  }

  const scrollToBottom = async () => {
    await nextTick()
    const el = chatBodyEl.value
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

  const buildHistoryPayload = () => {
    const maxHistory = 12
    const tail = messages.value.slice(Math.max(0, messages.value.length - maxHistory))
    return tail
      .filter((m) => (m.role === 'user' || m.role === 'assistant') && m.kind !== 'job_cards')
      .map((m) => ({ role: m.role, content: m.content }))
  }

  const redirectToLogin = () => {
    if (typeof window === 'undefined') return
    if (window.location.pathname === '/login') return
    window.location.href = '/login'
  }

  const callAgentChatStream = async (userText, onDelta, onEnd) => {
    const token = getToken() || ''
    const res = await fetch('/api/agent/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: JSON.stringify({
        profile: { ...(profileRef?.value || {}) },
        message: userText,
        history: buildHistoryPayload()
      })
    })

    if (res.status === 401) {
      clearAuth()
      redirectToLogin()
      throw new Error('未登录或登录已过期')
    }
    if (!res.ok) {
      throw new Error(`请求失败(${res.status})`)
    }
    if (!res.body) {
      throw new Error('当前浏览器不支持流式响应')
    }

    const reader = res.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    let pendingJson = ''

    const normalizeNewlines = (s) => {
      if (!s) return ''
      return String(s).replace(/\r\n/g, '\n').replace(/\r/g, '\n')
    }

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
      const merged = pendingJson ? `${pendingJson}${dataStr}` : dataStr
      const trimmed = merged.trim()
      if (!trimmed) return

      const looksLikeJson = trimmed.startsWith('{') || trimmed.startsWith('[')
      if (!looksLikeJson) {
        pendingJson = ''
        onDelta?.(merged)
        return
      }

      let obj = null
      try {
        obj = JSON.parse(merged)
      } catch {
        if (merged.length > 2 * 1024 * 1024) {
          pendingJson = ''
          onDelta?.(merged)
          return
        }
        pendingJson = merged
        return
      }

      pendingJson = ''
      if (!obj || typeof obj !== 'object') return
      if (obj.type === 'delta') {
        onDelta?.(String(obj.text || ''))
      } else if (obj.type === 'end') {
        onEnd?.(obj.payload || {})
      } else if (typeof obj.text === 'string') {
        onDelta?.(obj.text)
      }
    }

    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      buffer = normalizeNewlines(buffer)
      let idx
      while ((idx = buffer.indexOf('\n\n')) >= 0) {
        const block = buffer.slice(0, idx)
        buffer = buffer.slice(idx + 2)
        if (block.trim()) flushBlock(block)
      }
    }
    buffer += decoder.decode()
    buffer = normalizeNewlines(buffer)
    if (buffer.trim()) flushBlock(buffer)
    if (pendingJson.trim()) {
      try {
        const obj = JSON.parse(pendingJson)
        if (obj && typeof obj === 'object') {
          if (obj.type === 'delta') {
            onDelta?.(String(obj.text || ''))
          } else if (obj.type === 'end') {
            onEnd?.(obj.payload || {})
          } else if (typeof obj.text === 'string') {
            onDelta?.(obj.text)
          }
        }
      } catch {
        onDelta?.(pendingJson)
      } finally {
        pendingJson = ''
      }
    }
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
        education: c.education,
        jobDesc: c.jobDesc,
        jobKeywords: c.jobKeywords,
        companySize: c.companySize,
        companyIndustry: c.companyIndustry,
        companyWelfare: c.companyWelfare,
        publishDate: c.publishDate,
        createdAt: c.createdAt
      }
      out.push({
        index: i + 1,
        key: `${sourceTable}::${job.jobUrl || job.id || i}`,
        job,
        sourceTable,
        matchScore: c.matchScore,
        aiReason: c.aiReason
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
    if (!content || sending.value) return

    await pushUserText(content)
    input.value = ''

    sending.value = true
    try {
      const msgId = `a_${Date.now()}`
        const msg = reactive({ id: msgId, role: 'assistant', kind: 'text', content: '' })
      messages.value.push(msg)
      await scrollToBottom()

      let finalPayload = null
      let pendingDelta = ''
      let deltaRaf = 0
      let scrollRaf = 0

      const scheduleScroll = () => {
        if (typeof window === 'undefined') return
        if (scrollRaf) return
        scrollRaf = window.requestAnimationFrame(async () => {
          scrollRaf = 0
          await scrollToBottom()
        })
      }

      const scheduleDeltaFlush = () => {
        if (!pendingDelta) return
        msg.content = (msg.content || '') + pendingDelta
        pendingDelta = ''
      }

      const scheduleDelta = () => {
        if (typeof window === 'undefined') {
          scheduleDeltaFlush()
          scrollToBottom()
          return
        }
        if (deltaRaf) return
        deltaRaf = window.requestAnimationFrame(() => {
          deltaRaf = 0
          scheduleDeltaFlush()
          scheduleScroll()
        })
      }

      await callAgentChatStream(
        content,
        (delta) => {
          pendingDelta += delta || ''
          scheduleDelta()
        },
        (payload) => {
          finalPayload = payload || null
        }
      )

      if (typeof window !== 'undefined') {
        if (deltaRaf) window.cancelAnimationFrame(deltaRaf)
        if (scrollRaf) window.cancelAnimationFrame(scrollRaf)
      }
      scheduleDeltaFlush()
      await scrollToBottom()

      if (!msg.content) {
        msg.content = '未返回内容'
      }

      const cards = mapAgentJobCards(finalPayload?.jobCards)
      if (cards.length > 0) {
        await pushAssistantJobCards('推荐岗位（以此为准）', cards)
      }
    } catch (e) {
      const errorMessage = e?.message || 'AI 生成失败，请检查后端和百炼配置'
      ElMessage.error(String(errorMessage))
      await pushAssistantText('当前无法生成建议，请稍后重试。')
    } finally {
      sending.value = false
    }
  }

  return {
    messages,
    input,
    sending,
    setChatBodyEl,
    formatContent,
    pushAssistantText,
    clearChat,
    usePrompt,
    handleSend
  }
}
