export type Model = { kind: 'rc' | 'beam' | 'normal'; values: number[] }
export const fields = {
  normal: ['均值 μ', '标准差 σ', '样本量 n'],
  rc: ['电阻 / kΩ', '电容 / μF', '电源 / V'],
  beam: ['梁长 / m', '宽 / mm', '高 / mm', '集中力 / N', '弹性模量 / GPa', '加载位置 / 梁长'],
}
export const skill: Record<string, string> = {
  learn: '辅导概念或出一道可作答的题；批改须利用前一轮题目。严格区分资料原文与解释。',
  practice:
    '识别学生要验证的现象。支持直流阶跃 RC 充电、矩形悬臂梁单集中力及新闻采访原句核实。其他实验只解释步骤和需测量量。只有模型明确、参数充分且无冲突时输出计算指令。未给关键参数时先追问，也可以征得同意后使用示例参数。更新只改变学生指定的量。RC values=[电阻kΩ,电容μF,电源V]；beam values=[梁长m,宽mm,高mm,力N,杨氏模量GPa,载荷位置占梁长比例]。单位换算。用 JSON 对象响应：{"answer":"解释或追问","model":null} 或 {"answer":"说明依据和假设","model":{"kind":"rc或beam","values":[...]}}。采访核实时输出 {"answer":"说明","checks":[{"quote":"材料中的原句","claim":"报道表述","verdict":"证据充分或待核实"}]}。不要生成程序。',
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
  id: Model['kind']
  name: string
  discipline: string
  summary: string
  parameterSchema: ExperimentParameter[]
  compute(params: number[]): {
    points: { x: number; y: number }[]
    comparisonPoints?: { x: number; y: number }[]
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
export const experimentPlugins: Record<Model['kind'], ExperimentModelPlugin> = {
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
export function validModel(value: unknown): value is Model {
  if (!value || typeof value !== 'object') return false
  const m = value as Model,
    plugin = experimentPlugins[m.kind]
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
    (m.kind !== 'normal' || Number.isInteger(m.values[2]))
  )
}
export function valueAt(model: Model, ratio: number) {
  if (model.kind === 'rc') return rcValue(model.values, ratio)
  if (model.kind === 'beam') return beamValue(model.values, ratio)
  const z = 8 * ratio - 4
  return Math.exp((-z * z) / 2) / (model.values[1]! * Math.sqrt(2 * Math.PI))
}
export function extent(m: Model) {
  if (m.kind === 'rc') return (5 * m.values[0]! * m.values[1]!) / 1000
  return m.kind === 'beam' ? m.values[0]! : 8 * m.values[1]!
}

function parseRc(question: string, previous?: Model): Model | undefined {
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
  return { kind, values: experimentPlugins[kind].parameterSchema.map((p) => p.defaultValue) }
}
