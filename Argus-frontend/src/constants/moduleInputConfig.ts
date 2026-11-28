export interface ModuleInputConfig {
  materialTitle: string
  materialSubtitle: string
  recommendedUploads: string[]
  materialPlaceholder: string
  taskLabel: string
  taskPlaceholder: string
  submitLabel: string
  quickActions: string[]
}
export const moduleInputConfig: Record<string, ModuleInputConfig> = {
  learn: {
    materialTitle: '课程讲义 / 笔记 / 习题截图',
    materialSubtitle: '先核对课程材料，再选解释、复习或自测',
    recommendedUploads: ['PPT转PDF', '课堂笔记', '错题截图'],
    materialPlaceholder: '粘贴讲义、课堂笔记或需要讲解的题目',
    taskLabel: '你想怎么学这份材料？',
    taskPlaceholder: '解释这个定理的成立条件 / 出 3 道概念自测题',
    submitLabel: '开始辅导',
    quickActions: ['解释这个定理的成立条件', '出 3 道概念自测题'],
  },
  practice: {
    materialTitle: '实验指导书 / 题目公式 / 采访原稿',
    materialSubtitle: '上传材料后说“想做模拟实验”，也可以直接描述函数',
    recommendedUploads: ['实验指导书', '黑板公式图', '采访记录'],
    materialPlaceholder: '粘贴实验目的、已知参数、公式及需要验证的现象',
    taskLabel: '想模拟什么现象或画什么曲线？',
    taskPlaceholder:
      '根据材料生成模拟实验；画 RC 充电、阻尼振动曲线；或 y = A * exp(-k*x) * cos(w*x)',
    submitLabel: '生成模拟实验 / 解析',
    quickActions: [
      '根据上传的实验材料生成模拟实验',
      '画阻尼简谐振动曲线',
      '画 y = A * exp(-k*x) * cos(w*x)',
    ],
  },
  opps: {
    materialTitle: '把通知粘进来或上传截图/附件',
    materialSubtitle: '保留原来的换行，时间与材料对得更准',
    recommendedUploads: ['微信/QQ通知截图(Ctrl+V)', '通知PDF/Word', '官网公告链接'],
    materialPlaceholder: '逐字粘贴通知正文，保留段落、材料要求和时间',
    taskLabel: '补充你的关注点（选填，留空即完整分析）',
    taskPlaceholder: '例如：重点核对我能否报名、附件是否齐全',
    submitLabel: '开始综合分析并提取要求',
    quickActions: ['重点核对缺失附件和院内提前截止时间'],
  },
  project: {
    materialTitle: '零散项目记录 / 群聊汇报 / 结题文档',
    materialSubtitle: '只整理已发生的工作，先分清团队和本人行动',
    recommendedUploads: ['群聊记录', '阶段周报', '成果截图'],
    materialPlaceholder: '粘贴项目汇报、分工记录和成果证明位置',
    taskLabel: '想怎么整理这段项目经历？',
    taskPlaceholder: '拆清团队成果和我的具体贡献，准备收录进经历',
    submitLabel: '整理项目草稿',
    quickActions: ['帮我拆清团队成果和我个人的具体贡献'],
  },
  career: {
    materialTitle: '目标岗位 JD / 保研夏令营通知 / 个人简历',
    materialSubtitle: '先确认目标，再核对要求与本人经历',
    recommendedUploads: ['招聘JD截图', '夏令营简章', '个人简历PDF'],
    materialPlaceholder: '粘贴目标岗位要求或院校简章',
    taskLabel: '准备怎样打磨申请？',
    taskPlaceholder: '对照要求，指出薄弱证据并修改申请陈述',
    submitLabel: '生成申请指导',
    quickActions: ['逐项对照要求，指出证据缺口'],
  },
  experience: {
    materialTitle: '补充佐证材料 / 获奖证书截图',
    materialSubtitle: '给已收录经历补上可核验的证明',
    recommendedUploads: ['获奖证书截图', '报告摘要'],
    materialPlaceholder: '补充个人负责部分、成果位置或证书原文',
    taskLabel: '基于已有经历提炼什么？',
    taskPlaceholder: '将本人贡献提炼为可核对的经历要点',
    submitLabel: '整理经历',
    quickActions: ['整理个人贡献和待补的证明材料'],
  },
}
