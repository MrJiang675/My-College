import type { CampusProfile, NoticeAnalysis } from '@/types/notice'
export const noticeScenarios = [
  {
    id: 'contest',
    title: '竞赛招募',
    source:
      '【内置演示通知，非真实校务】校园创新赛招募。报名仅面向信息工程学院大二、大三本科生。报名截止时间为2026年12月10日17:00。请提交报名表（PDF）和项目说明（PDF），发送至学院教学办公室。通知未附本院本学年竞赛认定目录，院内初审时间和盖章办公室需另行核实。',
  },
  {
    id: 'scholarship',
    title: '奖学金评选',
    source:
      '【内置演示通知，非真实校务】学院专项奖学金评选。申请对象为信息工程学院大二、大三本科生。材料提交截止时间为2026年12月12日16:00。须提交成绩单（PDF）和本人事迹说明（PDF），报送至学院教学办公室。评选细则与院内排名证明未随通知提供，请向辅导员核实。',
  },
  {
    id: 'submission',
    title: '材料报送',
    source:
      '【内置演示通知，非真实校务】课程项目成果报送。报送对象为信息工程学院大二、大三本科生。报送截止时间为2026年12月15日17:00。请提交团队报告（PDF）与个人贡献说明（PDF），提交至学院教学办公室。盖章办公室和初审是否提前截止未在通知中说明。',
  },
]
export function noticeExample(
  source: string,
  profile: CampusProfile,
  enabled: boolean,
): NoticeAnalysis | undefined {
  const scene = noticeScenarios.find((s) => s.source === source)
  if (!scene) return
  const sentences = source.split('。').filter(Boolean),
    eligibility = sentences.find((s) => /面向|申请对象|报送对象/.test(s))!
  const complete = !!profile.college && !!profile.grade && enabled
  const match = profile.college === '信息工程学院' && ['大二', '大三'].includes(profile.grade)
  const status = !complete ? 'YELLOW' : match ? 'GREEN' : 'RED'
  const date = source.match(/(2026)年(12)月(\d+)日(\d+):(\d+)/)!
  const dateQuote = sentences.find((s) => s.includes(date[0]))!
  const material = sentences.find((s) => s.includes('（PDF）'))!
  return {
    answer: '内置样例规则演示：仅按通知明示条件判断，不代表真实校内政策或综测认定。',
    eligibility: {
      status,
      summary: !complete
        ? '请填写学院与年级并启用档案，再对照资格。'
        : `${profile.college} · ${profile.grade}：${match ? '符合本示例明示报名范围，认定和院内流程仍需核实。' : '不在本示例规定的学院/年级范围内，请勿按符合资格直接报名。'}`,
      evidences: [eligibility],
    },
    missingDocuments: [
      '本院当期竞赛/评奖认定文件或细则',
      '院内初审是否早于通知截止时间',
      '盖章办公室及办理时间',
    ],
    teacherInquiryScripts: [
      `老师您好，我在查看${scene.title}通知，想确认${profile.college || '本院'}${profile.grade || '本年级'}的适用范围。请问当期认定细则在哪里查看，院内初审是否需提前提交？谢谢老师！`,
    ],
    deadlines: [
      {
        title: scene.title + '材料截止',
        dateTime: `${date[1]}-${date[2]}-${date[3]!.padStart(2, '0')}T${date[4]!.padStart(2, '0')}:${date[5]}:00+08:00`,
        location: '学院教学办公室',
        description: dateQuote,
        needsVerify: false,
        evidenceQuote: dateQuote,
      },
    ],
    checklist: [
      {
        item:
          scene.id === 'scholarship' ? '成绩单' : scene.id === 'contest' ? '报名表' : '团队报告',
        required: true,
        formatNote: 'PDF，按实际通知核对名称',
        evidenceQuote: material,
      },
      {
        item:
          scene.id === 'contest'
            ? '项目说明'
            : scene.id === 'scholarship'
              ? '本人事迹说明'
              : '个人贡献说明',
        required: true,
        formatNote: 'PDF，不把团队成果写成本人贡献',
        evidenceQuote: material,
      },
    ],
  }
}
