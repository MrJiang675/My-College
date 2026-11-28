<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import QaView from './QaView.vue'
import AssistantView from '../assistant/AssistantView.vue'
const route = useRoute(),
  router = useRouter()
const mode = computed(() => (route.query.mode === 'assistant' ? 'assistant' : 'knowledge'))
function select(value: string) {
  router.replace({ path: '/app/intelligent', query: { mode: value } })
}
</script>
<template>
  <section class="unified-qa">
    <nav aria-label="智能问答模式" class="mode-tabs">
      <button :aria-pressed="mode === 'knowledge'" @click="select('knowledge')">
        知识库精准问答
      </button>
      <button :aria-pressed="mode === 'assistant'" @click="select('assistant')">AI 对话助手</button>
    </nav>
    <KeepAlive><component :is="mode === 'knowledge' ? QaView : AssistantView" /></KeepAlive>
  </section>
</template>
<style scoped>
.mode-tabs {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 18px;
}
.mode-tabs button {
  padding: 10px 16px;
  border-radius: 12px;
  border: 1px solid var(--border-default);
  color: var(--text-secondary);
  background: var(--bg-surface);
  cursor: pointer;
}
.mode-tabs button[aria-pressed='true'] {
  background: var(--accent-soft);
  color: var(--accent-primary);
}
</style>
