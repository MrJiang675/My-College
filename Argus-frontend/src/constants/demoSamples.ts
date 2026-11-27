import type { Model } from '@/views/college/models'
export interface SampleCitation {
  fileName: string
  chunkExcerpt: string
  sectionTitle: string
  matchHint: string
}
export interface DemoPreset {
  question: string
  answerMarkdown: string
  citations: SampleCitation[]
  experimentAction?: { current: Model; previous?: Model }
  badgeText: string
}
export interface DemoSample {
  id: string
  title: string
  targetModule: string
  fileName: string
  filePath: string
  description: string
  tags: string[]
  presets: DemoPreset[]
}
function citation(fileName: string, chunkExcerpt: string, sectionTitle: string): SampleCitation {
  return { fileName, chunkExcerpt, sectionTitle, matchHint: '逐字核对' }
}
const badgeText = '内置样例快速演示 · 支持继续自由追问'
export const demoSamples: DemoSample[] = [
  {
    id: 'course',
    title: '中值定理课程讲义',
    targetModule: 'learn',
    fileName: 'sample-course-notes.md',
    filePath: '/samples/sample-course-notes.md',
    description: '检查定理前提与课堂待补充问题',
    tags: ['讲义', '数学'],
    presets: [
      {
        question: '应用拉格朗日中值定理要先核对哪些前提？',
        answerMarkdown:
          '应先确认 a<b，函数在闭区间连续、开区间可导。连续性包括端点，可导性覆盖整个开区间。讲义中绝对值函数在 0 处不可导，不能直接应用定理。关于有限个不可导点的推广仍需核实。',
        citations: [
          citation(
            'sample-course-notes.md',
            '连续性要求包含两个端点。可导性要求作用于整个开区间。',
            '定理与成立前提',
          ),
        ],
        badgeText,
      },
    ],
  },
  {
    id: 'rc',
    title: 'RC 充电实验指导书',
    targetModule: 'practice',
    fileName: 'sample-rc-experiment-guide.md',
    filePath: '/samples/sample-rc-experiment-guide.md',
    description: '12 kΩ / 50 μF / 6 V，时间常数 0.6 s',
    tags: ['电学', '单变量'],
    presets: [
      {
        question: '按内置指导书生成 RC 充电实验。',
        answerMarkdown:
          '采用理想直流阶跃、零初始电压模型。R=12 kΩ，C=50 μF，U=6 V，时间常数 τ=0.6 s。可在实验台调节观察时刻，并锁定基准后比较参数。',
        citations: [
          citation(
            'sample-rc-experiment-guide.md',
            '电阻为 12 kΩ，电容为 50 μF，电源阶跃电压为 6 V，初始电容电压为 0 V。',
            '已知条件',
          ),
        ],
        experimentAction: { current: { kind: 'rc', values: [12, 50, 6] } },
        badgeText,
      },
      {
        question: '把电阻改成原来的两倍（24 kΩ），对比曲线。',
        answerMarkdown:
          '电阻变为 24 kΩ，电容 50 μF、电源 6 V 保持不变。τ 从 0.6 s 变为 1.2 s。达到相同终值比例所需时间变为两倍，稳态电压仍为 6 V。',
        citations: [
          citation(
            'sample-rc-experiment-guide.md',
            '保持电源与电容不变，仅将电阻改为 24 kΩ。',
            '单变量对照',
          ),
        ],
        experimentAction: {
          current: { kind: 'rc', values: [24, 50, 6] },
          previous: { kind: 'rc', values: [12, 50, 6] },
        },
        badgeText,
      },
    ],
  },
  {
    id: 'notice',
    title: '校园竞赛招募通知',
    targetModule: 'opps',
    fileName: 'sample-competition-notice.txt',
    filePath: '/samples/sample-competition-notice.txt',
    description: '演示报名资格与缺失的综测认定文件',
    tags: ['通知', '需核实'],
    presets: [
      {
        question: '这份通知的报名条件是什么，综测加分还缺什么依据？',
        answerMarkdown:
          '通知面向本校大二、大三本科生，每队三人。报名截至 2026 年 12 月 10 日 17 时，提交报名表、个人参与说明和一页研究计划。需核实本院本学年综测认定目录；通知未给出加分标准。建议携带赛事全称和拟申报奖项向学院辅导员或教务老师核实。',
        citations: [
          citation(
            'sample-competition-notice.txt',
            '报名截止时间为2026年12月10日17时。',
            '报名时间与材料',
          ),
          citation(
            'sample-competition-notice.txt',
            '每队需提交报名表、个人参与说明和一页研究计划。',
            '报名时间与材料',
          ),
        ],
        badgeText,
      },
    ],
  },
]
export async function loadSampleAsFile(filePath: string, fileName: string, signal?: AbortSignal) {
  const response = await fetch(filePath, { signal })
  if (!response.ok) throw Error('样例文件暂时无法读取')
  return new File([await response.blob()], fileName, {
    type: fileName.endsWith('.md') ? 'text/markdown' : 'text/plain',
  })
}
