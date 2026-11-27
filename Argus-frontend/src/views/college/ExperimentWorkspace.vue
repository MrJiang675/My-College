<script setup lang="ts">
import { computed, ref } from 'vue'
import { extent, fields, valueAt, type Model } from './models'

const props = defineProps<{ current: Model; previous?: Model }>()
const emit = defineEmits<{ snapshot: [] }>()
const point = ref(20)
const old = computed(() => props.previous?.kind === props.current.kind ? props.previous : undefined)
const xmax = computed(() => Math.max(extent(props.current), old.value ? extent(old.value) : 0))
const ymax = computed(() => Math.max(valueAt(props.current, 1), old.value ? valueAt(old.value, 1) : 0))

function coords(model: Model, ratio: number) {
  return [12 + 466 * extent(model) * ratio / xmax.value, 154 - 140 * valueAt(model, ratio) / ymax.value]
}
function line(model: Model) {
  return Array.from({ length: 101 }, (_, index) => coords(model, index / 100).join(',')).join(' ')
}
</script>

<template>
  <article class="experiment">
    <header class="experiment__header">
      <div>
        <span class="experiment__kicker">交互实验台</span>
        <h2>{{ current.kind === 'rc' ? 'RC 充电曲线' : '悬臂梁向下挠度' }}</h2>
      </div>
      <div class="experiment__legend">
        <span><i class="is-current" />当前模型</span>
        <span v-if="old"><i />前一次模型</span>
      </div>
    </header>

    <div class="experiment__params">
      <div v-for="(field, index) in fields[current.kind]" :key="field">
        <span>{{ field }}</span>
        <strong>{{ current.values[index] }}</strong>
      </div>
    </div>

    <div class="experiment__chart">
      <svg viewBox="0 0 500 190" role="img" :aria-label="current.kind === 'rc' ? 'RC 充电电压随时间变化曲线' : '悬臂梁挠度随位置变化曲线'">
        <line x1="12" y1="154" x2="486" y2="154" stroke="var(--border-default)" />
        <line x1="12" y1="14" x2="12" y2="154" stroke="var(--border-default)" />
        <line x1="12" y1="84" x2="486" y2="84" stroke="var(--border-subtle)" stroke-dasharray="3 5" />
        <polyline v-if="old" :points="line(old)" fill="none" stroke="#7c8da5" stroke-width="2" stroke-dasharray="5 5" />
        <polyline :points="line(current)" fill="none" stroke="var(--brand-primary)" stroke-width="3" stroke-linejoin="round" />
        <line :x1="coords(current, point / 100)[0]" :y1="coords(current, point / 100)[1]" :x2="coords(current, point / 100)[0]" y2="154" stroke="var(--brand-primary)" stroke-opacity="0.25" stroke-dasharray="3 4" />
        <circle :cx="coords(current, point / 100)[0]" :cy="coords(current, point / 100)[1]" r="5" fill="var(--surface-white)" stroke="var(--brand-primary)" stroke-width="2.5" />
      </svg>
      <div class="experiment__axis"><span>0</span><span>{{ current.kind === 'rc' ? '时间 / s' : '位置 / m' }}</span><span>{{ extent(current).toPrecision(3) }}</span></div>
    </div>

    <label class="experiment__slider">
      <span>{{ current.kind === 'rc' ? '观察时刻' : '测量位置' }}<strong>{{ (extent(current) * point / 100).toPrecision(4) }} {{ current.kind === 'rc' ? 's' : 'm' }}</strong></span>
      <input v-model.number="point" type="range" min="0" max="100" step="1">
    </label>

    <div class="experiment__readout">
      <div><span>{{ current.kind === 'rc' ? '当前电压' : '当前挠度' }}</span><strong>{{ valueAt(current, point / 100).toPrecision(4) }} <small>{{ current.kind === 'rc' ? 'V' : 'mm' }}</small></strong></div>
      <div v-if="current.kind === 'rc'"><span>时间常数 τ</span><strong>{{ (extent(current) / 5).toPrecision(4) }} <small>s</small></strong></div>
      <div v-else><span>末端挠度</span><strong>{{ valueAt(current, 1).toPrecision(4) }} <small>mm</small></strong></div>
    </div>

    <footer class="experiment__footer">
      <p>{{ current.kind === 'rc' ? '理想直流阶跃充电，初始电压 0 V。' : '集中力作用下的静载挠度，向下为正。' }}</p>
      <el-tooltip content="保存当前这组实验参数与曲线，之后可一键恢复继续对比。" placement="top">
        <button type="button" @click="emit('snapshot')">保存快照</button>
      </el-tooltip>
    </footer>
  </article>
</template>

<style scoped>
.experiment { min-width: 0; padding: 22px; border: 1px solid var(--border-default); border-radius: var(--radius-md); background: var(--surface-white); box-shadow: var(--shadow-xs); }
.experiment__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; }
.experiment__kicker { color: var(--brand-primary); font-size: 0.7rem; font-weight: 700; letter-spacing: 0.08em; }
.experiment h2 { margin: 4px 0 0; font-size: 1.04rem; }
.experiment__legend { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 10px; color: var(--text-muted); font-size: 0.68rem; }
.experiment__legend span { display: inline-flex; align-items: center; gap: 5px; }
.experiment__legend i { width: 16px; border-top: 2px dashed #7c8da5; }
.experiment__legend i.is-current { border-top: 2px solid var(--brand-primary); }
.experiment__params { display: grid; grid-template-columns: repeat(auto-fit, minmax(86px, 1fr)); gap: 8px; margin: 18px 0 12px; }
.experiment__params div { display: grid; gap: 3px; padding: 9px 10px; border-radius: var(--radius-sm); background: var(--surface-subtle); }
.experiment__params span { color: var(--text-muted); font-size: 0.66rem; }
.experiment__params strong { color: var(--text-secondary); font-family: 'JetBrains Mono', monospace; font-size: 0.74rem; }
.experiment__chart svg { width: 100%; height: auto; overflow: visible; }
.experiment__axis { display: flex; justify-content: space-between; margin: -2px 10px 0 28px; color: var(--text-muted); font-size: 0.65rem; }
.experiment__slider { display: grid; gap: 8px; margin-top: 18px; }
.experiment__slider > span { display: flex; justify-content: space-between; gap: 10px; color: var(--text-secondary); font-size: 0.76rem; }
.experiment__slider strong { color: var(--brand-primary-dark); font-family: 'JetBrains Mono', monospace; font-size: 0.74rem; }
.experiment__slider input { width: 100%; accent-color: var(--brand-primary); }
.experiment__readout { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-top: 18px; }
.experiment__readout div { display: grid; gap: 4px; padding: 12px; border: 1px solid rgba(74,144,217,.14); border-radius: var(--radius-sm); background: var(--surface-accent); }
.experiment__readout span { color: var(--text-secondary); font-size: 0.7rem; }
.experiment__readout strong { color: var(--brand-primary-dark); font-family: 'JetBrains Mono', monospace; font-size: 1rem; }
.experiment__readout small { font-size: 0.7rem; font-weight: 500; }
.experiment__footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 18px; padding-top: 14px; border-top: 1px solid var(--border-subtle); }
.experiment__footer p { margin: 0; color: var(--text-muted); font-size: 0.7rem; line-height: 1.5; }
.experiment__footer button { flex-shrink: 0; padding: 8px 13px; border-radius: var(--radius-sm); background: var(--brand-primary); color: #fff; font-size: 0.76rem; font-weight: 700; }
.experiment__footer button:hover { background: var(--brand-primary-dark); }
@media (max-width: 520px) {
  .experiment { padding: 16px; }
  .experiment__header { flex-direction: column; }
  .experiment__legend { justify-content: flex-start; }
  .experiment__readout { grid-template-columns: minmax(0, 1fr); }
  .experiment__footer { align-items: flex-start; flex-direction: column; }
}
</style>
