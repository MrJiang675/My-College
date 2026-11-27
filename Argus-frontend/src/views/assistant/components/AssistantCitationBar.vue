<script setup lang="ts">
import { ref } from 'vue'
import { ElTooltip } from 'element-plus'
import type { AssistantCitationItem } from '@/types/assistant'

defineProps<{
  citations: AssistantCitationItem[]
}>()

const emit = defineEmits<{
  inspect: [citation: AssistantCitationItem]
}>()

const expanded = ref(false)
const citationTooltip = '展开可查看答案出自哪份讲义的哪一段，方便核对是原文结论还是待确认内容。'

function formatScore(score: number | null | undefined): string {
  if (typeof score !== 'number' || !Number.isFinite(score)) return ''
  return `相关度 ${(score * 100).toFixed(1)}%`
}
</script>

<template>
  <section class="citebar" aria-label="引用来源">
    <header class="citebar__head">
      <div class="citebar__title">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
          <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
        </svg>
        <strong>引用来源</strong>
        <span class="citebar__count">{{ citations.length }}</span>
      </div>

      <el-tooltip :content="citationTooltip" placement="top" :show-after="150">
        <button
          class="citebar__toggle"
          type="button"
          :aria-expanded="expanded"
          @click="expanded = !expanded"
        >
          <svg :class="{ 'is-open': expanded }" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <polyline points="6 9 12 15 18 9" />
          </svg>
          {{ expanded ? '收起资料原文' : '核对资料原文' }}
        </button>
      </el-tooltip>
    </header>

    <p v-if="!expanded" class="citebar__hint">共 {{ citations.length }} 份资料，展开核对答案依据。</p>

    <div v-else class="citebar__list">
      <details
        v-for="(c, idx) in citations"
        :key="`${c.documentId ?? 'x'}-${c.chunkId ?? idx}`"
        class="citebar__card"
      >
        <summary class="citebar__summary">
          <span class="citebar__index">{{ String(idx + 1).padStart(2, '0') }}</span>
          <span class="citebar__name" :title="c.fileName">{{ c.fileName }}</span>
          <span v-if="formatScore(c.score)" class="citebar__score">{{ formatScore(c.score) }}</span>
          <svg class="citebar__chevron" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <polyline points="6 9 12 15 18 9" />
          </svg>
        </summary>

        <div class="citebar__body">
          <p v-if="c.snippet" class="citebar__snippet">{{ c.snippet }}</p>
          <p v-else class="citebar__snippet is-muted">暂无片段摘要</p>
          <div class="citebar__foot">
            <span v-if="c.chunkIndex !== null" class="citebar__chunk">片段 #{{ c.chunkIndex }}</span>
            <button
              v-if="c.documentId !== null"
              class="citebar__inspect"
              type="button"
              @click="emit('inspect', c)"
            >
              查看原文
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <line x1="7" y1="17" x2="17" y2="7" />
                <polyline points="7 7 17 7 17 17" />
              </svg>
            </button>
            <span v-else class="citebar__unavailable">来源暂不可预览</span>
          </div>
        </div>
      </details>
    </div>
  </section>
</template>

<style scoped>
.citebar {
  min-width: 0;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--border-default);
  overflow-wrap: anywhere;
}

.citebar__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px 12px;
}

.citebar__title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  color: var(--text-secondary);
  font-size: 0.8rem;
}

.citebar__title svg,
.citebar__title strong {
  color: var(--brand-primary-dark);
}

.citebar__title strong {
  font-weight: 650;
}

.citebar__count {
  min-width: 20px;
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--surface-accent);
  color: var(--brand-primary-dark);
  font-size: 0.68rem;
  font-weight: 700;
  text-align: center;
}

.citebar__toggle {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 2px;
  border: 0;
  background: transparent;
  color: var(--brand-primary-dark);
  font-family: inherit;
  font-size: 0.76rem;
  font-weight: 600;
}

.citebar__toggle svg {
  transition: transform 0.18s ease;
}

.citebar__toggle svg.is-open {
  transform: rotate(180deg);
}

.citebar__toggle:hover {
  color: var(--brand-primary);
}

.citebar__hint {
  margin: 7px 0 0;
  color: var(--text-muted);
  font-size: 0.74rem;
}

.citebar__list {
  display: grid;
  gap: 7px;
  margin-top: 9px;
}

.citebar__card {
  min-width: 0;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  background: var(--surface-white);
  overflow: hidden;
}

.citebar__card[open] {
  border-color: rgba(74, 144, 217, 0.32);
}

.citebar__summary {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  cursor: pointer;
  list-style: none;
}

.citebar__summary::-webkit-details-marker {
  display: none;
}

.citebar__summary:hover {
  background: var(--surface-accent);
}

.citebar__index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 23px;
  height: 23px;
  border-radius: 6px;
  background: var(--surface-accent);
  color: var(--brand-primary-dark);
  font-size: 0.66rem;
  font-weight: 700;
}

.citebar__name {
  min-width: 0;
  color: var(--text-primary);
  font-size: 0.79rem;
  font-weight: 600;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.citebar__score {
  color: var(--brand-primary-dark);
  font-size: 0.7rem;
  font-weight: 600;
  white-space: nowrap;
}

.citebar__chevron {
  color: var(--text-muted);
  transition: transform 0.18s ease;
}

.citebar__card[open] .citebar__chevron {
  transform: rotate(180deg);
}

.citebar__body {
  min-width: 0;
  padding: 0 11px 10px 41px;
}

.citebar__snippet {
  margin: 0;
  padding: 9px 10px;
  border-radius: 6px;
  background: var(--surface-subtle);
  color: var(--text-secondary);
  font-size: 0.77rem;
  line-height: 1.65;
  white-space: pre-line;
  overflow-wrap: anywhere;
}

.citebar__snippet.is-muted {
  color: var(--text-muted);
}

.citebar__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 6px 10px;
  margin-top: 8px;
}

.citebar__chunk,
.citebar__unavailable {
  color: var(--text-muted);
  font-size: 0.69rem;
}

.citebar__inspect {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border: 1px solid rgba(74, 144, 217, 0.24);
  border-radius: 6px;
  background: var(--surface-white);
  color: var(--brand-primary-dark);
  font-family: inherit;
  font-size: 0.72rem;
  font-weight: 600;
}

.citebar__inspect:hover {
  border-color: var(--brand-primary);
  background: var(--surface-accent);
}

@media (max-width: 480px) {
  .citebar__summary {
    grid-template-columns: auto minmax(0, 1fr) auto;
  }

  .citebar__score {
    grid-column: 2;
  }

  .citebar__chevron {
    grid-column: 3;
    grid-row: 1 / span 2;
  }

  .citebar__body { padding-left: 11px; }
}
</style>
