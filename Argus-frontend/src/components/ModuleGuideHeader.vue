<script setup lang="ts">
import { computed } from 'vue'
import { moduleGuides, type ModuleGuideKey } from '@/constants/moduleGuides'
import ModuleGuidePopover from './ModuleGuidePopover.vue'

const props = defineProps<{
  guideKey: ModuleGuideKey
  eyebrow?: string
}>()

const guide = computed(() => moduleGuides[props.guideKey])
</script>

<template>
  <header class="module-guide-header">
    <div class="module-guide-header__copy">
      <p v-if="eyebrow" class="module-guide-header__eyebrow">{{ eyebrow }}</p>
      <h1>{{ guide.title }}</h1>
      <p class="module-guide-header__subtitle">{{ guide.subtitle }}</p>
    </div>
    <div class="module-guide-header__actions">
      <slot name="actions" />
      <ModuleGuidePopover :guide-key="guideKey" />
    </div>
  </header>
</template>

<style scoped>
.module-guide-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 24px;
}

.module-guide-header__copy { min-width: 0; }
.module-guide-header__eyebrow {
  margin: 0 0 5px;
  color: var(--brand-primary);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}
.module-guide-header h1 {
  margin: 0;
  color: var(--text-primary);
  font-size: clamp(1.55rem, 2.2vw, 2rem);
  line-height: 1.25;
}
.module-guide-header__subtitle {
  margin: 7px 0 0;
  color: var(--text-secondary);
  font-size: 0.92rem;
  line-height: 1.65;
}
.module-guide-header__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;
  flex-shrink: 0;
}

@media (max-width: 640px) {
  .module-guide-header { flex-direction: column; gap: 14px; }
  .module-guide-header__actions { width: 100%; justify-content: flex-start; }
}
</style>
