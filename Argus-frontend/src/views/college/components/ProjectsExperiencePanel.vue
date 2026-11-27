<script setup lang="ts">
import { computed, ref } from 'vue'
type Entry = {
  id: string
  project: string
  text: string
  source: string
  type?: string
  team?: string
  tags?: string[]
}
const props = defineProps<{ mod: string; entries: Entry[] }>()
const filter = ref('全部')
const visible = computed(() =>
  props.entries.filter((e) => filter.value === '全部' || e.type === filter.value),
)
const tags = computed(() => new Set(props.entries.flatMap((e) => e.tags || [])).size)
</script>
<template>
  <section v-if="['experience', 'project'].includes(mod)" class="entry-list panel">
    <h2>我的经历</h2>
    <p>
      {{ entries.length }} 项经历 · {{ tags }} 类能力 ·
      {{ entries.filter((e) => e.source?.trim() && !e.id.startsWith('sample-')).length }}
      项填写了证明位置
    </p>
    <select v-model="filter" aria-label="经历类型">
      <option v-for="v in ['全部', '课程设计', '竞赛', '科研', '实践']" :key="v">{{ v }}</option>
    </select>
    <p v-if="!visible.length">当前类型还没有记录，可从项目进展中确认收录。</p>
    <article v-for="entry in visible" :key="entry.id">
      <b>{{ entry.project }}</b
      ><small>{{ entry.type || '实践' }}</small>
      <p>个人负责 · {{ entry.text }}</p>
      <p v-if="entry.team">团队背景 · {{ entry.team }}</p>
      <small>成果位置 · {{ entry.source || '待补充' }}</small>
      <p>{{ entry.tags?.join(' · ') }}</p>
    </article>
  </section>
</template>
<style scoped src="../college.css"></style>
