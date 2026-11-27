<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { experimentPlugins, defaultModel, normalCdf, validModel, type Model } from './models'

const props = defineProps<{
  current: Model
  previous?: Model
  snapshots?: { title: string; model: Model }[]
}>()
const emit = defineEmits<{
  snapshot: []
  change: [model: Model]
  restore: [model: Model]
  import: []
  ask: [question: string]
  news:[]
}>()
const mode = ref('自由实验')
const inspector = ref('参数调节')
const point = ref(20)
const baseline = ref<Model>()
const showBaseline = ref(true)
const showMarkers = ref(true)
const prediction = ref('')
const adjusted = ref(false)
const explained = ref(false)
const plugin = computed(() => experimentPlugins[props.current.kind])
const result = computed(() => plugin.value.compute(props.current.values))
const old = computed(() =>
  baseline.value?.kind === props.current.kind
    ? baseline.value
    : props.previous?.kind === props.current.kind
      ? props.previous
      : undefined,
)
const oldResult = computed(() => (old.value ? plugin.value.compute(old.value.values) : undefined))
const allPoints = computed(() => [
  ...result.value.points,
  ...(result.value.comparisonPoints || []),
  ...(showBaseline.value ? oldResult.value?.points || [] : []),
])
const xmin = computed(() => Math.min(...allPoints.value.map((p) => p.x)))
const xmax = computed(() => Math.max(...allPoints.value.map((p) => p.x)))
const ymax = computed(() => Math.max(...allPoints.value.map((p) => p.y), 1e-12))
function x(value: number) {
  return 30 + (650 * (value - xmin.value)) / (xmax.value - xmin.value || 1)
}
function y(value: number) {
  return 230 - (200 * value) / ymax.value
}
function line(points: { x: number; y: number }[]) {
  return points.map((p) => x(p.x) + ',' + y(p.y)).join(' ')
}
const observed = computed(() => {
  const points = result.value.points
  return points[Math.round((point.value * (points.length - 1)) / 100)]!
})
const z = computed(() => (observed.value.x - props.current.values[0]!) / props.current.values[1]!)
const challenge = computed(() => {
  if (!oldResult.value) return '请先锁定基准组，再只改变一个参数。'
  const key =
    props.current.kind === 'rc' ? '时间常数' : props.current.kind === 'beam' ? '端部挠度' : '标准误'
  const ratio = result.value.metrics[key]! / oldResult.value.metrics[key]!
  return Math.abs(ratio - 2) < 0.02
    ? '达成目标：指标已变为基准的两倍。'
    : '当前指标为基准的 ' + ratio.toFixed(3) + ' 倍，目标为 2 倍。'
})
watch(
  () => props.current.kind,
  () => {
    baseline.value = undefined
    adjusted.value = false
    prediction.value = ''
    explained.value = false
  },
)
function edit(index: number, value: string) {
  const model = { kind: props.current.kind, values: [...props.current.values] }
  model.values[index] = Number(value)
  if (validModel(model)) {
    emit('change', model)
    adjusted.value = true
  }
}
function lock() {
  baseline.value = structuredClone(JSON.parse(JSON.stringify(props.current)))
}
function exportRecord() {
  const blob = new Blob(
    [
      JSON.stringify(
        {
          model: props.current,
          baseline: old.value,
          prediction: prediction.value,
          result: result.value,
        },
        null,
        2,
      ),
    ],
    { type: 'application/json' },
  )
  const url = URL.createObjectURL(blob),
    a = document.createElement('a')
  a.href = url
  a.download = 'argus-experiment.json'
  a.click()
  URL.revokeObjectURL(url)
}
function requestExplanation() {
  explained.value = true
  emit('ask', '请根据当前参数与基准差异解释结果，指出假设条件。')
}
const quickValues: Record<string, number[]> = {
  电阻: [6, 12, 24, 48],
  电容: [25, 50, 100],
  样本量: [10, 25, 100],
  均值: [0, 1, 2],
}
</script>

<template>
  <article class="lab">
    <header>
      <div>
        <small>确定性计算模型 · {{ plugin.discipline }}</small>
        <h2>{{ plugin.name }}</h2>
      </div>
      <select v-model="mode" aria-label="实验模式">
        <option>自由实验</option>
        <option>引导任务</option>
        <option>参数对比挑战</option>
      </select>
    </header>
    <p class="flow">选模板 / 传指导书 → 提出预测 → 调一个变量 → 对照曲线 → 保存快照</p>
    <nav aria-label="实验模板">
      <button
        v-for="item in experimentPlugins"
        :key="item.id"
        :aria-pressed="current.kind === item.id"
        @click="emit('change', defaultModel(item.id))"
      >
        {{ item.name }}</button
      ><button
        @click="emit('news')"
      >
        新闻采访核实台</button
      ><button @click="emit('import')">导入指导书 / 跨学科设计</button>
    </nav>
    <p>已注册模型使用确定性计算；其他学科进入实验设计与测量方法讨论。</p>
    <div v-if="mode === '引导任务'" class="task">
      <label>我的预测<input v-model="prediction" placeholder="先预测，再改变参数" /></label>
      <ul>
        <li>{{ prediction ? '✓' : '○' }} 提出预测</li>
        <li>{{ adjusted ? '✓' : '○' }} 改变变量</li>
        <li>{{ explained ? '✓' : '○' }} 请求对比解释</li>
      </ul>
    </div>
    <p v-if="mode === '参数对比挑战'" role="status" class="task">{{ challenge }}</p>
    <div class="lab-grid">
      <section>
        <svg viewBox="0 0 720 260" role="img" :aria-label="plugin.name + '曲线'">
          <path d="M30 20 V230 H690" fill="none" stroke="var(--border-default)" />
          <rect
            v-if="current.kind === 'normal' && showMarkers"
            :x="x(result.metrics.置信下界!)"
            y="30"
            :width="x(result.metrics.置信上界!) - x(result.metrics.置信下界!)"
            height="200"
            fill="var(--brand-primary)"
            opacity=".09"
          />
          <polyline
            v-if="showBaseline && oldResult"
            :points="line(oldResult.points)"
            fill="none"
            stroke="#bf7c4d"
            stroke-width="2"
            stroke-dasharray="6 4"
          />
          <polyline
            :points="line(result.points)"
            fill="none"
            stroke="var(--brand-primary)"
            stroke-width="3"
          />
          <polyline
            v-if="result.comparisonPoints"
            :points="line(result.comparisonPoints)"
            fill="none"
            stroke="#8c70ce"
            stroke-width="2"
          />
          <circle v-if="showMarkers" :cx="x(observed.x)" :cy="y(observed.y)" r="4" fill="#bd7344" />
          <text x="30" y="252" fill="currentColor">{{ xmin.toPrecision(3) }}</text>
          <text x="640" y="252" fill="currentColor">{{ xmax.toPrecision(3) }}</text>
        </svg>
        <small v-if="current.kind === 'normal'"
          >绿色为总体密度，紫色为均值抽样分布，阴影为均值的 95% 区间。</small
        >
        <label
          >观察{{ current.kind === 'rc' ? '时刻' : current.kind === 'beam' ? '位置' : '值'
          }}<input v-model.number="point" aria-label="观察位置" type="range" min="0" max="100"
        /></label>
        <div class="hud">
          <div v-for="(metric, key) in result.metrics" :key="key">
            <small>{{ key }}</small
            ><b>{{ metric.toPrecision(4) }}</b>
          </div>
          <div>
            <small>当前横轴</small><b>{{ observed.x.toPrecision(4) }}</b>
          </div>
          <div>
            <small>{{ current.kind === 'normal' ? '密度' : '当前读数' }}</small
            ><b>{{ observed.y.toPrecision(4) }}</b>
          </div>
          <div v-if="current.kind === 'normal'">
            <small>z / 累积分位</small
            ><b>{{ z.toFixed(2) }} / {{ (100 * normalCdf(z)).toFixed(2) }}%</b>
          </div>
          <div v-if="oldResult && current.kind === 'rc'">
            <small>Δτ（s）</small
            ><b>{{ (result.metrics.时间常数! - oldResult.metrics.时间常数!).toPrecision(4) }}</b>
          </div>
        </div>
        <div class="actions">
          <button @click="lock">锁定当前为基准</button
          ><button @click="emit('change', defaultModel(current.kind))">重置参数</button
          ><button @click="exportRecord">导出记录</button
          ><button @click="requestExplanation">请助手解释差异</button>
        </div>
      </section>
      <aside>
        <nav>
          <button
            v-for="tab in ['参数调节', '实验步骤与假设', '历史快照对比']"
            :key="tab"
            :aria-pressed="inspector === tab"
            @click="inspector = tab"
          >
            {{ tab }}
          </button>
        </nav>
        <template v-if="inspector === '参数调节'"
          ><label v-for="(param, index) in plugin.parameterSchema" :key="param.name"
            >{{ param.name }}（{{ param.unit }}）
            <input
              type="number"
              :value="current.values[index]"
              :min="param.min"
              :max="param.max"
              :step="param.step"
              @change="edit(index, ($event.target as HTMLInputElement).value)"
            />
            <input
              type="range"
              :aria-label="param.name"
              :value="current.values[index]"
              :min="param.min"
              :max="Math.min(param.max, Math.max(param.defaultValue * 4, 4))"
              :step="param.step"
              @input="edit(index, ($event.target as HTMLInputElement).value)"
            />
            <button
              v-for="v in quickValues[param.name] || []"
              :key="v"
              @click="edit(index, String(v))"
            >
              {{ v }}
            </button> </label
          ><label><input v-model="showBaseline" type="checkbox" />显示基准曲线</label
          ><label><input v-model="showMarkers" type="checkbox" />关键点标注</label></template
        >
        <p v-if="inspector === '实验步骤与假设'">
          {{ plugin.summary }}。先固定条件，记录预测，再改变一个变量。{{
            current.kind === 'rc'
              ? '电阻决定电容充至同一比例所需的时间。'
              : current.kind === 'beam'
                ? '加载位置和截面决定弯曲响应。'
                : '独立同分布样本，已知总体标准差；均值的标准误随样本量平方根下降。'
          }}
        </p>
        <template v-if="inspector === '历史快照对比'"
          ><button
            v-for="(snap, i) in snapshots || []"
            :key="i"
            @click="emit('restore', snap.model)"
          >
            {{ snap.title }}
          </button></template
        >
        <button class="primary" @click="emit('snapshot')">保存当前快照</button>
      </aside>
    </div>
  </article>
</template>
<style scoped>
.lab {
  padding: 20px;
  border: 1px solid var(--border-default);
  border-radius: 12px;
  background: var(--surface-white);
  color: var(--text-primary);
}
header,
.actions,
nav {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}
h2 {
  margin: 4px 0;
}
.flow,
small {
  color: var(--text-secondary);
}
button,
select {
  border: 1px solid var(--border-default);
  border-radius: 6px;
  padding: 7px;
  background: var(--surface-subtle);
  color: inherit;
  cursor: pointer;
}
button[aria-pressed='true'],
.primary {
  background: var(--brand-primary);
  color: white;
}
.lab-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 240px;
  gap: 18px;
  margin-top: 18px;
}
.lab-grid > section {
  min-width: 0;
}
svg {
  width: 100%;
  background: var(--surface-subtle);
  border-radius: 8px;
}
aside {
  padding: 12px;
  background: var(--surface-subtle);
  border-radius: 8px;
}
label {
  display: block;
  margin: 10px 0;
}
input:not([type='checkbox']) {
  width: 100%;
  accent-color: var(--brand-primary);
  background: var(--surface-white);
  color: inherit;
  border: 1px solid var(--border-default);
  padding: 5px;
}
.hud {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(100px, 1fr));
  gap: 8px;
  margin: 12px 0;
}
.hud div,
.task {
  padding: 10px;
  border: 1px solid var(--border-default);
  border-radius: 8px;
}
.hud b,
.hud small {
  display: block;
}
.hud b {
  font-variant-numeric: tabular-nums;
  font-size: 14px;
}
.actions {
  justify-content: flex-start;
}
li {
  display: inline-block;
  margin-right: 12px;
}
@media (max-width: 900px) {
  .lab-grid {
    grid-template-columns: 1fr;
  }
}
</style>
