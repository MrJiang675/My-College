<script setup lang="ts">
import { computed } from 'vue'
import { moduleGuides, type ModuleGuideKey } from '@/constants/moduleGuides'

const props = defineProps<{
  guideKey: ModuleGuideKey
  disabled?: boolean
}>()
const emit = defineEmits<{ select: [text: string] }>()
const examples = computed(() => moduleGuides[props.guideKey].examples)
</script>

<template>
  <div v-if="examples.length" class="empty-examples">
    <p class="empty-examples__label">没头绪？点一个试试</p>
    <div class="empty-examples__grid">
      <button
        v-for="example in examples"
        :key="example.label"
        type="button"
        :disabled="disabled"
        @click="emit('select', example.text)"
      >
        <span>{{ example.label }}</span>
        <small>{{ example.text }}</small>
        <b aria-hidden="true">↗</b>
      </button>
    </div>
  </div>
</template>

<style scoped>
.empty-examples { width: 100%; margin-top: 24px; text-align: left; }
.empty-examples__label { margin: 0 0 10px; color: var(--text-muted); font-size: 0.78rem; font-weight: 600; }
.empty-examples__grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: 10px; }
.empty-examples button {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
  padding: 13px 34px 13px 14px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  background: var(--surface-white);
  color: var(--text-primary);
  text-align: left;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.18s ease;
}
.empty-examples button:hover:not(:disabled) { border-color: var(--brand-primary); background: var(--surface-accent); transform: translateY(-1px); }
.empty-examples button:disabled { opacity: 0.5; cursor: not-allowed; }
.empty-examples span { font-size: 0.8rem; font-weight: 700; }
.empty-examples small { display: -webkit-box; overflow: hidden; color: var(--text-secondary); font-size: 0.75rem; line-height: 1.5; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.empty-examples b { position: absolute; top: 12px; right: 13px; color: var(--brand-primary); font-size: 0.9rem; }
</style>
