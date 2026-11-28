import { compileFormula } from '../../utils/formulaEvaluator.ts'
export type BuiltinKind = 'rc' | 'beam' | 'normal' | 'damped' | 'projectile' | 'power' | 'binomial'
export type FormulaParam = {
  name: string
  symbol: string
  unit?: string
  value: number
  min: number
  max: number
  step: number
}
export type FormulaModel = {
  kind: 'formula'
  title: string
  discipline?: string
  summary?: string
  expression: string
  xVar: string
  xLabel: string
  yLabel: string
  xMin: number
  xMax: number
  params: FormulaParam[]
}
export type Model = { kind: BuiltinKind; values: number[] } | FormulaModel
export const fields = {
  normal: ['均值 μ', '标准差 σ', '样本量 n'],
  rc: ['电阻 / kΩ', '电容 / μF', '电源 / V'],
  beam: ['梁长 / m', '宽 / mm', '高 / mm', '集中力 / N', '弹性模量 / GPa', '加载位置 / 梁长'],
}
export const skill: Record<string, string> = {
  learn: '辅导概念或出一道可作答的题；批改须利用前一轮题目。严格区分资料原文与解释。',
  practice:
    '支持 rc、beam、normal、damped、projectile、power、binomial 七种确定性模型和 formula 通用公式。明确请求模拟时，已识别模型的缺失次要参数用教材默认值并明确披露；未知关系不得编造。公式只用白名单数学函数。返回 answer 和 model 的 JSON，不生成程序。',
  opps: '根据提供的机会原文分别核对资格、截止日与综测认定。无本院本学年目录时标记综测待核实；每条结论引用对应原句。',
  campus: '从通知原文提取对象、材料、时间及办理地点，逐项附原句。缺失项写需核实。',
  project: '整理学生自己的项目贡献，区分个人和团队工作，保留成果位置，不补造指标。',
  career: '根据已保存经历和用户提供的申请要求逐项对照，写出可验证证据与缺口，不虚构成绩。',
  experience: '依据已有经历答复，明确来源项目及成果位置。',
}
export interface ExperimentParameter {
  name: string
  unit: string
  defaultValue: number
  min: number
  max: number
  step: number
}
export interface ExperimentModelPlugin {
  id: BuiltinKind
  name: string
  discipline: string
  summary: string
  parameterSchema: ExperimentParameter[]
  compute(params: number[]): {
    points: { x: number; y: number }[]
    comparisonPoints?: { x: number; y: number }[]
    lowerEnvelope?: { x: number; y: number }[]
    metrics: Record<string, number>
  }
  samplePrompt: string
}
const rcValue = (v: number[], r: number) => v[2]! * -Math.expm1(-5 * r)
const beamValue = (v: number[], r: number) => {
  const [L, b, h, F, E, k] = v as [number, number, number, number, number, number]
  const a = L * k,
    x = L * r,
    I = ((b / 1000) * (h / 1000) ** 3) / 12
  return (1000 * F * (x <= a ? x * x * (3 * a - x) : a * a * (3 * x - a))) / (6 * E * 1e9 * I)
}
const parameter = (
  name: string,
  unit: string,
  defaultValue: number,
  min = 0.000001,
  max = 1e9,
  step = 1,
): ExperimentParameter => ({ name, unit, defaultValue, min, max, step })
function points(values: number[], fn: (v: number[], r: number) => number, width: number) {
  return Array.from({ length: 101 }, (_, i) => ({ x: (width * i) / 100, y: fn(values, i / 100) }))
}
const basePlugins: Record<'rc' | 'beam' | 'normal', ExperimentModelPlugin> = {
  rc: {
    id: 'rc',
    name: 'RC 充电',
    discipline: '电工电子',
    summary: '理想直流阶跃，初始电压为零',
    parameterSchema: [
      parameter('电阻', 'kΩ', 12),
      parameter('电容', 'μF', 50),
      parameter('电源', 'V', 6),
    ],
    compute(v) {
      const tau = (v[0]! * v[1]!) / 1000
      return { points: points(v, rcValue, 5 * tau), metrics: { 时间常数: tau } }
    },
    samplePrompt: '使用RC充电示例',
  },
  beam: {
    id: 'beam',
    name: '悬臂梁静载',
    discipline: '工程力学',
    summary: '矩形等截面、线弹性小变形、单个集中力',
    parameterSchema: [
      parameter('梁长', 'm', 1),
      parameter('宽', 'mm', 20),
      parameter('高', 'mm', 40),
      parameter('集中力', 'N', 50),
      parameter('弹性模量', 'GPa', 200),
      parameter('加载位置', '梁长比例', 1, 0.01, 1, 0.01),
    ],
    compute(v) {
      return { points: points(v, beamValue, v[0]!), metrics: { 端部挠度: beamValue(v, 1) } }
    },
    samplePrompt: '使用悬臂梁示例',
  },
  normal: {
    id: 'normal',
    name: '正态分布与均值抽样',
    discipline: '数学与统计',
    summary: '已知总体标准差，独立同分布正态样本',
    parameterSchema: [
      parameter('均值', '', 0, -1000, 1000, 0.1),
      parameter('标准差', '', 1, 0.01, 100, 0.1),
      parameter('样本量', '', 25, 1, 10000, 1),
    ],
    compute(v) {
      const se = v[1]! / Math.sqrt(v[2]!)
      return {
        points: Array.from({ length: 201 }, (_, i) => {
          const x = v[0]! + v[1]! * (i / 25 - 4)
          return {
            x,
            y: Math.exp(-0.5 * ((x - v[0]!) / v[1]!) ** 2) / (v[1]! * Math.sqrt(2 * Math.PI)),
          }
        }),
        comparisonPoints: Array.from({ length: 401 }, (_, i) => {
          const x = v[0]! + v[1]! * (i / 50 - 4)
          return { x, y: Math.exp(-0.5 * ((x - v[0]!) / se) ** 2) / (se * Math.sqrt(2 * Math.PI)) }
        }),
        metrics: { 标准误: se, 置信下界: v[0]! - 1.96 * se, 置信上界: v[0]! + 1.96 * se },
      }
    },
    samplePrompt: '使用正态分布示例',
  },
}
export const experimentPlugins = Object.assign(basePlugins, {
  damped: {
    id: 'damped',
    name: '阻尼简谐振动',
    discipline: '大学物理',
    summary: '欠阻尼自由振动，忽略外力；紫色为正负包络',
    parameterSchema: [
      parameter('振幅', 'm', 1, 0.01, 100, 0.01),
      parameter('阻尼比', '', 0.15, 0.001, 0.999, 0.01),
      parameter('固有角频率', 'rad/s', 3, 0.01, 100, 0.1),
    ],
    compute(v: number[]) {
      const [A, z, w] = v as [number, number, number],
        tau = 1 / (z * w),
        T = Math.min(10 * tau, (40 * Math.PI) / w)
      return {
        points: points(
          v,
          (_, r) => A * Math.exp(-z * w * T * r) * Math.cos(w * Math.sqrt(1 - z * z) * T * r),
          T,
        ),
        comparisonPoints: Array.from({ length: 101 }, (_, i) => ({
          x: (T * i) / 100,
          y: A * Math.exp((-z * w * T * i) / 100),
        })),
        lowerEnvelope: Array.from({ length: 101 }, (_, i) => ({
          x: (T * i) / 100,
          y: -A * Math.exp((-z * w * T * i) / 100),
        })),
        metrics: { 衰减时间常数: tau, 阻尼角频率: w * Math.sqrt(1 - z * z) },
      }
    },
    samplePrompt: '使用阻尼振动示例',
  },
  projectile: {
    id: 'projectile',
    name: '抛体运动',
    discipline: '大学物理',
    summary: '无空气阻力，平坦落地面',
    parameterSchema: [
      parameter('初速度', 'm/s', 20, 0.1, 1000, 0.1),
      parameter('仰角', '°', 45, 0.1, 89.9, 0.1),
      parameter('初始高度', 'm', 0, 0, 1000, 0.1),
      parameter('重力加速度', 'm/s²', 9.81, 0.1, 100, 0.01),
    ],
    compute(v: number[]) {
      const [speed, deg, h, g] = v as [number, number, number, number],
        vy = speed * Math.sin((deg * Math.PI) / 180),
        vx = speed * Math.cos((deg * Math.PI) / 180),
        t = (vy + Math.sqrt(vy * vy + 2 * g * h)) / g
      return {
        points: Array.from({ length: 201 }, (_, i) => {
          const s = (t * i) / 200
          return { x: vx * s, y: h + vy * s - (g * s * s) / 2 }
        }),
        metrics: { 射程: vx * t, 最大高度: h + (vy * vy) / (2 * g), 飞行时间: t },
      }
    },
    samplePrompt: '使用抛体运动示例',
  },
  power: {
    id: 'power',
    name: '最大功率传输',
    discipline: '电工电子',
    summary: '理想电动势与串联内阻；绿色功率(W)，紫色效率(0—1)，双量纲勿直接比较幅值',
    parameterSchema: [
      parameter('电动势', 'V', 12, 0.01, 1000, 0.1),
      parameter('内阻', 'Ω', 10, 0.01, 10000, 0.1),
      parameter('负载电阻', 'Ω', 10, 0, 100000, 0.1),
    ],
    compute(v: number[]) {
      const [E, r, R] = v as [number, number, number],
        max = Math.max(5 * r, 1.2 * R)
      return {
        points: points(v, (_, s) => (E * E * max * s) / (r + max * s) ** 2, max),
        comparisonPoints: points(v, (_, s) => (max * s) / (r + max * s), max),
        metrics: {
          最大功率: (E * E) / (4 * r),
          最大功率对应电阻: r,
          当前负载功率: (E * E * R) / (r + R) ** 2,
          当前效率: R / (r + R),
        },
      }
    },
    samplePrompt: '使用最大功率示例',
  },
  binomial: {
    id: 'binomial',
    name: '二项分布',
    discipline: '数学与统计',
    summary: 'n 次独立同分布伯努利试验',
    parameterSchema: [
      parameter('试验次数', '', 20, 1, 200, 1),
      parameter('成功概率', '', 0.5, 0, 1, 0.01),
    ],
    compute(v: number[]) {
      const [n, p] = v as [number, number]
      const result = Array.from({ length: n + 1 }, (_, k) => {
        let log = 0
        for (let j = 1; j <= k; j++) log += Math.log((n - j + 1) / j)
        return {
          x: k,
          y:
            p === 0
              ? k === 0
                ? 1
                : 0
              : p === 1
                ? k === n
                  ? 1
                  : 0
                : Math.exp(log + k * Math.log(p) + (n - k) * Math.log1p(-p)),
        }
      })
      return { points: result, metrics: { 期望: n * p, 方差: n * p * (1 - p) } }
    },
    samplePrompt: '使用二项分布示例',
  },
} as Record<Exclude<BuiltinKind, 'rc' | 'beam' | 'normal'>, ExperimentModelPlugin>)

export function formulaResult(m: FormulaModel) {
  const fn = compileFormula(m.expression, [m.xVar, ...m.params.map((p) => p.symbol)])
  const vars = Object.fromEntries(m.params.map((p) => [p.symbol, p.value]))
  const points = Array.from({ length: 401 }, (_, i) => {
    const x = m.xMin + ((m.xMax - m.xMin) * i) / 400
    return { x, y: fn({ ...vars, [m.xVar]: x }) }
  })
  if (points.some((p) => !Number.isFinite(p.y) || Math.abs(p.y) > 1e12))
    throw Error('区间存在奇点、溢出或非实数，请缩小区间')
  return {
    points,
    metrics: {
      峰值: Math.max(...points.map((p) => p.y)),
      谷值: Math.min(...points.map((p) => p.y)),
    },
  }
}
export function modelValues(m: Model) {
  return m.kind === 'formula' ? m.params.map((p) => p.value) : m.values
}
export function pluginFor(m: Model): Omit<ExperimentModelPlugin, 'id'> {
  if (m.kind !== 'formula') return experimentPlugins[m.kind]
  return {
    name: m.title,
    discipline: m.discipline || '通用数学绘图',
    summary: m.summary || m.expression,
    parameterSchema: m.params.map((p) => ({
      name: p.name,
      unit: p.unit || '',
      defaultValue: p.value,
      min: p.min,
      max: p.max,
      step: p.step,
    })),
    compute: (values) =>
      formulaResult({ ...m, params: m.params.map((p, i) => ({ ...p, value: values[i]! })) }),
    samplePrompt: '绘制自定义公式',
  }
}
export function computeModel(m: Model) {
  return pluginFor(m).compute(modelValues(m))
}
export function editModel(m: Model, index: number, value: number): Model {
  return m.kind === 'formula'
    ? { ...m, params: m.params.map((p, i) => (i === index ? { ...p, value } : p)) }
    : { ...m, values: m.values.map((v, i) => (i === index ? value : v)) }
}
export function validModel(value: unknown): value is Model {
  try {
    if (!value || typeof value !== 'object') return false
    const m = value as Model
    if (m.kind === 'formula') {
      if (
        typeof m.title !== 'string' ||
        !m.title.trim() ||
        m.title.length > 120 ||
        typeof m.expression !== 'string' ||
        !Array.isArray(m.params) ||
        m.params.length > 8
      )
        return false
      const names = [m.xVar, ...m.params.map((p) => p.symbol)]
      if (
        names.some(
          (n) =>
            typeof n !== 'string' ||
            !/^[A-Za-z][A-Za-z0-9_]{0,15}$/.test(n) ||
            [
              'pi',
              'e',
              'sin',
              'cos',
              'tan',
              'asin',
              'acos',
              'atan',
              'exp',
              'log',
              'ln',
              'sqrt',
              'abs',
              'pow',
              'min',
              'max',
            ].includes(n),
        ) ||
        new Set(names).size !== names.length
      )
        return false
      if (
        !Number.isFinite(m.xMin) ||
        !Number.isFinite(m.xMax) ||
        m.xMax <= m.xMin ||
        Math.max(Math.abs(m.xMin), Math.abs(m.xMax)) > 1e6
      )
        return false
      if (
        m.params.some(
          (p) =>
            typeof p.name !== 'string' ||
            p.name.length > 80 ||
            ![p.value, p.min, p.max, p.step].every(Number.isFinite) ||
            p.min >= p.max ||
            p.step <= 0 ||
            p.value < p.min ||
            p.value > p.max,
        )
      )
        return false
      formulaResult(m)
      return true
    }
    const plugin = experimentPlugins[m.kind]
    return (
      !!plugin &&
      Array.isArray(m.values) &&
      m.values.length === plugin.parameterSchema.length &&
      m.values.every(
        (v, i) =>
          Number.isFinite(v) &&
          v >= plugin.parameterSchema[i]!.min &&
          v <= plugin.parameterSchema[i]!.max,
      ) &&
      (m.kind !== 'normal' || Number.isInteger(m.values[2])) &&
      (m.kind !== 'binomial' || Number.isInteger(m.values[0])) &&
      computeModel(m).points.every((p) => Number.isFinite(p.y))
    )
  } catch {
    return false
  }
}
export function valueAt(m: Model, ratio: number) {
  if (m.kind === 'rc') return rcValue(m.values, ratio)
  if (m.kind === 'beam') return beamValue(m.values, ratio)
  const p = computeModel(m).points
  return p[Math.round(Math.max(0, Math.min(1, ratio)) * (p.length - 1))]!.y
}
export function extent(m: Model) {
  const p = computeModel(m).points
  return p[p.length - 1]!.x - p[0]!.x
}

function parseRc(question: string, previous?: Model): { kind: 'rc'; values: number[] } | undefined {
  const resistance = question.match(
    /电阻\s*(?:改为|改成|设为|设置为)?\s*[:=]?\s*(\d+(?:\.\d+)?)\s*(k?Ω|千欧|欧姆)/i,
  )
  const capacitance = question.match(
    /电容\s*(?:改为|改成|设为|设置为)?\s*[:=]?\s*(\d+(?:\.\d+)?)\s*([μmunp]?F|毫法|微法|纳法|皮法)/i,
  )
  const voltage = question.match(
    /电源\s*(?:改为|改成|设为|设置为)?\s*[:=]?\s*(\d+(?:\.\d+)?)\s*(mV|V|千伏)/i,
  )
  if (!resistance && !capacitance && !voltage) return undefined
  if (!previous && (!resistance || !capacitance || !voltage)) return undefined

  const values = previous?.kind === 'rc' ? [...previous.values] : [0, 0, 0]
  if (resistance) {
    const unit = resistance[2]!.toLowerCase()
    values[0] = Number(resistance[1]) / (unit === 'ω' || unit === '欧姆' ? 1000 : 1)
  }
  if (capacitance) {
    const unit = capacitance[2]!.toLowerCase()
    const scale =
      unit === 'f'
        ? 1e6
        : unit === 'mf' || unit === '毫法'
          ? 1000
          : unit === 'nf' || unit === '纳法'
            ? 0.001
            : unit === 'pf' || unit === '皮法'
              ? 1e-6
              : 1
    values[1] = Number(capacitance[1]) * scale
  }
  if (voltage) {
    const unit = voltage[2]!.toLowerCase()
    values[2] = Number(voltage[1]) * (unit === 'mv' ? 0.001 : unit === '千伏' ? 1000 : 1)
  }
  return { kind: 'rc', values }
}

// Resolve unambiguous parameter edits without spending a model call.
export function localExperiment(
  question: string,
  previous?: Model,
): { answer: string; model?: Model } | undefined {
  const q = question.trim()
  if (q === '使用正态分布示例')
    return {
      answer: '已载入正态总体与均值抽样分布，可修改均值、标准差和样本量。',
      model: { kind: 'normal', values: [0, 1, 25] },
    }
  const parsedRc = parseRc(q, previous)
  if (parsedRc) {
    if (!validModel(parsedRc)) return { answer: 'RC 参数超出可计算范围，请核对单位。' }
    const [resistance, capacitance, voltage] = parsedRc.values
    const format = (value: number) => Number(value.toPrecision(4))
    const tau = format((resistance! * capacitance!) / 1000)
    return {
      model: parsedRc,
      answer: `已按 R=${format(resistance!)} kΩ、C=${format(capacitance!)} μF、U=${format(voltage!)} V 生成 RC 充电曲线，时间常数 τ=${tau} s。`,
    }
  }
  if (
    previous?.kind === 'rc' &&
    /^(?:把|将)?电阻(?:改为原来的两倍|翻倍|加倍)[。！!？?]?$/.test(q)
  ) {
    const model: Model = { kind: 'rc', values: [...previous.values] }
    model.values[0] = model.values[0]! * 2
    if (!validModel(model)) return { answer: '电阻超出可计算范围，请核对单位。' }
    return { model, answer: '电容与电源保持不变。电阻翻倍，时间常数也翻倍。' }
  }
  if (/^(我想做(一个)?)?(电学|力学)实验[。？?]?$/.test(q))
    return { answer: '想改变哪个量，观察什么现象？也可以上传指导书，我会先核对条件。' }
  if (/^(?:试用|使用|打开)(RC充电|悬臂梁)示例$/.test(q)) {
    const model: Model = q.includes('RC')
      ? { kind: 'rc', values: [10, 100, 5] }
      : { kind: 'beam', values: [1, 20, 40, 50, 200, 1] }
    return { model, answer: '已使用界面所列的示例条件，可逐项核对或继续调整。' }
  }
}

export function normalCdf(z: number) {
  const sign = z < 0 ? -1 : 1
  const x = Math.abs(z) / Math.sqrt(2),
    t = 1 / (1 + 0.3275911 * x)
  const erf =
    sign *
    (1 -
      ((((1.061405429 * t - 1.453152027) * t + 1.421413741) * t - 0.284496736) * t + 0.254829592) *
        t *
        Math.exp(-x * x))
  return (1 + erf) / 2
}
export function defaultModel(kind: Model['kind']): Model {
  if (kind === 'formula')
    return {
      kind,
      title: '阻尼余弦函数',
      expression: 'A * exp(-k * x) * cos(w * x)',
      xVar: 'x',
      xLabel: 'x',
      yLabel: 'y',
      xMin: 0,
      xMax: 10,
      params: [
        { name: '振幅', symbol: 'A', value: 1, min: 0, max: 10, step: 0.1 },
        { name: '衰减系数', symbol: 'k', value: 0.2, min: 0, max: 2, step: 0.01 },
        { name: '角频率', symbol: 'w', value: 3, min: 0.1, max: 10, step: 0.1 },
      ],
    }
  return { kind, values: experimentPlugins[kind].parameterSchema.map((p) => p.defaultValue) }
}
