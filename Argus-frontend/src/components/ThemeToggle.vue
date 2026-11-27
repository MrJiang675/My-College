<script setup lang="ts">
import { useTheme } from '@/composables/useTheme'

const { theme, toggleTheme } = useTheme()
</script>

<template>
  <button
    type="button"
    class="theme-toggle"
    :aria-label="theme === 'light' ? '切换到深色模式' : '切换到浅色模式'"
    :title="theme === 'light' ? '切换到深色模式' : '切换到浅色模式'"
    :aria-pressed="theme === 'dark'"
    @click="toggleTheme"
  >
    <svg v-if="theme === 'light'" width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="4" stroke="currentColor" stroke-width="1.7" />
      <path d="M12 2v2M12 20v2M4.93 4.93l1.42 1.42M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.42-1.42M17.66 6.34l1.41-1.41" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
    </svg>
    <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M20.5 14.2A8.5 8.5 0 0 1 9.8 3.5 8.5 8.5 0 1 0 20.5 14.2Z" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round" />
    </svg>
  </button>
</template>

<style scoped>
.theme-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  padding: 0;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-muted);
  opacity: 0.8;
  transition: color 0.2s ease, background 0.2s ease, opacity 0.2s ease;
}

.theme-toggle:hover {
  color: var(--brand-primary);
  background: var(--surface-subtle);
  opacity: 1;
}
</style>
