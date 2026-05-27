<template>
  <el-card class="action-card" shadow="hover">
    <template #header>
      <span>⚡️ 数据操作</span>
    </template>

    <div class="update-section">
      <el-button
        type="danger"
        size="large"
        :loading="updating"
        :disabled="buttonDisabled"
        style="width: 100%; height: 50px"
        @click="startUpdate"
      >
        <el-icon><Refresh /></el-icon>
        {{ buttonDisabled ? '请等待...' : '立即更新数据' }}
      </el-button>

      <el-button
        v-if="canConfirmLogin"
        type="primary"
        size="large"
        style="width: 100%; height: 50px; margin-top: 12px"
        @click="confirmLogin"
      >
        我已登录，继续爬取
      </el-button>

      <div class="tips">
        <p>⚠️ 注意：点击后将启动爬虫脚本，过程可能需要几分钟</p>
        <p>💡 请先保存配置，再启动爬虫</p>
        <p>🔒 爬虫运行时按钮将禁用30秒，防止重复触发</p>
        <p v-if="canConfirmLogin">✅ 请先在弹出的浏览器中完成登录/验证，然后点击“我已登录，继续爬取”</p>
      </div>
    </div>
  </el-card>

  <el-card class="preview-card" shadow="hover" style="margin-top: 20px">
    <template #header>
      <span>🔍 本次爬取预览</span>
    </template>

    <div class="preview-content">
      <div class="preview-item">
        <span class="preview-label">关键词：</span>
        <span class="preview-value">{{ config.keywords.join('、') || '-' }}</span>
      </div>
      <div class="preview-item">
        <span class="preview-label">城市：</span>
        <span class="preview-value">{{ config.cities.join('、') || '-' }}</span>
      </div>
      <div class="preview-item">
        <span class="preview-label">预计请求：</span>
        <span class="preview-value">{{ expectedRequests }} 次</span>
      </div>
    </div>
  </el-card>

  <el-card class="reindex-card" shadow="hover" style="margin-top: 20px">
    <template #header>
      <span>🧠 向量索引</span>
    </template>
    <div class="reindex-section">
      <p class="form-tip" style="margin: 0 0 12px 0">将数据库岗位数据向量化存入索引，供 AI 对话中的 RAG 检索使用。新增岗位后需重建。</p>
      <el-button type="primary" size="large" :loading="reindexing" style="width: 100%" @click="handleReindex">
        <el-icon><Refresh /></el-icon>
        清洗重复数据和重建向量索引
      </el-button>
      <div v-if="reindexResult != null || reindexDedup != null" style="margin-top: 12px" class="reindex-result">
        <el-tag v-if="reindexResult != null" type="success" effect="plain">已索引 {{ reindexResult }} 个岗位</el-tag>
        <el-tag v-if="reindexDedup != null" type="info" effect="plain" style="margin-left: 8px">
          已清洗重复数据 {{ reindexDedup.totalDeleted }} 条
        </el-tag>
      </div>
    </div>
  </el-card>

  <el-card class="log-card" shadow="hover" style="margin-top: 20px">
    <template #header>
      <div class="card-header">
        <span>📝 运行日志</span>
        <el-button size="small" link @click="clearLogs">清空</el-button>
      </div>
    </template>

    <div :ref="setLogContainer" class="log-container">
      <div v-if="logs.length === 0" class="empty-log">暂无日志</div>
      <div v-for="(log, index) in logs" :key="index" class="log-item">
        <span class="log-time">[{{ log.time }}]</span>
        <span class="log-text">{{ log.text }}</span>
      </div>
    </div>
  </el-card>
</template>

<script setup>
/**
 * 数据管理页右侧面板（操作区 + 预览 + 向量索引 + 日志）
 *
 * 设计目标：
 * - 右侧区域承担“触发更新/确认登录/重建索引/查看日志”的 UI；
 * - 与 useDataManagement composable 解耦，所有状态与动作由上层注入。
 */
defineProps({
  updating: { type: Boolean, required: true },
  buttonDisabled: { type: Boolean, required: true },
  canConfirmLogin: { type: Boolean, required: true },
  expectedRequests: { type: Number, required: true },
  config: { type: Object, required: true },
  logs: { type: Array, required: true },
  setLogContainer: { type: Function, required: true },
  reindexing: { type: Boolean, required: true },
  reindexResult: { type: Number, required: false, default: undefined },
  reindexDedup: { type: Object, required: false, default: undefined },
  startUpdate: { type: Function, required: true },
  confirmLogin: { type: Function, required: true },
  handleReindex: { type: Function, required: true },
  clearLogs: { type: Function, required: true }
})
</script>
