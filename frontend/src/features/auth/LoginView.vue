<template>
  <div class="jd-auth">
    <div class="jd-auth__card u-card u-card-pad">
      <div class="jd-auth__head">
        <div class="jd-auth__logo" aria-hidden="true">
          <el-icon :size="28"><DataAnalysis /></el-icon>
        </div>
        <div class="jd-auth__titles">
          <div class="u-title u-h2">招聘数据可视化系统</div>
          <div class="u-text u-muted">Job Data Visualization Platform</div>
        </div>
      </div>

      <el-segmented v-model="mode" :options="modeOptions" size="large" class="jd-auth__seg" />

      <el-form
        v-if="mode === 'login'"
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        label-position="top"
        class="jd-auth__form"
        @keyup.enter="handleLogin"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="loginForm.username" placeholder="请输入用户名" prefix-icon="User" size="large" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" prefix-icon="Lock" size="large" show-password />
        </el-form-item>

        <div class="u-row u-row-between">
          <el-checkbox v-model="loginForm.remember" label="记住我" />
          <span class="jd-auth__hint">测试账号: admin / admin123</span>
        </div>

        <el-button type="primary" size="large" class="jd-auth__btn" :loading="loading" @click="handleLogin">
          {{ loading ? '登录中...' : '登录' }}
        </el-button>
      </el-form>

      <el-form
        v-else
        ref="registerFormRef"
        :model="registerForm"
        :rules="registerRules"
        label-position="top"
        class="jd-auth__form"
        @keyup.enter="handleRegister"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="registerForm.username" placeholder="3-20位，仅字母/数字/下划线" prefix-icon="User" size="large" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            placeholder="至少8位，建议字母+数字"
            prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            placeholder="再次输入密码"
            prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>

        <el-button type="primary" size="large" class="jd-auth__btn" :loading="loading" @click="handleRegister">
          {{ loading ? '提交中...' : '注册并返回登录' }}
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DataAnalysis, User, Lock } from '@element-plus/icons-vue'
import { authApi } from '@/services/index.js'
import { setAuth } from '@/shared/authStorage.js'

const router = useRouter()
const loginFormRef = ref(null)
const registerFormRef = ref(null)

const mode = ref('login')
const modeOptions = [
  { label: '登录', value: 'login' },
  { label: '注册', value: 'register' }
]

const loginForm = reactive({
  username: '',
  password: '',
  remember: false
})

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const loading = ref(false)

const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: ''
})

const validateUsername = (rule, value, callback) => {
  const v = String(value || '').trim()
  if (!v) return callback(new Error('请输入用户名'))
  if (!/^[a-zA-Z0-9_]{3,20}$/.test(v)) return callback(new Error('3-20位，仅字母/数字/下划线'))
  callback()
}

const validatePassword = (rule, value, callback) => {
  const v = String(value || '')
  if (!v) return callback(new Error('请输入密码'))
  if (v.length < 8) return callback(new Error('密码至少8位'))
  callback()
}

const validateConfirm = (rule, value, callback) => {
  if (!value) return callback(new Error('请再次输入密码'))
  if (value !== registerForm.password) return callback(new Error('两次密码不一致'))
  callback()
}

const registerRules = {
  username: [{ validator: validateUsername, trigger: 'blur' }],
  password: [{ validator: validatePassword, trigger: 'blur' }],
  confirmPassword: [{ validator: validateConfirm, trigger: 'blur' }]
}

const handleLogin = async () => {
  if (!loginFormRef.value) return

  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const data = await authApi.loginData({
          username: loginForm.username,
          password: loginForm.password
        })

        const { token, userInfo } = data || {}

        setAuth({ token, userInfo, remember: loginForm.remember })

        ElMessage.success('登录成功!')
        router.push('/dashboard')
      } catch (error) {
        const msg = error?.response?.data?.message || error?.message || '登录失败，请稍后重试'
        ElMessage.error(String(msg))
      } finally {
        loading.value = false
      }
    }
  })
}

const handleRegister = async () => {
  if (!registerFormRef.value) return
  await registerFormRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await authApi.registerData({
        username: String(registerForm.username || '').trim(),
        password: registerForm.password
      })

      ElMessage.success('注册成功，请登录')
      loginForm.username = String(registerForm.username || '').trim()
      loginForm.password = ''
      registerForm.password = ''
      registerForm.confirmPassword = ''
      mode.value = 'login'
    } catch (error) {
      const msg = error?.response?.data?.message || error?.message || '注册失败，请稍后重试'
      ElMessage.error(String(msg))
    } finally {
      loading.value = false
    }
  })
}
</script>
