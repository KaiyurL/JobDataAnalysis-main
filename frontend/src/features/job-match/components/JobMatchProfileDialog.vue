<template>
  <el-dialog
    :model-value="visible"
    class="profile-dialog"
    width="760px"
    :append-to-body="true"
    :destroy-on-close="false"
    align-center
    @update:model-value="(v) => emit('update:visible', v)"
  >
    <template #header>
      <div class="card-header">
        <span>🧾 求职画像</span>
        <div class="u-inline u-gap-2">
          <el-button type="success" size="small" :loading="savingProfile" @click="emit('save')">保存</el-button>
          <el-upload
            action="#"
            :auto-upload="false"
            :show-file-list="false"
            :on-change="(file) => emit('resumeUpload', file)"
            accept=".pdf,.doc,.docx,.txt"
          >
            <el-button type="primary" size="small" :loading="parsingResume">
              <el-icon><Upload /></el-icon> 上传简历
            </el-button>
          </el-upload>
          <el-button size="small" title="重置" @click="emit('reset')"
            ><el-icon><RefreshLeft /></el-icon
          ></el-button>
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
        <el-tag size="small" type="success" effect="plain">
          已解析：{{ resumeMeta?.fileType?.toUpperCase?.() || resumeMeta?.fileType }}
        </el-tag>
        <el-tag v-if="resumeMeta?.textLength != null" size="small" type="info" effect="plain">文本 {{ resumeMeta.textLength }} 字</el-tag>
      </div>

      <el-collapse v-if="resumeMeta && resumeMeta.rich" class="resume-collapse" accordion>
        <el-collapse-item title="简历内容预览（节选）" name="preview">
          <div class="resume-preview">{{ resumeMeta.textPreview }}</div>
        </el-collapse-item>
        <el-collapse-item v-if="profileExtra?.highlights?.length" title="结构化亮点" name="highlights">
          <div class="resume-list">
            <div v-for="(x, i) in profileExtra.highlights" :key="i" class="resume-list-item">• {{ x }}</div>
          </div>
        </el-collapse-item>
        <el-collapse-item v-if="profileExtra?.projects?.length" title="项目经历" name="projects">
          <div class="resume-cards">
            <el-card v-for="(p, i) in profileExtra.projects" :key="i" class="resume-mini-card" shadow="never">
              <div class="resume-mini-title">
                {{ p.name || '项目' }}<span v-if="p.role"> · {{ p.role }}</span>
              </div>
              <div v-if="p.tech?.length" class="resume-mini-tags">
                <el-tag v-for="t in p.tech" :key="t" size="small" type="info" effect="plain">{{ t }}</el-tag>
              </div>
              <div v-if="p.highlights?.length" class="resume-mini-text">
                <div v-for="(h, j) in p.highlights" :key="j">• {{ h }}</div>
              </div>
            </el-card>
          </div>
        </el-collapse-item>
        <el-collapse-item v-if="profileExtra?.workExperiences?.length" title="工作经历" name="work">
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
</template>

<script setup>
import { RefreshLeft, Upload } from '@element-plus/icons-vue'

defineProps({
  visible: {
    type: Boolean,
    required: true
  },
  profile: {
    type: Object,
    required: true
  },
  resumeMeta: {
    type: Object,
    required: false,
    default: null
  },
  profileExtra: {
    type: Object,
    required: true
  },
  savingProfile: {
    type: Boolean,
    required: true
  },
  parsingResume: {
    type: Boolean,
    required: true
  }
})

const emit = defineEmits(['update:visible', 'save', 'resumeUpload', 'reset'])
</script>
