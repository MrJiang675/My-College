package com.argus.rag.college;
import com.argus.rag.common.exception.BusinessException;
/** Resource isolation is enforced before any generated script executes. iframe must additionally omit allow-same-origin. */
public final class SimulationHtmlSupport {
 private SimulationHtmlSupport(){}
 public static final String CSP="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data: blob:; connect-src 'none'; frame-src 'none'; worker-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none';";
 public static String summary(String raw){var paragraphs=java.util.regex.Pattern.compile("(?is)<p\\b[^>]*>(.*?)</p>").matcher(raw);var parts=new java.util.ArrayList<String>();while(paragraphs.find()&&parts.size()<3){String text=org.springframework.web.util.HtmlUtils.htmlUnescape(paragraphs.group(1).replaceAll("<[^>]+>","")).strip();if(text.matches("(?s).*(公式|假设|定律|关系|条件|原理|参数).*"))parts.add(text.substring(0,Math.min(500,text.length())));}return parts.isEmpty()?"AI 生成的定性模拟。请在画布中核对公式、默认参数和适用条件。":"AI 生成的原理摘要，仍需核验：\n\n"+String.join("\n\n",parts);}
 public static String sanitizeAndWrapHtml(String raw){
  if(raw==null||raw.length()>120000)throw new BusinessException("仿真代码超过安全长度，请简化实验后重试。");
  String html=raw.strip().replaceFirst("(?is)^```(?:html)?\\s*","").replaceFirst("(?is)\\s*```$","");
  int start=html.toLowerCase(java.util.Locale.ROOT).indexOf("<!doctype html");if(start<0)start=html.toLowerCase(java.util.Locale.ROOT).indexOf("<html");if(start>0)html=html.substring(start);
  int end=html.toLowerCase(java.util.Locale.ROOT).lastIndexOf("</html>");if(end>=0)html=html.substring(0,end+7);
  if(!html.matches("(?is).*(?:<canvas\\b|<svg\\b).*"))throw new BusinessException("模型未生成可交互实验画布，请重试或使用内置模型。");
  html=html.replaceAll("(?is)<!--.*?-->","").replaceAll("(?is)<script\\b(?=[^>]*\\bsrc\\s*=)[^>]*>.*?</script\\s*>","")
   .replaceAll("(?is)<(?:iframe|object|embed|frame|frameset|portal)\\b[^>]*>.*?</(?:iframe|object|embed|frame|frameset|portal)\\s*>","")
   .replaceAll("(?is)<(?:meta|base|link|iframe|object|embed|frame|portal)\\b[^>]*>","")
   .replaceAll("(?is)<!doctype[^>]*>|</?(?:html|head|body)\\b[^>]*>","");
  // Put all generated markup after a new trusted head; even malformed leading scripts cannot run before CSP.
  return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta http-equiv=\"Content-Security-Policy\" content=\""+CSP+"\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><style>html,body{margin:0;min-height:100%;width:100%;box-sizing:border-box}*{box-sizing:border-box}canvas,svg{max-width:100%}</style><script>"+
   "(()=>{let n=0;const report=(msg,line)=>{if(n++<20)parent.postMessage({type:'argus-sim-error',message:String(msg).slice(0,2000),line:Number(line)||0},'*')};addEventListener('error',e=>report(e.message,e.lineno));addEventListener('unhandledrejection',e=>report(e.reason&&e.reason.message||e.reason,0))})();"+
   "</script></head><body>"+html+"</body></html>";
 }
}
