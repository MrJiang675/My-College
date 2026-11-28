<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import { sandboxHtml } from '@/utils/simulationSandbox'
const props = defineProps<{ html: string }>(),
  emit = defineEmits<{
    error: [message: string]
    records: [rows: Record<string, string | number>[]]
  }>()
const frame = ref<HTMLIFrameElement>(),
  generation = ref(0),
  safeHtml = computed(() => sandboxHtml(props.html))
watch(
  () => props.html,
  () => generation.value++,
)
function message(e: MessageEvent) {
  if (
    e.source !== frame.value?.contentWindow ||
    e.origin !== 'null' ||
    !e.data ||
    typeof e.data !== 'object'
  )
    return
  const v = e.data
  if (v.type === 'argus-sim-error' && typeof v.message === 'string')
    emit('error', v.message.slice(0, 2000) + ' · 行 ' + (Number(v.line) || 0))
  if (v.type === 'argus-sim-records' && Array.isArray(v.rows) && v.rows.length <= 1000) {
    const rows = v.rows
      .filter((r: unknown) => r && typeof r === 'object' && !Array.isArray(r))
      .map((r: Record<string, unknown>) =>
        Object.fromEntries(
          Object.entries(r)
            .slice(0, 16)
            .filter(([, value]) => typeof value === 'string' || typeof value === 'number')
            .map(([key, value]) => [
              key.slice(0, 60),
              typeof value === 'string' ? value.slice(0, 300) : value,
            ]),
        ),
      )
    emit('records', rows)
  }
}
onMounted(() => window.addEventListener('message', message))
onUnmounted(() => window.removeEventListener('message', message))
</script>
<template>
  <iframe
    ref="frame"
    :key="generation"
    :srcdoc="safeHtml"
    sandbox="allow-scripts"
    referrerpolicy="no-referrer"
    title="AI 动态原理仿真实验台"
    class="sim-frame"
    allow="
      camera 'none';
      microphone 'none';
      geolocation 'none';
      payment 'none';
      clipboard-read 'none';
      clipboard-write 'none';
    "
  />
</template>
<style scoped>
.sim-frame {
  display: block;
  width: 100%;
  height: max(620px, calc(100dvh - 210px));
  border: 1px solid var(--border-default);
  border-radius: 12px;
  background: var(--surface-white);
  color-scheme: light dark;
}
</style>
