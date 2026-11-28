import { marked } from 'marked'
const allowed = new Set(
  'P H1 H2 H3 H4 H5 H6 STRONG EM DEL UL OL LI BLOCKQUOTE PRE CODE TABLE THEAD TBODY TR TH TD HR BR A'.split(
    ' ',
  ),
)
export function renderSafeMarkdown(content: string): string {
  const escaped = content.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  const doc = new DOMParser().parseFromString(
    marked.parse(escaped, { gfm: true, breaks: true, async: false }) as string,
    'text/html',
  )
  for (const element of Array.from(doc.body.querySelectorAll('*'))) {
    if (!allowed.has(element.tagName)) {
      element.replaceWith(
        doc.createTextNode(element.textContent || element.getAttribute('alt') || ''),
      )
      continue
    }
    for (const attribute of Array.from(element.attributes))
      if (!(element.tagName === 'A' && ['href', 'title'].includes(attribute.name)))
        element.removeAttribute(attribute.name)
    if (element.tagName === 'A') {
      const href = element.getAttribute('href') || ''
      try {
        if (!['http:', 'https:', 'mailto:'].includes(new URL(href, location.origin).protocol))
          element.removeAttribute('href')
      } catch {
        element.removeAttribute('href')
      }
      element.setAttribute('rel', 'noopener noreferrer')
      element.setAttribute('target', '_blank')
    }
  }
  return doc.body.innerHTML
}
