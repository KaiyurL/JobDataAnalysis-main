<template>
  <el-card class="chat-card" shadow="hover">
    <template #header>
      <div class="card-header">
        <div class="chat-head-left">
          <span>💬 AI 求职辅导</span>
          <el-button class="profile-head-btn" size="small" @click="emit('openProfile')">
            🧾 求职画像
            <span class="profile-head-meta">
              {{ profileSummary?.targetRole || '未设置目标岗位' }}
              <span v-if="profileSummary?.city"> · {{ profileSummary.city }}</span>
            </span>
          </el-button>
        </div>
        <div class="chat-actions">
          <el-button size="small" :disabled="sending || messages.length <= 1" @click="emit('clearChat')">清空对话</el-button>
        </div>
      </div>
    </template>

    <div :ref="setChatBodyEl" class="chat-body">
      <div v-for="m in messages" :key="m.id" class="chat-line" :class="m.role">
        <div class="chat-bubble" :class="{ 'job-cards-bubble': m.kind === 'job_cards' }">
          <div class="chat-role">{{ m.role === 'user' ? '我' : 'AI 顾问' }}</div>
          <div v-if="m.kind === 'job_cards'" class="job-cards">
            <div class="job-cards-header">{{ m.title || '推荐岗位（可点开查看详情）' }}</div>
            <el-row :gutter="12">
              <el-col v-for="c in m.cards" :key="c.key" :xs="24" :sm="12" :md="12" :lg="8">
                <JobMatchJobCard
                  :card="c"
                  :is-favorite="isFavoriteCard(c)"
                  :favorite-busy-key="favoriteBusyKey"
                  :favorite-key="favoriteKeyOfCard(c)"
                  :source-label="sourceLabel"
                  :source-tag-type="sourceTagType"
                  :score-color="scoreColor"
                  @open-detail="emit('openJobDetail', $event)"
                  @toggle-favorite="emit('toggleFavorite', $event)"
                />
              </el-col>
            </el-row>
          </div>
          <div v-else class="chat-content" v-html="formatContent(m.content)"></div>
        </div>
      </div>
    </div>

    <div class="composer-shell">
      <div class="composer">
        <div class="composer-top">
          <div class="composer-brand"></div>
        </div>

        <div class="composer-mid">
          <el-input
            :model-value="input"
            class="composer-input"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 4 }"
            placeholder="输入你的问题，例如：我现在是应届生，目标 Java 后端，帮我规划投递与面试准备"
            :disabled="sending"
            @update:model-value="(v) => emit('update:input', v)"
            @keydown.enter.exact.prevent="emit('send')"
          />

          <div class="composer-inline">
            <div class="chat-tools">
              <el-button
                size="small"
                @click="
                  emit(
                    'usePrompt',
                    '请基于我的求职画像，从数据库检索岗位并做精排推荐：输出5个最适合的岗位（按优先级排序），每个岗位给出匹配点/差距点/投递与面试准备建议。'
                  )
                "
              >
                匹配岗位
              </el-button>
              <el-button size="small" @click="emit('usePrompt', '帮我优化简历要点，给出可以直接写到简历上的 bullet')"> 简历优化 </el-button>
              <el-button size="small" @click="emit('usePrompt', '列出我这种背景去面这个岗位，最容易被问到的5个面试题')">
                模拟面试
              </el-button>
            </div>
            <div class="composer-actions">
              <el-button
                class="composer-send-btn"
                circle
                type="primary"
                :loading="sending"
                :disabled="!String(input || '').trim()"
                title="发送"
                @click="emit('send')"
              >
                <el-icon><ArrowUp /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { ArrowUp } from '@element-plus/icons-vue'
import JobMatchJobCard from '@/features/job-match/components/JobMatchJobCard.vue'

defineProps({
  messages: {
    type: Array,
    required: true
  },
  input: {
    type: String,
    required: true
  },
  sending: {
    type: Boolean,
    required: true
  },
  profileSummary: {
    type: Object,
    required: true
  },
  setChatBodyEl: {
    type: Function,
    required: true
  },
  formatContent: {
    type: Function,
    required: true
  },
  isFavoriteCard: {
    type: Function,
    required: true
  },
  favoriteBusyKey: {
    type: String,
    required: true
  },
  favoriteKeyOfCard: {
    type: Function,
    required: true
  },
  sourceLabel: {
    type: Function,
    required: true
  },
  sourceTagType: {
    type: Function,
    required: true
  },
  scoreColor: {
    type: Function,
    required: true
  }
})

const emit = defineEmits(['update:input', 'openProfile', 'clearChat', 'usePrompt', 'send', 'openJobDetail', 'toggleFavorite'])
</script>
