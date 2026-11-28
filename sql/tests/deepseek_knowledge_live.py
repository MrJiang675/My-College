"""Live hybrid verification: Zhipu 1024-vector ingestion + DeepSeek grounded QA on isolated argus_test."""
import json,time
from playwright.sync_api import sync_playwright
BASE='http://127.0.0.1:15174/api/'
def main():
 with sync_playwright() as p:
  guest=p.request.new_context(base_url=BASE);r=guest.post('auth/demo-guest');assert r.ok;c=p.request.new_context(base_url=BASE,extra_http_headers={'Authorization':'Bearer '+r.json()['data']['accessToken']})
  def read(r):assert r.ok,(r.status,r.json().get('message'));return r.json()['data']
  group=read(c.post('groups',data={'name':'DeepSeek迁移联调（本机隔离）'}));group=group['groupId'] if isinstance(group,dict) else group
  source='理想串联 RC 课堂实验：电阻为10千欧，电容为100微法。时间常数为1秒。讲义规定必须在三个时间常数后记录电容电压。'
  doc=read(c.post('documents/upload',multipart={'groupId':str(group),'file':{'name':'deepseek-migration-course.txt','mimeType':'text/plain','buffer':source.encode()}}))
  for _ in range(60):
   row=next(d for d in read(c.get('documents',params={'groupId':group})) if d['documentId']==doc)
   if row['status']=='READY':break
   assert row['status']!='FAILED',row.get('failureReason');time.sleep(1)
  assert row['status']=='READY';print('PASS live document ingestion READY using retained Zhipu embedding')
  r=c.post('qa/stream-ask',data={'groupId':group,'question':'讲义规定在几个时间常数后记录电容电压？'},timeout=95000);assert r.ok
  answer=[];citations=[]
  for frame in r.text().replace('\r\n','\n').split('\n\n'):
   lines=frame.splitlines();kind=next((l[6:].strip() for l in lines if l.startswith('event:')),'');data='\n'.join(l[5:].strip() for l in lines if l.startswith('data:'))
   if kind=='error':raise AssertionError(data)
   if kind=='token':answer.append(data)
   if kind=='citations':citations=json.loads(data)
  assert any(v in ''.join(answer) for v in ['三','3']);assert any(v['documentId']==doc for v in citations);print('PASS live DeepSeek grounded streaming QA with original document citation')
  session=read(c.post('assistant/sessions'))
  r=c.post('assistant/chat/stream',data={'sessionId':session['sessionId'],'message':'请调用知识库检索工具，根据讲义说出规定在几个时间常数后记录电压，并说明来源，不要使用常识代替。','toolMode':'KB_SEARCH','groupId':group},timeout=100000);assert r.ok
  reply='';done=False
  for frame in r.text().replace('\r\n','\n').split('\n\n'):
   lines=frame.splitlines();kind=next((l[6:].strip() for l in lines if l.startswith('event:')),'');data='\n'.join(l[5:].strip() for l in lines if l.startswith('data:'))
   if not data:continue
   payload=json.loads(data)
   if kind=='error':raise AssertionError(payload.get('error','Agent error'))
   if kind=='delta':reply+=payload.get('delta','')
   if kind=='done':done=True;reply=payload.get('reply') or reply
  assert done and any(v in reply for v in ['三','3']);print('PASS live DeepSeek ReAct knowledge-tool streaming, no reasoning-content protocol error')
  c.dispose();guest.dispose()
if __name__=='__main__':main()
