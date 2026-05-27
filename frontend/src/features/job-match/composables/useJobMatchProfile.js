import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { resumeApi, userApi } from '@/services/index.js'

export function useJobMatchProfile() {
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
  const profileDialogVisible = ref(false)
  const savingProfile = ref(false)
  const parsingResume = ref(false)

  const loadProfile = async (silent = false) => {
    try {
      const data = await userApi.getUserProfileData()
      const p = data?.profile || {}
      profile.value = {
        targetRole: p.targetRole || '',
        city: p.city || '',
        education: p.education || '',
        experience: p.experience || '',
        skills: p.skills || '',
        notes: p.notes || ''
      }
      resumeMeta.value = data?.resumeMeta || null
      const extra = data?.profileExtra || {}
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

  const saveProfile = async (silent = false) => {
    savingProfile.value = true
    try {
      await userApi.saveUserProfileData({
        profile: { ...profile.value },
        resumeMeta: resumeMeta.value,
        profileExtra: profileExtra.value
      })
      if (!silent) ElMessage.success('已保存')
      return true
    } catch (e) {
      if (!silent) ElMessage.error(String(e?.message || '保存失败'))
      return false
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

  const handleResumeUpload = async (file) => {
    if (!file || !file.raw) return false

    const isValidType = [
      'application/pdf',
      'application/msword',
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      'text/plain'
    ].includes(file.raw.type)
    const isExtensionValid = /\.(pdf|doc|docx|txt)$/i.test(file.name)

    if (!isValidType && !isExtensionValid) {
      ElMessage.error('仅支持 PDF, DOC, DOCX, TXT 格式的文件')
      return false
    }
    if (file.raw.size > 20 * 1024 * 1024) {
      ElMessage.error('简历文件大小不能超过 20MB')
      return false
    }

    parsingResume.value = true
    ElMessage.info('正在解析简历并提取信息，请稍候...')

    try {
      const data = await resumeApi.parseResumeData(file.raw)
      resumeMeta.value = data?._resume || null
      profileExtra.value = {
        highlights: Array.isArray(data?.highlights) ? data.highlights : [],
        workExperiences: Array.isArray(data?.workExperiences) ? data.workExperiences : [],
        projects: Array.isArray(data?.projects) ? data.projects : [],
        certifications: Array.isArray(data?.certifications) ? data.certifications : [],
        links: Array.isArray(data?.links) ? data.links : []
      }
      profile.value = {
        targetRole: data?.targetRole || profile.value.targetRole,
        city: data?.city || profile.value.city,
        education: data?.education || profile.value.education,
        experience: data?.experience || profile.value.experience,
        skills: data?.skills || profile.value.skills,
        notes: data?.notes || profile.value.notes
      }

      ElMessage.success('简历解析成功，已自动填充求职画像')
      await saveProfile(true)
      return true
    } catch (e) {
      const backendMessage = e?.response?.data?.message
      ElMessage.error('简历解析异常: ' + (backendMessage || e?.message || '网络或服务端错误'))
      return false
    } finally {
      parsingResume.value = false
    }
  }

  onMounted(async () => {
    await loadProfile(true)
  })

  watch(profileDialogVisible, async (v) => {
    if (v) {
      await loadProfile(true)
    }
  })

  return {
    profile,
    resumeMeta,
    profileExtra,
    profileDialogVisible,
    savingProfile,
    parsingResume,
    loadProfile,
    saveProfile,
    resetProfile,
    handleResumeUpload
  }
}
