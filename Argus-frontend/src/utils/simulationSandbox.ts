export const simulationCsp =
  "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data: blob:; connect-src 'none'; frame-src 'none'; worker-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none';"
/** Defense in depth: exports and API responses also pass a trusted head before any generated code. */
export function sandboxHtml(raw: string) {
  if (!raw || raw.length > 150000) throw Error('仿真 HTML 长度无效')
  const doc = new DOMParser().parseFromString(raw, 'text/html')
  // Older responses may carry Markdown commentary after the HTML; never put it into the experiment canvas.
  for (const node of [...doc.body.childNodes].reverse()) {
    if (node.nodeType !== Node.TEXT_NODE) break
    const t = node.textContent?.trim() || ''
    if (!t || t.startsWith('```') || t.startsWith('###')) node.remove()
    else break
  }
  if (!doc.querySelector('canvas,svg')) throw Error('仿真缺少可视化画布，请重新生成或使用内置模型')
  for (const el of doc.querySelectorAll(
    'meta,base,link,iframe,object,embed,frame,frameset,portal,script[src],script[data-argus-sim-bridge]',
  ))
    el.remove()
  for (const el of doc.querySelectorAll('a,form')) {
    el.removeAttribute('href')
    el.removeAttribute('action')
    el.removeAttribute('target')
  }
  return `<!DOCTYPE html><html><head><meta http-equiv="Content-Security-Policy" content="${simulationCsp}"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>html,body{margin:0;width:100%;min-height:100%;box-sizing:border-box}*{box-sizing:border-box}canvas,svg{max-width:100%}</style><script data-argus-sim-bridge>(()=>{let n=0;const send=(message,line)=>{if(n++<20)parent.postMessage({type:'argus-sim-error',message:String(message).slice(0,2000),line:Number(line)||0},'*')};addEventListener('error',e=>send(e.message,e.lineno));addEventListener('unhandledrejection',e=>send(e.reason?.message||e.reason,0))})();</script>${doc.head.innerHTML}</head><body>${doc.body.innerHTML}</body></html>`
}
