import { makeZip } from './zipPackager.ts'
/** Real file containers, clearly marked as examples; never substitute for student evidence. */
export function demoPdf() {
  const stream = 'BT /F1 16 Tf 50 750 Td (ARGUS DEMO - replace with your own evidence) Tj ET'
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    `<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`,
  ]
  let out = '%PDF-1.4\n'
  const offsets = [0]
  objects.forEach((o, i) => {
    offsets.push(out.length)
    out += `${i + 1} 0 obj\n${o}\nendobj\n`
  })
  const xref = out.length
  out += `xref\n0 6\n0000000000 65535 f \n`
  out += offsets
    .slice(1)
    .map((n) => String(n).padStart(10, '0') + ' 00000 n \n')
    .join('')
  out += `trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`
  return new Blob([out], { type: 'application/pdf' })
}
export async function demoXlsx() {
  return makeZip(
    [
      {
        name: '[Content_Types].xml',
        data: '<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>',
      },
      {
        name: '_rels/.rels',
        data: '<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>',
      },
      {
        name: 'xl/workbook.xml',
        data: '<?xml version="1.0"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="DEMO" sheetId="1" r:id="rId1"/></sheets></workbook>',
      },
      {
        name: 'xl/_rels/workbook.xml.rels',
        data: '<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>',
      },
      {
        name: 'xl/worksheets/sheet1.xml',
        data: '<?xml version="1.0"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>ARGUS DEMO - replace with personal data</t></is></c></row></sheetData></worksheet>',
      },
    ],
    true,
  )
}
export async function demoFile(extension: string, name: string) {
  const ext = extension.toLowerCase()
  const blob =
    ext === 'pdf'
      ? demoPdf()
      : ext === 'xlsx'
        ? await demoXlsx()
        : new Blob(
            [
              ext === 'csv'
                ? 'name,status\nARGUS DEMO,replace\n'
                : 'ARGUS DEMO - replace with your own evidence',
            ],
            { type: ext === 'csv' ? 'text/csv' : 'text/plain' },
          )
  return new File([blob], name + '.' + ext, { type: blob.type })
}
export async function generateDemoMaterials() {
  return Promise.all([
    demoFile('pdf', '示例成绩单'),
    demoFile('xlsx', '示例报名表'),
    demoFile('csv', '示例申请书_故意格式错误'),
    demoFile('txt', '示例说明'),
  ])
}
