<script setup lang="ts">
import { computed } from 'vue'
import { renderSafeMarkdown } from '@/utils/safeMarkdown'
const props = defineProps<{ content?: string | null }>()
const html = computed(() => renderSafeMarkdown(props.content || ''))
</script>
<template><div class="markdown-content" v-html="html" /></template>
<style scoped>
.markdown-content {
  color: var(--text-primary);
  line-height: 1.75;
  overflow-wrap: anywhere;
  min-width: 0;
}
.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3) {
  font-size: 1.1em;
  margin: 16px 0 8px;
  line-height: 1.5;
}
.markdown-content :deep(p) {
  margin: 6px 0;
  white-space: normal;
}
.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  padding-left: 1.5em;
  margin: 8px 0;
}
.markdown-content :deep(blockquote) {
  border-left: 3px solid var(--accent-primary);
  padding: 8px 14px;
  margin: 12px 0;
  background: var(--accent-soft);
}
.markdown-content :deep(code) {
  font-family: monospace;
  background: var(--bg-elevated);
  padding: 2px 5px;
  border-radius: 4px;
}
.markdown-content :deep(pre) {
  max-width: 100%;
  overflow: auto;
  padding: 12px;
  background: var(--bg-elevated);
  border-radius: 10px;
}
.markdown-content :deep(pre code) {
  padding: 0;
  white-space: pre;
}
.markdown-content :deep(table) {
  display: block;
  max-width: 100%;
  overflow: auto;
  border-collapse: collapse;
  margin: 10px 0;
}
.markdown-content :deep(td),
.markdown-content :deep(th) {
  padding: 7px 10px;
  border: 1px solid var(--border-default);
}
.markdown-content :deep(th) {
  background: var(--bg-elevated);
}
.markdown-content :deep(a) {
  color: var(--accent-primary);
  text-decoration: underline;
}
</style>
