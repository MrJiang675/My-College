"""Optional integration test: isolated local argus_test only; uses the configured cloud AI."""
import hashlib, io, math, time
from PIL import Image, ImageDraw, ImageFont
from playwright.sync_api import sync_playwright

BASE='http://127.0.0.1:15173/api/'
def main():
 with sync_playwright() as w:
  c=w.request.new_context(base_url=BASE)
  login=c.post('auth/demo-guest');assert login.ok,login.status
  token=login.json()['data']['accessToken']
  c.dispose();c=w.request.new_context(base_url=BASE,extra_http_headers={'Authorization':'Bearer '+token})
  def check(response):
   assert response.ok,(response.status,response.json().get('message','请求失败'))
   return response.json()['data']
  result=check(c.post('college/files/extract-text',multipart={'file':{'name':'notes.txt','mimeType':'text/plain','buffer':'本人完成了 RC 电路的标定与误差分析。'.encode()}}))
  assert '标定' in result['extractedText'];print('PASS live authenticated short text extraction')
  import sys
  vision_unavailable=False
  if '--storage-only' not in sys.argv:
   image=Image.new('RGB',(1000,400),'white');draw=ImageDraw.Draw(image)
   font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',42)
   draw.text((40,60),'RC circuit: R = 12 kOhm',font=font,fill='black');draw.text((40,150),'C = 50 uF, U = 6 V',font=font,fill='black')
   buf=io.BytesIO();image.save(buf,format='PNG')
   vision=c.post('college/files/extract-text',multipart={'file':{'name':'circuit.png','mimeType':'image/png','buffer':buf.getvalue()}},timeout=45000)
   vision_unavailable=not vision.ok
   if vision_unavailable:print('LIMITED live OCR: provider unavailable; readable API response, no fabricated extraction')
   else:
    result=check(vision);assert '12' in result['extractedText'] and '50' in result['extractedText'];print('PASS live vision OCR via configured glm-4.6v-flash')
   request={'resumeText':'电气工程专业大二，课程设计使用 RC 电路进行充电实验。本人负责传感器标定与误差分析，成果是校内课程报告，未参加实习。','targetRole':'电气工程专业实习','interviewType':'实习技术面'}
   generated=check(c.post('college/interview/generate',data=request,timeout=55000))
   assert len(generated['questions'])==5
   request['answers']=[{'question':q['question'],'category':q['category'],'userAnswer':'我负责标定与误差分析，先用已知电压对传感器进行校准，再记录重复测量的偏差；校内报告可以核对，但还没有公开链接。'} for q in generated['questions']]
   report=check(c.post('college/interview/evaluate',data=request,timeout=55000))
   assert 0<=report['overallScore']<=100 and len(report['questionFeedbacks'])==5
   print('PASS live five-question generation and evaluated report')
  # A valid one-page PDF padded to exactly 50 MiB tests transport size independently of semantic size.
  from reportlab.pdfgen import canvas
  pdf=io.BytesIO();writer=canvas.Canvas(pdf);writer.drawString(72,720,'RC study: resistance 12 kiloohms, capacitance 50 microfarads.');writer.save()
  data=pdf.getvalue();data+=b' '*(50*1024*1024-len(data))
  group=check(c.post('groups',data={'name':'50MB 分片安全合并验收'}))
  group_id=group['groupId'] if isinstance(group,dict) else group
  size=3*1024*1024
  init=check(c.post('documents/upload/init',data={'groupId':group_id,'fileName':'bounded-50mb.pdf','fileSize':len(data),'fileHash':hashlib.sha256(data).hexdigest(),'contentType':'application/pdf','chunkSize':size,'chunkCount':math.ceil(len(data)/size)}))
  for i in range(math.ceil(len(data)/size)):
   chunk=data[i*size:(i+1)*size]
   check(c.post('documents/upload/chunks',multipart={'uploadId':init['uploadId'],'chunkIndex':str(i),'chunkHash':hashlib.sha256(chunk).hexdigest(),'chunk':{'name':f'part-{i}','mimeType':'application/octet-stream','buffer':chunk}},timeout=30000))
  document_id=check(c.post('documents/upload/'+init['uploadId']+'/complete',timeout=120000))
  for _ in range(90):
   docs=check(c.get('documents',params={'groupId':group_id}));doc=next(d for d in docs if d['documentId']==document_id)
   if doc['status']=='READY':break
   assert doc['status']!='FAILED',doc.get('failureReason')
   time.sleep(1)
  assert doc['status']=='READY'
  print('PASS live 50 MiB upload in 3 MiB chunks, SHA-256 merge, disk PDF parse, READY with ES disabled')
  c.dispose()
  if vision_unavailable:raise SystemExit('PARTIAL: vision provider not available; other live checks completed')
if __name__=='__main__':main()
