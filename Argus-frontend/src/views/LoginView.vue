<script setup lang="ts">
import { ref, reactive, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { extractApiError } from '@/api/http'
import type { LoginPayload } from '@/api/auth'
import ThemeToggle from '@/components/ThemeToggle.vue'

const router = useRouter()
const authStore = useAuthStore()

const form = reactive<LoginPayload>({
  loginId: '',
  password: '',
})
const experienceForm = reactive({
  username: '',
})
const showExperience = ref(false)
const passwordInput = ref<HTMLInputElement | null>(null)
const experienceInput = ref<HTMLInputElement | null>(null)

const loading = ref(false)
const errorMsg = ref('')
const experienceError = ref('')

function openExperienceForm() {
  showExperience.value = true
  errorMsg.value = ''
  experienceError.value = ''
  nextTick(() => experienceInput.value?.focus())
}

async function handleLogin() {
  if (!form.loginId.trim() || !form.password?.trim()) {
    errorMsg.value = '请输入登录标识和密码'
    return
  }
  errorMsg.value = ''
  loading.value = true
  try {
    await authStore.login({
      loginId: form.loginId.trim(),
      password: form.password,
    })
    router.push(authStore.resolveLandingPath())
  } catch (err) {
    errorMsg.value = extractApiError(err, '登录失败')
  } finally {
    loading.value = false
  }
}

async function handleExperienceLogin() {
  if (!experienceForm.username.trim()) {
    experienceError.value = '请输入用户名'
    return
  }

  const username = experienceForm.username.trim()
  experienceError.value = ''
  loading.value = true
  try {
    await authStore.login({ loginId: username })
    router.push(authStore.resolveLandingPath())
  } catch (err) {
    const message = extractApiError(err, '登录失败')
    if (message === '该账号需要密码登录') {
      form.loginId = username
      form.password = ''
      showExperience.value = false
      errorMsg.value = '该账号需要密码，请在登录表单中输入密码'
      await nextTick()
      passwordInput.value?.focus()
    } else {
      experienceError.value = message
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-theme-toggle">
      <ThemeToggle />
    </div>
    <div class="login-card">
      <a href="/" class="login-logo">
        <svg width="32" height="32" viewBox="0 0 32 32" fill="none">
          <defs>
            <linearGradient id="loginPageGrad" x1="2" y1="2" x2="30" y2="30"><stop stop-color="#4A90D9"/><stop offset="1" stop-color="#5CC9C1"/></linearGradient>
            <path id="lpPetal" d="M16 3C17.5 6 20 9 22.5 10C20 11 17.5 11.5 16 13C14.5 11.5 12 11 9.5 10C12 9 14.5 6 16 3Z" fill="url(#loginPageGrad)" opacity="0.7"/>
          </defs>
          <use href="#lpPetal"/>
          <use href="#lpPetal" transform="rotate(90 16 16)"/>
          <use href="#lpPetal" transform="rotate(180 16 16)"/>
          <use href="#lpPetal" transform="rotate(270 16 16)"/>
          <circle cx="16" cy="16" r="5.5" fill="url(#loginPageGrad)"/>
        </svg>
        <span>数据洞察中心</span>
      </a>

      <h1>登录</h1>
      <p class="subtitle">使用您的账号登录数据洞察中心</p>

      <form class="login-form" @submit.prevent="handleLogin">
        <div class="input-group">
          <label>登录标识</label>
          <input
            v-model="form.loginId"
            type="text"
            placeholder="用户名或邮箱"
            autocomplete="username"
          />
        </div>

        <div class="input-group">
          <label>密码</label>
          <input
            ref="passwordInput"
            v-model="form.password"
            type="password"
            placeholder="输入密码"
            autocomplete="current-password"
          />
        </div>

        <p v-if="errorMsg" class="form-error">{{ errorMsg }}</p>

        <button type="submit" class="btn-login" :disabled="loading">
          <span v-if="!loading">登录</span>
          <span v-else>登录中...</span>
        </button>
      </form>

      <button type="button" class="btn-experience" :disabled="loading" @click="openExperienceForm">
        体验用户
      </button>

      <form v-if="showExperience" class="login-form experience-form" @submit.prevent="handleExperienceLogin">
        <div class="input-group">
          <label>体验用户名</label>
          <input
            ref="experienceInput"
            v-model="experienceForm.username"
            type="text"
            placeholder="输入一个用户名"
            autocomplete="username"
          />
        </div>
        <p class="experience-hint">无需注册和密码，输入用户名即可直接进入。</p>
        <p v-if="experienceError" class="form-error">{{ experienceError }}</p>
        <button type="submit" class="btn-login" :disabled="loading">
          <span v-if="!loading">进入体验</span>
          <span v-else>登录中...</span>
        </button>
      </form>

      <p class="form-footer">体验账号会自动创建，管理员账号仍需密码验证。</p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--hero-background);
  padding: 40px 24px;
}

.login-theme-toggle {
  position: fixed;
  top: 20px;
  right: 24px;
  z-index: 10;
}

.login-card {
  width: 100%;
  max-width: 420px;
  background: var(--surface-white);
  border-radius: var(--radius-lg);
  padding: 44px 40px;
  box-shadow: var(--shadow-xl);
  border: 1px solid var(--border-default);
}

.login-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-weight: 700;
  font-size: 17px;
  color: var(--text-primary);
  margin-bottom: 28px;
}

.login-card h1 {
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-size: 24px;
  font-weight: 800;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.subtitle {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 28px;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.input-group {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.input-group label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.input-group input {
  padding: 11px 14px;
  border: 1.5px solid var(--border-default);
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-family: inherit;
  color: var(--text-primary);
  background: var(--surface-white);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
  outline: none;
}

.input-group input::placeholder {
  color: var(--text-muted);
}

.input-group input:focus {
  border-color: var(--brand-primary);
  box-shadow: 0 0 0 3px rgba(74, 144, 217, 0.1);
}

.form-error {
  font-size: 13px;
  color: var(--el-color-danger);
  margin: 0;
}

.btn-login {
  padding: 12px 24px;
  border-radius: var(--radius-sm);
  background: linear-gradient(135deg, var(--brand-primary), var(--brand-primary-dark));
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  transition: all 0.2s ease;
  box-shadow: 0 4px 14px rgba(74, 144, 217, 0.25);
  margin-top: 4px;
}

.btn-login:hover:not(:disabled) {
  box-shadow: 0 6px 20px rgba(74, 144, 217, 0.35);
  transform: translateY(-1px);
}

.btn-login:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-experience {
  width: 100%;
  margin-top: 12px;
  padding: 11px 24px;
  border: 1.5px solid rgba(74, 144, 217, 0.35);
  border-radius: var(--radius-sm);
  background: var(--surface-accent);
  color: var(--brand-primary-dark);
  font-size: 14px;
  font-weight: 600;
  transition: all 0.2s ease;
}

.btn-experience:hover:not(:disabled) {
  border-color: var(--brand-primary);
  background: var(--surface-accent-strong);
}

.btn-experience:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.experience-form {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px dashed var(--border-default);
}

.experience-hint {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.form-footer {
  margin-top: 24px;
  text-align: center;
  font-size: 14px;
  color: var(--text-secondary);
}

</style>
