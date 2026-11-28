export type MaterialKind = '电子材料' | '纸质材料' | '系统填报'
export type MaterialRequirement = {
  id: string
  name: string
  kind: MaterialKind
  format: string
  quote: string
  deadline: string
  copies: number
  notes: string
  fileId?: string
  outputName: string
  namingChecked?: boolean
  contentChecked: boolean
  signatureChecked: boolean
  copiesChecked: boolean
  signatureRequired: boolean
}
export const normalizedEvidence = (s: string) =>
  s
    .normalize('NFKC')
    .replace(/[\s\u00a0]+/g, '')
    .replace(/[，。；：]/g, (c) => ({ '，': ',', '。': '.', '；': ';', '：': ':' })[c]!)
export const matchesEvidence = (s: string, q: string) =>
  !!q.trim() && normalizedEvidence(s).includes(normalizedEvidence(q))
export function newRequirement(
  name = '待补充材料',
  format = '需核实',
  quote = '',
): MaterialRequirement {
  return {
    id: crypto.randomUUID(),
    name,
    format,
    quote,
    kind: /纸质|打印|份/.test(format + name)
      ? '纸质材料'
      : /系统|平台|在线填报/.test(format + name)
        ? '系统填报'
        : '电子材料',
    deadline: '',
    copies: 0,
    notes: '',
    outputName: '',
    contentChecked: false,
    signatureChecked: false,
    copiesChecked: false,
    signatureRequired: /签字|签名|盖章/.test(format + quote),
  }
}
export function extensions(r: MaterialRequirement) {
  return [
    ...new Set(
      (r.format.match(/(?:pdf|xlsx|docx|doc|xls|csv|txt|png|jpg|jpeg)\b/gi) || []).map((s) =>
        s.toLowerCase(),
      ),
    ),
  ]
}
export const extension = (name: string) => name.split('.').pop()?.toLowerCase() || ''
export function blockers(r: MaterialRequirement, files: Map<string, File>) {
  const list: string[] = [],
    f = r.fileId ? files.get(r.fileId) : undefined,
    allowed = extensions(r)
  if (r.kind === '电子材料') {
    if (!f) list.push('尚未关联任何文件（材料缺失）')
    else {
      if (allowed.length && !allowed.includes(extension(f.name))) list.push('文件扩展名与要求不符')
      if (r.outputName && extension(r.outputName) !== extension(f.name))
        list.push('不能通过改扩展名伪装修复文件格式')
      if (!r.outputName) list.push('文件名尚未确认')
      const template = r.format.match(
        /(?:命名(?:格式)?(?:为|[:：])|文件名[:：])\s*[“"「]?([^\s，。；”"」]+\.(?:pdf|xlsx|docx|csv|txt))/i,
      )?.[1]
      if (template) {
        const expected = template.split('_'),
          actual = r.outputName.split('_')
        if (
          expected.length !== actual.length ||
          expected.some((part, i) =>
            part.includes('学号')
              ? !/^[A-Za-z0-9]{4,}$/.test(actual[i] || '')
              : part.includes('姓名')
                ? !/^[\p{L} ·.]{1,40}$/u.test(actual[i] || '')
                : part !== actual[i],
          )
        )
          list.push('文件名不合规范（要求：' + template + '）')
      }
      if (/命名/.test(r.format) && !r.namingChecked) list.push('文件命名规则尚未人工确认')
    }
  }
  if (!r.contentChecked) list.push('尚未勾选确认内容满足要求')
  if (r.signatureRequired && !r.signatureChecked) list.push('尚未确认本人及导师签字/盖章')
  if (r.copies < 1 || !r.copiesChecked) list.push('份数/提交次数需核实并确认')
  return list
}
