<script setup lang="ts">
import { ref } from 'vue'
import http, { extractApiError } from '@/api/http'
type Notice = { id: string; title: string; url: string }
defineProps<{ mod: string; notices: Notice[] }>()
const emit = defineEmits<{
  load: []
  use: [notice: Notice]
  fetched: [body: string, name: string]
}>()
const url = ref(''),
  busy = ref(false),
  message = ref(''),
  expanded = ref(false)
async function fetchUrl() {
  busy.value = true
  message.value = ''
  try {
    const { data } = await http.post(
      '/college/notices/fetch-url',
      { url: url.value },
      { timeout: 11000 },
    )
    const p = data.data
    emit('fetched', p.body, p.title + ' · ' + p.url + ' · ' + p.publishedAt)
    if (p.body.length > 16000)
      message.value = '正文已提取，本轮分析截取前 16000 字，请检查原网页和附件。'
  } catch (e) {
    message.value = extractApiError(e)
  } finally {
    busy.value = false
  }
}
function example() {
  url.value = 'https://www.cs.tsinghua.edu.cn/index/tzgg.htm'
  expanded.value = true
  emit('load')
}
</script>
<template>
  <section v-if="mod === 'opps'" class="source-panel panel" style="padding: 18px">
    <h2>公共来源</h2>
    <p>公开网页由后端提取；需校内网登录的内容请复制正文或贴截图。</p>
    <form @submit.prevent="fetchUrl" class="source-panel__actions">
      <input
        v-model="url"
        type="url"
        required
        placeholder="官网通知链接（http / https）"
        aria-label="官网通知链接"
        style="flex: 1; min-width: 180px"
      /><button class="btn-primary" :disabled="busy">{{ busy ? '正在抓取…' : '抓取正文' }}</button>
    </form>
    <button type="button" class="btn-secondary" @click="example">
      评委示例：一键填入清华大学计算机系公开公告
    </button>
    <ul v-if="expanded && notices.length" class="notice-list">
      <li v-for="n in notices" :key="n.id">
        <a :href="n.url" target="_blank" rel="noopener">{{ n.title }}</a
        ><button @click="emit('use', n)">引用正文</button>
      </li>
    </ul>
    <p v-if="message" role="status">{{ message }}</p>
  </section>
</template>
<style scoped src="../college.css"></style>
