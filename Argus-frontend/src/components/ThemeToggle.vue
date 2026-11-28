<script setup lang="ts">
import { useTheme } from '@/composables/useTheme'
const { theme, toggleTheme } = useTheme()
</script>
<template>
  <div class="theme-segment" role="group" aria-label="外观主题">
    <button
      :aria-pressed="theme === 'light'"
      aria-label="切换到浅色模式"
      @click="theme !== 'light' && toggleTheme()"
    >
      ☀ 浅色</button
    ><button
      :aria-pressed="theme === 'dark'"
      aria-label="切换到深色模式"
      @click="theme !== 'dark' && toggleTheme()"
    >
      ☾ 深色
    </button>
  </div>
</template>
<style scoped>
.theme-segment {
  display: inline-flex;
  padding: 3px;
  gap: 2px;
  border: 1px solid var(--border-default);
  border-radius: 12px;
  background: var(--surface-muted);
  flex-shrink: 0;
  box-shadow: var(--shadow-xs);
}
button {
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  border-radius: 8px;
  font-size: 12px;
  padding: 7px 10px;
  white-space: nowrap;
  cursor: pointer;
  transition:
    background 0.2s,
    color 0.2s;
}
button[aria-pressed='true'] {
  background: var(--surface-white);
  color: var(--brand-primary);
  box-shadow: var(--shadow-sm);
}
</style>
