"""Two-page practice, native image, refresh, sandbox and fallback acceptance with explicit API fixtures."""
import json,io,tempfile
from pathlib import Path
from PIL import Image
from playwright.sync_api import sync_playwright,expect
BASE='http://127.0.0.1:15173'
HTML='''<!DOCTYPE html><html><head><style>body{font:16px sans-serif;margin:20px;background:#eef4fb;color:#172f42}canvas{width:100%;height:300px;background:white}button,input{margin:12px}</style></head><body><h1>光学装置（验收夹具）</h1><p>默认参数，可拖动调节；仅供定性观察</p><canvas id="c" width="800" height="300"></canvas><label>折射率<input id="r" type="range" min="1" max="2" step="0.1" value="1.5"></label><output id="hud">1.5</output><button id="record">记录当前数据点</button><table><tbody id="rows"></tbody></table><script>const ctx=document.querySelector('#c').getContext('2d');ctx.fillStyle='#3888b3';ctx.fillRect(10,30,200,20);document.querySelector('#r').oninput=e=>document.querySelector('#hud').textContent=e.target.value;document.querySelector('#record').onclick=()=>{document.querySelector('#rows').innerHTML='<tr><td>1.5</td></tr>';parent.postMessage({type:'argus-sim-records',rows:[{时间:0,折射率:1.5}]},'*')};fetch('https://blocked.invalid/api').catch(()=>{});</script><script src="https://blocked.invalid/cdn.js"></script></body></html>'''
def main():
 state={'school':'测试大学','dept':'信息学院','major':'电气工程','grade':'大二','track':'实习','pages':{},'entries':[],'snapshots':[]};revision=0;mode='success';refreshes=0;expired=False;calls=[];requests=[];errors=[];blocked=[]
 def api(route):
  nonlocal revision,mode,refreshes,expired
  req=route.request;path=req.url.split('/api/')[-1].split('?')[0];calls.append(path)
  user={'userId':1,'userCode':'demo_guest_fixture','displayName':'验收同学','systemRole':'USER','mustChangePassword':False,'passwordEnabled':False}
  if path=='auth/refresh':refreshes+=1;value={'accessToken':'renewed' if expired else 'initial','currentUser':user}
  elif path=='auth/me':value=user
  elif path=='college/workspace':
   if req.method=='PUT':state.clear();state.update(req.post_data_json['state']);revision+=1;value=revision
   else:value={'revision':revision,'payload':json.dumps(state)}
  elif path=='groups/my':value={'ownedGroups':[],'joinedGroups':[],'pendingInvitations':[]}
  elif path=='college/files/extract-text':value={'extractedText':'光学实验：观察折射与全反射，折射率1.5。','fileName':'指导书.txt','truncated':False}
  elif path=='college/practice/simulate-stream':
   if not expired:expired=True;return route.fulfill(status=401,json={'success':False,'message':'expired fixture'})
   assert req.headers['authorization']=='Bearer renewed';requests.append(req.post_data_json)
   if mode=='hold':return
   if mode=='missing':body='event: error\ndata: '+json.dumps({'message':'尚未配置 DEEPSEEK_API_KEY，当前可先使用内置确定性实验模板','fallbackSuggested':True})+'\n\n'
   else:body='event: status\r\ndata: '+json.dumps({'stage':'building','message':'正在构建实验…'})+'\r\n\r\nevent: delta\r\ndata: '+json.dumps({'receivedChars':1234})+'\r\n\r\nevent: done\r\ndata: '+json.dumps({'title':'光学动态实验','summary':'### 原理\n**折射率与临界角**需核对。','html':HTML,'usedModel':'deepseek-flash-fixture','reasoningMode':'fast'})+'\r\n\r\n'
   return route.fulfill(content_type='text/event-stream',body=body)
  elif path=='college/practice/repair-simulation':
   assert 'fixture runtime error' in req.post_data_json['runtimeError'];assert 'Content-Security-Policy' in req.post_data_json['brokenHtml'];value={'title':'修复后的实验','summary':'已修复','html':HTML.replace('验收夹具','修复夹具'),'usedModel':'deepseek-flash-fixture','reasoningMode':'fast','fixSummary':'已修复，需核验'}
  else:return route.fulfill(status=404,json={'success':False,'message':path})
  route.fulfill(json={'success':True,'data':value})
 with sync_playwright() as p:
  browser=p.chromium.launch(channel='msedge');context=browser.new_context(viewport={'width':1440,'height':1000},permissions=['clipboard-read','clipboard-write']);page=context.new_page();page.on('pageerror',lambda e:(errors.append(str(e)),print('PAGE ERROR',str(e))));page.route(BASE+'/api/**',api);page.route('https://blocked.invalid/**',lambda r:(blocked.append(r.request.url),r.abort()))
  page.goto(BASE+'/app/college/practice');page.get_by_role('heading',name='你想做什么模拟实验？',exact=True).wait_for(timeout=8000);assert page.locator('canvas,iframe,input[type="range"]').count()==0;assert page.get_by_text('对话记录',exact=True).count()==0
  page.get_by_role('button',name='生成模拟实验 →',exact=True).click();page.get_by_text('请先写下想做的实验，或上传一份实验材料',exact=True).wait_for();page.screenshot(path=str(Path(tempfile.gettempdir())/'argus-practice-setup.png'),full_page=True)
  page.locator('input[type="file"]').set_input_files({'name':'指导书.txt','mimeType':'text/plain','buffer':'光学实验指导书'.encode()});page.get_by_text('核对已提取正文',exact=True).wait_for();page.get_by_role('button',name='光学折射与全反射',exact=True).click();before=calls.count('college/files/extract-text');image=io.BytesIO();Image.new('RGB',(1800,700),'white').save(image,format='PNG');page.locator('input[type="file"]').set_input_files({'name':'装置.png','mimeType':'image/png','buffer':image.getvalue()});expect(page.get_by_alt_text('实验截图缩略图')).to_be_visible();assert calls.count('college/files/extract-text')==before
  page.get_by_role('button',name='生成模拟实验 →',exact=True).click();page.wait_for_url('**/practice/lab');frame=page.frame_locator('iframe[title="AI 动态原理仿真实验台"]');frame.get_by_role('heading',name='光学装置（验收夹具）').wait_for();assert refreshes==2;assert requests[-1]['imageDataUrl'].startswith('data:image/jpeg;base64,');assert len(requests[-1]['imageDataUrl'])<2621440
  iframe=page.locator('iframe');assert iframe.get_attribute('sandbox')=='allow-scripts';assert iframe.get_attribute('referrerpolicy')=='no-referrer';f=iframe.element_handle().content_frame();assert f.evaluate('()=>{try{return !!parent.document.body}catch{return false}}') is False;page.evaluate("window.postMessage({type:'argus-sim-error',message:'forged'},'*')");page.wait_for_timeout(100);assert page.get_by_text('沙盒运行报错',exact=True).count()==0
  frame.get_by_label('折射率',exact=True).fill('1.8');expect(frame.locator('output')).to_have_text('1.8');frame.get_by_role('button',name='记录当前数据点',exact=True).click()
  with page.expect_download() as d:page.get_by_role('button',name='导出 1 条记录',exact=True).click()
  assert '折射率' in Path(d.value.path()).read_text(encoding='utf-8-sig')
  with page.expect_download() as d:page.get_by_role('button',name='下载单文件 HTML',exact=True).click()
  downloaded=Path(d.value.path()).read_text(encoding='utf-8');assert 'Content-Security-Policy' in downloaded and '<canvas' in downloaded
  page.get_by_role('button',name='查看代码',exact=True).click();assert 'Content-Security-Policy' in page.get_by_label('仿真 HTML 代码').input_value();page.get_by_role('button',name='Close this dialog').click()
  f.evaluate("window.dispatchEvent(new ErrorEvent('error',{message:'fixture runtime error',lineno:42}))");page.get_by_text('沙盒运行报错',exact=True).wait_for();page.get_by_role('button',name='一键 AI 自动修复',exact=True).click();frame.get_by_role('heading',name='光学装置（修复夹具）').wait_for();assert not blocked,blocked
  page.screenshot(path=str(Path(tempfile.gettempdir())/'argus-practice-lab.png'),full_page=True);page.go_back();page.wait_for_url('**/college/practice');expect(page.get_by_label('实验名称（选填）')).to_have_value('光学折射与全反射');assert page.locator('iframe').count()==0
  print('PASS clean setup, file/image distinction, route history, SSE 401 refresh, sandbox/CSP, data export, HTML and repair')
  mode='hold';page.get_by_role('button',name='生成模拟实验 →',exact=True).click();page.wait_for_url('**/practice/lab');page.get_by_role('button',name='取消并返回',exact=True).click();page.wait_for_url('**/college/practice')
  mode='missing';page.get_by_role('button',name='生成模拟实验 →',exact=True).click();page.get_by_text('尚未配置 DEEPSEEK_API_KEY，当前可先使用内置确定性实验模板',exact=True).wait_for();page.get_by_role('button',name='改用内置公式画图器兜底',exact=True).click();page.locator('#experiment-workspace').wait_for();assert page.locator('#experiment-workspace polyline').count()>0
  select=page.get_by_label('切换内置 7 套经典实验',exact=True)
  for kind in ['rc','beam','normal','damped','projectile','power','binomial']:select.select_option(kind);assert page.locator('#experiment-workspace svg polyline').count()>0
  page.get_by_role('button',name='切换到深色模式',exact=True).click();page.set_viewport_size({'width':390,'height':900});page.wait_for_timeout(200);page.screenshot(path=str(Path(tempfile.gettempdir())/'argus-practice-lab-mobile.png'),full_page=True);assert page.evaluate('document.documentElement.scrollWidth<=innerWidth');page.get_by_role('button',name='← 返回重新提问 / 换材料',exact=True).click();page.wait_for_url('**/college/practice');assert page.evaluate('document.documentElement.scrollWidth<=innerWidth');page.screenshot(path=str(Path(tempfile.gettempdir())/'argus-practice-setup-dark.png'),full_page=True)
  assert not errors,errors;print('PASS cancel, missing-key friendly fallback, seven deterministic templates and dark mobile no overflow');browser.close()
if __name__=='__main__':main()
