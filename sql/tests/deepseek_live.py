"""Opt-in live DeepSeek acceptance against the isolated local test profile. No credentials in code/output."""
import io,json
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
from playwright.sync_api import sync_playwright
BASE='http://127.0.0.1:15174/api/'
def main():
 with sync_playwright() as p:
  guest=p.request.new_context(base_url=BASE);login=guest.post('auth/demo-guest');assert login.ok,login.status;c=p.request.new_context(base_url=BASE,extra_http_headers={'Authorization':'Bearer '+login.json()['data']['accessToken']})
  def read(r):
   assert r.ok,(r.status,r.json().get('message'));return r.json()['data']
  import sys
  if '--simulation-only' not in sys.argv:
   answer=read(c.post('college/reading',data={'question':'根据材料，理想 RC 时间常数是多少？','source':'理想 RC 电路，R=10千欧，C=100微法，时间常数为1秒。','current':None},timeout=45000));assert answer.get('answer');print('PASS live global DeepSeek task answer')
   image=Image.new('RGB',(1000,360),'white');draw=ImageDraw.Draw(image);font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',40);draw.text((30,60),'RC: R = 10 kOhm, C = 100 uF',font=font,fill='black');draw.text((30,160),'time constant = 1 second',font=font,fill='black');buf=io.BytesIO();image.save(buf,format='PNG')
   vision=read(c.post('college/files/extract-text',multipart={'file':{'name':'rc.png','mimeType':'image/png','buffer':buf.getvalue()}},timeout=45000));assert vision['model'].startswith('deepseek');assert '100' in vision['extractedText'];print('PASS live DeepSeek native vision OCR')
  r=c.post('college/practice/simulate-stream',data={'experimentName':'光学折射与全反射','simulationGoal':'做一个精简的二维光路图，两个滑块调节入射角和折射率，显示临界角，能暂停、重置、记录数据点。不要三维，不写长注释，完整 HTML 尽量在10000字符内。','reasoningMode':'fast'},timeout=150000);assert r.ok
  events=[]
  for frame in r.text().replace('\r\n','\n').split('\n\n'):
   lines=frame.splitlines();tag=next((x[6:].strip() for x in lines if x.startswith('event:')),'');data='\n'.join(x[5:].strip() for x in lines if x.startswith('data:'))
   if data:events.append((tag,json.loads(data)))
  error=next((v for k,v in events if k=='error'),None);assert error is None,error
  result=next(v for k,v in events if k=='done');assert '<canvas' in result['html'].lower() or '<svg' in result['html'].lower();assert 'Content-Security-Policy' in result['html'];assert any(k=='delta' for k,v in events)
  import tempfile
  path=Path(tempfile.gettempdir())/'argus-deepseek-live-simulation.json';path.write_text(json.dumps(result,ensure_ascii=False),encoding='utf-8');print('PASS live DeepSeek SSE HTML generation:',result['usedModel'],len(result['html']),'characters; artifact in temporary directory')
  c.dispose();guest.dispose()
if __name__=='__main__':main()
