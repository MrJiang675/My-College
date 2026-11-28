import type { CalendarDeadline } from '@/utils/icsExport'
export type CampusProfile = { school: string; college: string; major: string; grade: string }
export type StudentExperience = {
  id: string
  project: string
  text: string
  team?: string
  source: string
}
export type NoticeAnalysis = {
  attachmentWarnings?: string[]
  opportunity?: {
    level: string
    audience: string
    benefit: string
    credit: string
    risks: string[]
  }

  answer: string
  eligibility: { status: 'GREEN' | 'YELLOW' | 'RED'; summary: string; evidences: string[] }
  missingDocuments: string[]
  teacherInquiryScripts: string[]
  deadlines: (CalendarDeadline & { evidenceQuote?: string })[]
  checklist: { item: string; required: boolean; formatNote: string; evidenceQuote: string }[]
}
