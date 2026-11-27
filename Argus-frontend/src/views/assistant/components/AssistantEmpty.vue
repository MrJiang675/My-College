<script setup lang="ts">
import { computed } from 'vue'
import type { AssistantToolMode } from '@/types/assistant'
import EmptyExampleCards from '@/components/EmptyExampleCards.vue'

const props = defineProps<{
  mode: AssistantToolMode
  hasKb: boolean
}>()

const emit = defineEmits<{
  pick: [prompt: string, mode: AssistantToolMode]
}>()

const isKb = computed(() => props.mode === 'KB_SEARCH')
</script>

<template>
  <section class="aempty" aria-labelledby="assistant-empty-title">
    <div class="aempty__glow" aria-hidden="true" />

    <div class="aempty__inner">
      <div class="aempty__mark" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
          <path d="M8 8h8M8 12h5" />
        </svg>
      </div>

      <p class="aempty__eyebrow">独立多轮会话</p>
      <h2 id="assistant-empty-title" class="aempty__title">
        {{ isKb ? '围绕课程资料提问' : '把思路慢慢理清' }}
      </h2>
      <p class="aempty__subtitle">
        <template v-if="isKb">
          指定课程知识库后提问，回答会附上资料来源，便于核对原文。
        </template>
        <template v-else>
          自由讨论选题、概念或代码。会话会保存，可随时从左侧继续。
        </template>
      </p>

      <div v-if="isKb" class="aempty__kb-tip" :class="{ 'is-warning': !hasKb }">
        <span class="aempty__kb-dot" aria-hidden="true" />
        {{ hasKb ? '已在下方选定课程知识库，可开始提问或切换小组。' : '请先在下方输入框选择课程知识库。' }}
      </div>

      <div class="aempty__scope">
        <strong>本页用于独立会话</strong>
        <p>顶栏「问助手」会自动带当前任务上下文；本页可自由讨论或指定知识库。</p>
      </div>

      <EmptyExampleCards
        v-if="!isKb"
        guide-key="assistant"
        @select="(text) => emit('pick', text, 'CHAT')"
      />
    </div>
  </section>
</template>

<style scoped>
.aempty {
  flex: 1;
  display: flex;
  min-height: 0;
  padding: 32px;
  overflow-y: auto;
  position: relative;
  background: linear-gradient(180deg, var(--surface-accent), var(--surface-white) 72%);
}

.aempty__glow {
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

.aempty__inner {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  max-width: 720px;
  margin: auto;
  text-align: center;
}

.aempty__mark {
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

.aempty__mark svg {
  width: 23px;
  height: 23px;
}

.aempty__eyebrow {
  margin: 0 0 8px;
  color: var(--brand-primary);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.aempty__title {
  margin: 0;
  color: var(--text-primary);
  font-size: clamp(1.5rem, 2.4vw, 1.9rem);
  line-height: 1.3;
  overflow-wrap: anywhere;
}

.aempty__subtitle {
  max-width: 520px;
  margin: 10px 0 16px;
  color: var(--text-secondary);
  font-size: 0.9rem;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.aempty__kb-tip {
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
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.aempty__kb-dot {
  width: 6px;
  height: 6px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--brand-primary);
}

.aempty__kb-tip.is-warning {
  border-color: rgba(245, 158, 11, 0.25);
  background: var(--status-warning-bg);
  color: var(--status-warning);
}

.aempty__kb-tip.is-warning .aempty__kb-dot {
  background: var(--status-warning);
}

.aempty__scope {
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

.aempty__scope strong {
  flex-shrink: 0;
  color: var(--brand-primary-dark);
}

.aempty__scope p {
  margin: 0;
  color: var(--text-secondary);
}

.aempty :deep(.empty-examples button),
.aempty :deep(.empty-examples small) {
  min-width: 0;
  overflow-wrap: anywhere;
}

@media (max-width: 640px) {
  .aempty { padding: 24px 16px; }
  .aempty__scope { flex-direction: column; gap: 2px; }
  .aempty__kb-tip { border-radius: var(--radius-sm); }
}
</style>
