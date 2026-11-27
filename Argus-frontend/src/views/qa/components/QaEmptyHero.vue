<script setup lang="ts">
import EmptyExampleCards from '@/components/EmptyExampleCards.vue'

defineProps<{
  groupName: string
  hasGroup: boolean
}>()

const emit = defineEmits<{
  pick: [prompt: string]
}>()
</script>

<template>
  <section class="qa-empty" aria-labelledby="qa-empty-title">
    <div class="qa-empty__glow" aria-hidden="true" />

    <div class="qa-empty__inner">
      <div class="qa-empty__mark" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
          <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
          <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
          <path d="M8 7h8M8 11h6" />
        </svg>
      </div>

      <p class="qa-empty__eyebrow">课程资料 · 可追溯问答</p>
      <h2 id="qa-empty-title" class="qa-empty__title">从课程讲义里找答案</h2>
      <p class="qa-empty__subtitle">
        选择课程小组，提问概念、推导或资料对比。回答附带引用，方便回看原文。
      </p>

      <div class="qa-empty__context" :class="{ 'is-warning': !hasGroup }">
        <span class="qa-empty__context-dot" aria-hidden="true" />
        <template v-if="hasGroup">
          当前课程知识库：<strong>{{ groupName || '已选课程小组' }}</strong>
        </template>
        <template v-else>请先在左侧选择课程小组</template>
      </div>

      <div class="qa-empty__scope">
        <strong>完整课程知识库在此</strong>
        <p>短材料追问请到「我的大学 → 学习助手」。</p>
      </div>

      <EmptyExampleCards
        guide-key="qa"
        :disabled="!hasGroup"
        @select="(text) => emit('pick', text)"
      />
    </div>
  </section>
</template>

<style scoped>
.qa-empty {
  flex: 1;
  display: flex;
  min-height: 0;
  padding: 32px;
  overflow-y: auto;
  position: relative;
  background: linear-gradient(180deg, var(--surface-accent), var(--surface-white) 72%);
}

.qa-empty__glow {
  position: absolute;
  top: -110px;
  left: 50%;
  width: 360px;
  height: 240px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(74, 144, 217, 0.13), transparent 70%);
  transform: translateX(-50%);
  pointer-events: none;
}

.qa-empty__inner {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  max-width: 720px;
  margin: auto;
  text-align: center;
}

.qa-empty__mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  margin-bottom: 14px;
  border: 1px solid rgba(74, 144, 217, 0.18);
  border-radius: 14px;
  background: var(--surface-white);
  color: var(--brand-primary);
  box-shadow: var(--shadow-sm);
}

.qa-empty__mark svg {
  width: 23px;
  height: 23px;
}

.qa-empty__eyebrow {
  margin: 0 0 8px;
  color: var(--brand-primary);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.qa-empty__title {
  margin: 0;
  color: var(--text-primary);
  font-size: clamp(1.5rem, 2.4vw, 1.9rem);
  line-height: 1.3;
  overflow-wrap: anywhere;
}

.qa-empty__subtitle {
  max-width: 520px;
  margin: 10px 0 16px;
  color: var(--text-secondary);
  font-size: 0.9rem;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.qa-empty__context {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  max-width: 100%;
  padding: 6px 12px;
  border: 1px solid rgba(74, 144, 217, 0.2);
  border-radius: 999px;
  background: var(--surface-white);
  color: var(--text-secondary);
  font-size: 0.78rem;
  overflow-wrap: anywhere;
}

.qa-empty__context strong {
  color: var(--text-primary);
  font-weight: 650;
}

.qa-empty__context-dot {
  width: 6px;
  height: 6px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--brand-primary);
}

.qa-empty__context.is-warning {
  border-color: rgba(245, 158, 11, 0.25);
  background: var(--status-warning-bg);
  color: var(--status-warning);
}

.qa-empty__context.is-warning .qa-empty__context-dot {
  background: var(--status-warning);
}

.qa-empty__scope {
  display: flex;
  align-items: baseline;
  gap: 9px;
  max-width: 100%;
  margin-top: 14px;
  padding: 10px 13px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.78);
  text-align: left;
  font-size: 0.78rem;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.qa-empty__scope strong {
  flex-shrink: 0;
  color: var(--brand-primary-dark);
}

.qa-empty__scope p {
  margin: 0;
  color: var(--text-secondary);
}

.qa-empty :deep(.empty-examples button),
.qa-empty :deep(.empty-examples small) {
  min-width: 0;
  overflow-wrap: anywhere;
}

@media (max-width: 640px) {
  .qa-empty { padding: 24px 16px; }
  .qa-empty__scope { flex-direction: column; gap: 2px; }
  .qa-empty__context { border-radius: var(--radius-sm); }
}
</style>
