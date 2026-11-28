export type CalendarDeadline = {
  title: string
  dateTime: string
  location?: string
  description?: string
  needsVerify: boolean
}
const escapeText = (text: string) =>
  text
    .replace(/\\/g, '\\\\')
    .replace(/\r\n|\r|\n/g, '\\n')
    .replace(/,/g, '\\,')
    .replace(/;/g, '\\;')
// RFC 5545: fold at <=75 UTF-8 octets; continuation starts with one space.
function fold(line: string): string {
  const encoder = new TextEncoder()
  let result = '',
    part = '',
    bytes = 0
  for (const char of line) {
    const n = encoder.encode(char).length
    if (bytes + n > 75) {
      result += part + '\r\n'
      part = ' '
      bytes = 1
    }
    part += char
    bytes += n
  }
  return result + part
}
function stamp(date: Date) {
  return date
    .toISOString()
    .replace(/[-:]/g, '')
    .replace(/\.\d{3}Z$/, 'Z')
}
export function buildCalendar(items: CalendarDeadline[], now = new Date()): string {
  if (!items.length) throw Error('没有可导出的时间节点。')
  const lines = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//Argus//Campus Actions//ZH',
    'CALSCALE:GREGORIAN',
    'METHOD:PUBLISH',
  ]
  for (const item of items) {
    if (item.needsVerify) throw Error('请先核实并确认待定日期，再导出日历。')
    const day = /^\d{4}-\d{2}-\d{2}$/.test(item.dateTime)
    if (
      !day &&
      !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2})?(?:Z|[+-]\d{2}:\d{2})$/.test(item.dateTime)
    )
      throw Error('时间需要完整日期和时区，请核实原通知。')
    const calendarDate = new Date(item.dateTime.slice(0, 10) + 'T00:00:00Z')
    if (
      !Number.isFinite(calendarDate.getTime()) ||
      calendarDate.toISOString().slice(0, 10) !== item.dateTime.slice(0, 10)
    )
      throw Error('日期无效，请检查年月日。')
    const date = new Date(day ? item.dateTime + 'T00:00:00Z' : item.dateTime)
    if (
      !Number.isFinite(date.getTime()) ||
      (day && date.toISOString().slice(0, 10) !== item.dateTime)
    )
      throw Error('日期无效，请检查年月日。')
    lines.push(
      'BEGIN:VEVENT',
      `UID:${crypto.randomUUID()}@argus.local`,
      `DTSTAMP:${stamp(now)}`,
      day ? 'DTSTART;VALUE=DATE:' + item.dateTime.replace(/-/g, '') : 'DTSTART:' + stamp(date),
      'SUMMARY:' + escapeText(item.title),
      'LOCATION:' + escapeText(item.location || ''),
      'DESCRIPTION:' + escapeText(item.description || ''),
      'BEGIN:VALARM',
      'TRIGGER:-P1D',
      'ACTION:DISPLAY',
      'DESCRIPTION:' + escapeText('提前一天提醒：' + item.title),
      'END:VALARM',
      'END:VEVENT',
    )
  }
  lines.push('END:VCALENDAR')
  return lines.map(fold).join('\r\n') + '\r\n'
}
export function exportCalendar(items: CalendarDeadline[]) {
  const url = URL.createObjectURL(
    new Blob([buildCalendar(items)], { type: 'text/calendar;charset=utf-8' }),
  )
  const link = document.createElement('a')
  link.href = url
  link.download = 'Argus-通知行动日历.ics'
  link.click()
  URL.revokeObjectURL(url)
}
