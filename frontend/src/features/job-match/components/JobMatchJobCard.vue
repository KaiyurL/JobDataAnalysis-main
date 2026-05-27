<template>
  <el-card class="job-card" shadow="hover" :body-style="{ padding: '14px' }" @click="emitOpenDetail">
    <div class="job-card-head">
      <div class="job-card-title">{{ card?.job?.jobName }}</div>
      <div v-if="card?.job?.salaryMin != null && card?.job?.salaryMax != null" class="job-card-salary-pill">
        {{ card.job.salaryMin }}K-{{ card.job.salaryMax }}K
      </div>
      <div v-else class="job-card-salary-pill muted">面议</div>
    </div>

    <div class="job-card-sub">{{ card?.job?.companyName }} · {{ card?.job?.city }}</div>

    <div class="job-card-tags">
      <el-tag size="small" type="info" effect="plain">{{ card?.job?.experience || '经验不限' }}</el-tag>
      <el-tag size="small" type="info" effect="plain">{{ card?.job?.education || '学历不限' }}</el-tag>
      <el-tag size="small" :type="sourceTagType(card?.sourceTable)" effect="plain">{{ sourceLabel(card?.sourceTable) }}</el-tag>
    </div>

    <div v-if="card?.matchScore != null" class="job-card-score">
      <el-progress
        :percentage="Number(card.matchScore) || 0"
        :stroke-width="8"
        :show-text="false"
        :color="scoreColor(Number(card.matchScore) || 0)"
      />
      <div class="job-card-score-text">{{ Math.round((Number(card.matchScore) || 0) * 10) / 10 }}% 匹配</div>
    </div>

    <div v-if="card?.aiReason" class="job-card-reason">{{ card.aiReason }}</div>

    <div class="job-card-actions">
      <el-button
        size="small"
        :type="isFavorite ? 'warning' : 'default'"
        plain
        :loading="favoriteBusyKey === favoriteKey"
        @click.stop="emitToggleFavorite"
      >
        <el-icon><StarFilled v-if="isFavorite" /><Star v-else /></el-icon>
        {{ isFavorite ? '已收藏' : '收藏' }}
      </el-button>
      <el-button type="primary" size="small" @click.stop="emitOpenDetail">
        详情 <el-icon><View /></el-icon>
      </el-button>
    </div>
  </el-card>
</template>

<script setup>
import { Star, StarFilled, View } from '@element-plus/icons-vue'

const props = defineProps({
  card: {
    type: Object,
    required: true
  },
  isFavorite: {
    type: Boolean,
    required: true
  },
  favoriteBusyKey: {
    type: String,
    required: true
  },
  favoriteKey: {
    type: String,
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

const emit = defineEmits(['openDetail', 'toggleFavorite'])

const emitOpenDetail = () => emit('openDetail', props.card)
const emitToggleFavorite = () => emit('toggleFavorite', props.card)
</script>
