<script setup lang="ts">
import { computed, ref } from 'vue'
const props = defineProps<{
  source: string
  sourceName: string
  publishedAt?: string
  mode?: string
}>()
const checked = ref<string[]>([])
const sentences = computed(() =>
  props.source
    .split(/[。；\n]/)
    .map((s) => s.trim())
    .filter(Boolean),
)
const definitions = [
  { key: '资格', pattern: /面向|对象|年级|本科生/ },
  { key: '截止时间', pattern: /截止|截至|报名时间|前提交/ },
  { key: '材料', pattern: /提交|材料|携带/ },
  { key: '地点或入口', pattern: /地点|地址|提交至|报送至/ },
]
const rows = computed(() =>
  definitions.map((d) => ({ ...d, quotes: sentences.value.filter((s) => d.pattern.test(s)) })),
)
const missing = computed(() => rows.value.filter((r) => !r.quotes.length).map((r) => r.key))
const recognition = computed(() =>
  sentences.value.filter(
    (s) =>
      /(本院|学院).*(本学年|20[0-9]{2}).*(认定目录|测评细则)/.test(s) &&
      !/未提供|未写|不包含|不涉及|需核实/.test(s),
  ),
)
const sourceUrl = computed(() => props.sourceName.match(/https?:\/\/[^\s]+/)?.[0])
</script>
<template>
  <section class="evidence-panel">
    <header>
      <h2>原文核查台</h2>
      <small>{{ publishedAt ? '发布日期 ' + publishedAt : '发布日期需核实' }}</small
      ><a v-if="sourceUrl" :href="sourceUrl" target="_blank" rel="noopener">查看原站 ↗</a>
    </header>
    <p v-if="!source">载入一份通知，即可按原句核对资格、时间和材料。</p>
    <template v-else>
      <div class="warning" role="status">
        需核实
        {{
          [...missing, ...(!recognition.length ? ['本院本学年综测认定目录'] : [])].join('、') ||
          '通知是否仍为当期有效版本'
        }}。请携原文向学院辅导员或教务负责老师确认。
      </div>
      <div class="summary">
        <div v-for="row in rows" :key="row.key">
          <small>{{ row.key }}</small
          ><strong>{{ row.quotes.length ? row.quotes[0] : '需核实' }}</strong>
        </div>
      </div>
      <details v-for="row in rows" :key="row.key">
        <summary>{{ row.key }} · 原文依据（{{ row.quotes.length }}）</summary>
        <label v-for="quote in row.quotes" :key="quote"
          ><input v-model="checked" :value="quote" type="checkbox" />{{ quote }}</label
        >
        <p v-if="!row.quotes.length">原文未明确提供，请补充通知或附件。</p>
      </details>
      <p>已核对 {{ checked.length }} 项。当前面板仅呈现材料中的原句，适用性可向情境助手追问。</p>
    </template>
  </section>
</template>
<style scoped>
.evidence-panel {
  border: 1px solid var(--border-default);
  background: var(--surface-white);
  padding: 18px;
  border-radius: 10px;
}
.summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin: 14px 0;
}
.summary div {
  padding: 10px;
  background: var(--surface-subtle);
  border-radius: 6px;
  overflow-wrap: anywhere;
}
.summary small,
.summary strong {
  display: block;
}
.summary strong {
  font-size: 13px;
}
.warning {
  background: var(--status-warning-bg);
  color: var(--status-warning);
  padding: 12px;
  border-radius: 6px;
}
details {
  padding: 10px;
  border-bottom: 1px solid var(--border-default);
}
label {
  display: block;
  margin: 8px 0;
}
header {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}
h2 {
  font-size: 18px;
}
</style>
