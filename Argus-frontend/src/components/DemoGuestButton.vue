<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import http, { extractApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { AuthSessionResponse } from '@/api/auth'
const router = useRouter()
const auth = useAuthStore()
const busy = ref(false)
const error = ref('')
async function enter() {
  busy.value = true
  error.value = ''
  try {
    const { data } = await http.post<{ data: AuthSessionResponse }>('/auth/demo-guest')
    auth.setSession(data.data.accessToken, data.data.currentUser)
    await router.push(auth.resolveLandingPath())
  } catch (cause) {
    error.value = extractApiError(cause, '体验入口暂时不可用')
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <div class="demo-guest">
    <button type="button" :disabled="busy" @click="enter">
      {{ busy ? '正在分配空间…' : '评委一键体验（独立空间，免注册）' }}
    </button>
    <p>将为你分配独立演示空间，与他人的操作互不干扰</p>
    <p v-if="error" role="alert">{{ error }}</p>
  </div>
</template>
<style scoped>
.demo-guest {
  margin: 16px 0;
}
.demo-guest button {
  width: 100%;
  padding: 12px;
  background: var(--brand-primary, #315743);
  color: white;
  border: 0;
  border-radius: 8px;
  cursor: pointer;
}
.demo-guest p {
  font-size: 12px;
  margin: 8px 0;
}
</style>
