<script setup lang="ts">
import { computed } from 'vue'
import { ElPopover } from 'element-plus'
import { moduleGuides, type ModuleGuideKey } from '@/constants/moduleGuides'

const props = defineProps<{ guideKey: ModuleGuideKey }>()
const guide = computed(() => moduleGuides[props.guideKey])
</script>

<template>
  <el-popover placement="bottom-end" :width="360" trigger="click" popper-class="module-guide-popover">
    <template #reference>
      <button type="button" class="module-guide-trigger" aria-label="查看使用说明">
        <span aria-hidden="true">?</span>
        使用说明
      </button>
    </template>
    <div class="module-guide-content">
      <h2>怎么用</h2>
      <ol class="module-guide-steps">
        <li v-for="(step, index) in guide.steps" :key="step">
          <span>{{ index + 1 }}</span>
          <p>{{ step }}</p>
        </li>
      </ol>
      <div v-if="guide.example" class="module-guide-example">
        <strong>可以这样问</strong>
        <p>{{ guide.example }}</p>
      </div>
      <div class="module-guide-boundaries">
        <strong>能力边界</strong>
        <p v-for="item in guide.boundaries" :key="item">{{ item }}</p>
      </div>
    </div>
  </el-popover>
</template>

<style scoped>
.module-guide-trigger {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 36px;
  padding: 7px 12px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  background: var(--surface-white);
  color: var(--text-secondary);
  font-size: 0.82rem;
  font-weight: 600;
  transition: all 0.18s ease;
}
.module-guide-trigger > span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: 1px solid var(--brand-primary);
  border-radius: 50%;
  color: var(--brand-primary);
  font-size: 0.72rem;
}
.module-guide-trigger:hover {
  border-color: var(--brand-primary);
  color: var(--brand-primary);
  background: var(--surface-accent);
}
</style>

<style>
.module-guide-popover .el-popover__title { margin-bottom: 12px; }
.module-guide-content { color: var(--text-secondary); font-size: 0.84rem; line-height: 1.6; }
.module-guide-content h2 { margin: 0 0 12px; color: var(--text-primary); font-size: 1rem; }
.module-guide-steps { display: grid; gap: 10px; margin: 0; padding: 0; list-style: none; }
.module-guide-steps li { display: flex; align-items: flex-start; gap: 9px; }
.module-guide-steps li > span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 21px;
  height: 21px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--surface-accent);
  color: var(--brand-primary);
  font-size: 0.72rem;
  font-weight: 700;
}
.module-guide-steps p { margin: 0; }
.module-guide-example,
.module-guide-boundaries { margin-top: 14px; padding: 11px 12px; border-radius: var(--radius-sm); }
.module-guide-example { background: var(--surface-accent); }
.module-guide-boundaries { border: 1px solid rgba(245, 158, 11, 0.22); background: rgba(245, 158, 11, 0.06); }
.module-guide-content strong { display: block; margin-bottom: 4px; color: var(--text-primary); font-size: 0.8rem; }
.module-guide-content p { margin: 0; }
.module-guide-boundaries p + p { margin-top: 5px; }
@media (max-width: 420px) {
  .module-guide-popover { max-width: calc(100vw - 24px) !important; }
}
</style>
