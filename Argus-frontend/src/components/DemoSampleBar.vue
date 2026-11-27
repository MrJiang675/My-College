<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  demoSamples,
  loadSampleAsFile,
  type DemoSample,
  type DemoPreset,
} from '@/constants/demoSamples'
const props = defineProps<{ module: string }>()
const emit = defineEmits<{ load: [sample: DemoSample, file: File, preset?: DemoPreset] }>()
const error = ref(''),
  busy = ref(false)
const samples = computed(() =>
  demoSamples.filter(
    (s) =>
      s.targetModule === props.module ||
      (props.module === 'documents' && s.id === 'course') ||
      (props.module === 'campus' && s.id === 'notice'),
  ),
)
async function load(s: DemoSample, preset?: DemoPreset) {
  busy.value = true
  error.value = ''
  try {
    emit('load', s, await loadSampleAsFile(s.filePath, s.fileName), preset)
  } catch {
    error.value = '样例暂不可用，请稍后再试。'
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <section class="sample-bar" aria-label="内置样例">
    <article v-for="s in samples" :key="s.id">
      <b>{{ s.title }}</b
      ><small>{{ s.description }}</small>
      <div>
        <a :href="s.filePath" target="_blank" rel="noopener">预览原文</a
        ><a :href="s.filePath" :download="s.fileName">下载</a
        ><button :disabled="busy" type="button" @click="load(s)">仅载入材料</button
        ><button
          v-for="preset in s.presets"
          :key="preset.question"
          :disabled="busy"
          type="button"
          @click="load(s, preset)"
        >
          {{ props.module === 'documents' ? '一键使用内置样例讲义' : preset.question }}
        </button>
      </div>
    </article>
    <p v-if="error" role="status">{{ error }}</p>
  </section>
</template>
<style scoped>
.sample-bar {
  display: grid;
  gap: 8px;
  margin: 14px 0;
}
.sample-bar article {
  padding: 12px;
  border: 1px solid var(--border-default);
  background: var(--surface-subtle);
  border-radius: 8px;
}
.sample-bar small {
  display: block;
  color: var(--text-secondary);
  margin: 4px 0;
}
.sample-bar div {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}
.sample-bar a,
.sample-bar button {
  font-size: 12px;
  color: var(--brand-primary);
}
.sample-bar button {
  background: var(--surface-white);
  padding: 6px 9px;
  border: 1px solid var(--border-default);
  border-radius: 5px;
}
</style>
