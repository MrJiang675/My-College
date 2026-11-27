<script setup lang="ts">
import DemoGuestButton from "@/components/DemoGuestButton.vue"
import { ref, reactive, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { extractApiError } from '@/api/http'
import type { LoginPayload } from '@/api/auth'

const props = defineProps<{
  visible: boolean
  initialExperience?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}>()

const router = useRouter()
const authStore = useAuthStore()

const loginForm = reactive<LoginPayload>({
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

watch(() => props.visible, async (val) => {
  if (val) {
    showExperience.value = props.initialExperience ?? false
    if (showExperience.value) {
      await nextTick()
      experienceInput.value?.focus()
    }
  } else {
    errorMsg.value = ''
    experienceError.value = ''
    loginForm.loginId = ''
    loginForm.password = ''
    experienceForm.username = ''
    showExperience.value = false
  }
})

async function handleLogin() {
  if (!loginForm.loginId.trim() || !loginForm.password?.trim()) {
    errorMsg.value = '请输入登录标识和密码'
    return
  }
  errorMsg.value = ''
  loading.value = true
  try {
    await authStore.login({
      loginId: loginForm.loginId.trim(),
      password: loginForm.password,
    })
    emit('update:visible', false)
    emit('success')
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
    emit('update:visible', false)
    emit('success')
    router.push(authStore.resolveLandingPath())
  } catch (err) {
    const message = extractApiError(err, '登录失败')
    if (message === '该账号需要密码登录') {
      loginForm.loginId = username
      loginForm.password = ''
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
  <el-dialog
    :model-value="visible"
    :show-close="true"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    width="420px"
    top="15vh"
    @update:model-value="(val: boolean) => emit('update:visible', val)"
  >
    <template #header>
      <div class="modal-header">
        <h2>欢迎回来</h2>
        <p>登录Argus 学生工作台</p>
      </div>
    </template>

    <DemoGuestButton />
    <form class="modal-form" @submit.prevent="handleLogin">
      <div class="input-group">
        <label>登录标识</label>
        <input
          v-model="loginForm.loginId"
          type="text"
          placeholder="用户名或邮箱"
          autocomplete="username"
        />
      </div>
      <div class="input-group">
        <label>密码</label>
        <input
          ref="passwordInput"
          v-model="loginForm.password"
          type="password"
          placeholder="输入密码"
          autocomplete="current-password"
        />
      </div>
      <p v-if="errorMsg" class="form-error">{{ errorMsg }}</p>
      <button type="submit" class="btn-submit" :disabled="loading">
        <span v-if="!loading">登录</span>
        <span v-else>登录中...</span>
      </button>
    </form>

    <button type="button" class="btn-experience" :disabled="loading" @click="openExperienceForm">
      体验用户
    </button>

    <form v-if="showExperience" class="modal-form experience-form" @submit.prevent="handleExperienceLogin">
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
      <button type="submit" class="btn-submit" :disabled="loading">
        <span v-if="!loading">进入体验</span>
        <span v-else>登录中...</span>
      </button>
    </form>
  </el-dialog>
</template>

<style scoped>
/* Header */
.modal-header {
  text-align: center;
  padding-top: 8px;
}
.modal-header h2 {
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 4px;
}
.modal-header p {
  font-size: 13.5px;
  color: var(--text-secondary);
  margin: 0;
}

/* Experience entry */
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
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px dashed var(--border-default);
}
.experience-hint {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

/* Form */
.modal-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
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
  padding: 10px 13px;
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

.btn-submit {
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
.btn-submit:hover:not(:disabled) {
  box-shadow: 0 6px 20px rgba(74, 144, 217, 0.35);
  transform: translateY(-1px);
}
.btn-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
