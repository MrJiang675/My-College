<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ source: string; name: string; group?: number }>()
const outline = computed(() =>
  props.source
    .split('\n')
    .filter((l) => /^#{1,4}\s/.test(l))
    .map((l) => l.replace(/^#+\s*/, '')),
)
const assumptions = computed(() =>
  props.source
    .split(/[。\n]/)
    .filter((l) => /前提|条件|连续|可导|假设|忽略|独立/.test(l))
    .slice(0, 6),
)
</script>
<template>
  <section class="panel context-card">
    <h2>学习上下文检查器</h2>
    <p>{{ name || '尚未载入材料' }}{{ group ? ' · 课程库 ' + group : '' }}</p>
    <h3>材料大纲</h3>
    <ul v-if="outline.length">
      <li v-for="line in outline" :key="line">{{ line }}</li>
    </ul>
    <p v-else>载入讲义或选择课程知识库，检索结果会随回答显示。</p>
    <h3>核心前提与易错假设</h3>
    <details v-for="line in assumptions" :key="line">
      <summary>原文条件</summary>
      <p>{{ line }}</p>
    </details>
    <p v-if="!assumptions.length">当前材料尚未提供明确前提，请向助手追问并核对原文。</p>
  </section>
</template>
<style scoped src="../college.css"></style>
