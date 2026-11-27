<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{
  requirements: string
  entries: { id: string; project: string; text: string; source: string }[]
  selected?: string[]
}>()
const draft = defineModel<string>('draft')
const statuses = defineModel<Record<string, string>>('statuses', { default: () => ({}) })
const rows = computed(() =>
  props.requirements
    .split(/[。；\n]/)
    .map((v) => v.trim())
    .filter(Boolean)
    .slice(0, 20)
    .map((requirement) => {
      const keywords = requirement.split(/[，、,\s]+/).filter((v) => v.length >= 2)
      const matching = props.entries.filter(
        (e) =>
          props.selected?.includes(e.id) &&
          keywords.some((k) => (e.text + ' ' + e.project).includes(k)),
      )
      return { requirement, matching }
    }),
)
function exportDraft() {
  const blob = new Blob([draft.value || ''], { type: 'text/plain;charset=utf-8' }),
    url = URL.createObjectURL(blob),
    a = document.createElement('a')
  a.href = url
  a.download = 'argus-application-draft.txt'
  a.click()
  URL.revokeObjectURL(url)
}
</script>
<template>
  <section class="matrix panel">
    <h2>申请要求逐项对照</h2>
    <p>下表按原文词语查找已选经历，证明强度由本人核对。</p>
    <p v-if="!rows.length">载入岗位或院校要求后，逐项对照经历。</p>
    <article v-for="row in rows" :key="row.requirement">
      <b>{{ row.requirement }}</b
      ><select
        :value="statuses[row.requirement] || (row.matching.length ? '证据偏弱' : '缺失佐证')"
        @change="
          statuses = { ...statuses, [row.requirement]: ($event.target as HTMLSelectElement).value }
        "
      >
        <option>已证明</option>
        <option>证据偏弱</option>
        <option>缺失佐证</option>
      </select>
      <p>
        {{
          row.matching
            .map((e) => e.project + ' · ' + (e.source || '请补充报告或作品'))
            .join('；') || '请勾选相关经历并补充可核实材料。'
        }}
      </p>
    </article>
    <label
      >申请陈述草稿<textarea
        v-model="draft"
        placeholder="核对助手建议后，在这里编辑最终陈述"
      ></textarea></label
    ><button @click="exportDraft">导出草稿</button>
  </section>
</template>
<style scoped>
.matrix {
  padding: 18px;
}
.matrix article {
  padding: 12px 0;
  border-bottom: 1px solid var(--border-default);
}
select {
  display: block;
  margin: 8px 0;
}
textarea {
  width: 100%;
  min-height: 150px;
  background: var(--surface-subtle);
  color: var(--text-primary);
  border: 1px solid var(--border-default);
  border-radius: 6px;
}
button,
select {
  padding: 7px;
  background: var(--surface-white);
  color: var(--text-primary);
  border: 1px solid var(--border-default);
  border-radius: 6px;
}
</style>
