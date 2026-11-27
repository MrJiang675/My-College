<script setup lang="ts">
import { ref } from 'vue'
import { ElTooltip } from 'element-plus'
import type { CitationItem } from '@/api/qa'

defineProps<{
  citations: CitationItem[]
}>()

const emit = defineEmits<{
  inspect: [citation: CitationItem]
}>()

const expanded = ref(false)
const citationTooltip = '展开可查看答案出自哪份讲义的哪一段，方便核对是原文结论还是待确认内容。'

function formatScore(score: number | null | undefined): string {
  if (typeof score !== 'number' || !Number.isFinite(score)) return ''
  return `相关度 ${(score * 100).toFixed(1)}%`
}
</script>

<template>
  <section class="citation-rail" aria-label="引用来源">
    <header class="citation-rail__head">
      <div class="citation-rail__title">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
          <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
        </svg>
        <strong>引用来源</strong>
        <span class="citation-rail__count">{{ citations.length }}</span>
      </div>

      <el-tooltip :content="citationTooltip" placement="top" :show-after="150">
        <button
          class="citation-rail__toggle"
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

    <p v-if="!expanded" class="citation-rail__hint">共 {{ citations.length }} 份资料，展开核对答案依据。</p>

    <div v-else class="citation-rail__list">
      <details
        v-for="(cite, idx) in citations"
        :key="`${cite.documentId ?? 'x'}-${cite.chunkId ?? idx}`"
        class="citation-rail__card"
      >
        <summary class="citation-rail__summary">
          <span class="citation-rail__index">{{ String(idx + 1).padStart(2, '0') }}</span>
          <span class="citation-rail__filename" :title="cite.fileName">{{ cite.fileName }}</span>
          <span v-if="formatScore(cite.score)" class="citation-rail__score">{{ formatScore(cite.score) }}</span>
          <svg class="citation-rail__chevron" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <polyline points="6 9 12 15 18 9" />
          </svg>
        </summary>

        <div class="citation-rail__body">
          <p v-if="cite.snippet" class="citation-rail__snippet">{{ cite.snippet }}</p>
          <p v-else class="citation-rail__snippet is-muted">暂无片段摘要</p>
          <div class="citation-rail__foot">
            <span v-if="cite.chunkIndex !== null" class="citation-rail__chunk">片段 #{{ cite.chunkIndex }}</span>
            <button
              v-if="cite.documentId !== null"
              class="citation-rail__inspect"
              type="button"
              @click="emit('inspect', cite)"
            >
              查看原文
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <line x1="7" y1="17" x2="17" y2="7" />
                <polyline points="7 7 17 7 17 17" />
              </svg>
            </button>
            <span v-else class="citation-rail__unavailable">来源暂不可预览</span>
          </div>
        </div>
      </details>
    </div>
  </section>
</template>

<style scoped>
.citation-rail {
  min-width: 0;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--border-default);
  overflow-wrap: anywhere;
}

.citation-rail__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px 12px;
}

.citation-rail__title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  color: var(--text-secondary);
  font-size: 0.8rem;
}

.citation-rail__title svg,
.citation-rail__title strong {
  color: var(--brand-primary-dark);
}

.citation-rail__title strong {
  font-weight: 650;
}

.citation-rail__count {
  min-width: 20px;
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--surface-accent);
  color: var(--brand-primary-dark);
  font-size: 0.68rem;
  font-weight: 700;
  text-align: center;
}

.citation-rail__toggle {
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

.citation-rail__toggle svg {
  transition: transform 0.18s ease;
}

.citation-rail__toggle svg.is-open {
  transform: rotate(180deg);
}

.citation-rail__toggle:hover {
  color: var(--brand-primary);
}

.citation-rail__hint {
  margin: 7px 0 0;
  color: var(--text-muted);
  font-size: 0.74rem;
}

.citation-rail__list {
  display: grid;
  gap: 7px;
  margin-top: 9px;
}

.citation-rail__card {
  min-width: 0;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  background: var(--surface-white);
  overflow: hidden;
}

.citation-rail__card[open] {
  border-color: rgba(74, 144, 217, 0.32);
}

.citation-rail__summary {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  cursor: pointer;
  list-style: none;
}

.citation-rail__summary::-webkit-details-marker {
  display: none;
}

.citation-rail__summary:hover {
  background: var(--surface-accent);
}

.citation-rail__index {
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

.citation-rail__filename {
  min-width: 0;
  color: var(--text-primary);
  font-size: 0.79rem;
  font-weight: 600;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.citation-rail__score {
  color: var(--brand-primary-dark);
  font-size: 0.7rem;
  font-weight: 600;
  white-space: nowrap;
}

.citation-rail__chevron {
  color: var(--text-muted);
  transition: transform 0.18s ease;
}

.citation-rail__card[open] .citation-rail__chevron {
  transform: rotate(180deg);
}

.citation-rail__body {
  min-width: 0;
  padding: 0 11px 10px 41px;
}

.citation-rail__snippet {
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

.citation-rail__snippet.is-muted {
  color: var(--text-muted);
}

.citation-rail__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 6px 10px;
  margin-top: 8px;
}

.citation-rail__chunk,
.citation-rail__unavailable {
  color: var(--text-muted);
  font-size: 0.69rem;
}

.citation-rail__inspect {
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

.citation-rail__inspect:hover {
  border-color: var(--brand-primary);
  background: var(--surface-accent);
}

@media (max-width: 480px) {
  .citation-rail__summary {
    grid-template-columns: auto minmax(0, 1fr) auto;
  }

  .citation-rail__score {
    grid-column: 2;
  }

  .citation-rail__chevron {
    grid-column: 3;
    grid-row: 1 / span 2;
  }

  .citation-rail__body { padding-left: 11px; }
}
</style>
