"""Seven-upgrade UI acceptance. API fixtures are explicit; not a live-model test."""
import json,tempfile,zipfile
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
BASE='http://127.0.0.1:15173'
SOURCE='报名仅面向信息工程学院大二、大三本科生。\n提交示例成绩单 PDF、示例报名表 XLSX、示例申请书 PDF。\n截止12月10日17:00，提交至学院办公室。\n详见附件1；纸质件退回时间另行通知。'
def main():
 state={'school':'测试大学','dept':'信息工程学院','major':'电气工程','grade':'大二','track':'实习','pages':{},'entries':[{'id':'own','project':'本人课程设计','text':'本人清洗数据并保留报告','source':'校内报告'}],'snapshots':[]}
 revision=0;errors=[];calls=[]
 def api(route):
  nonlocal revision
  req=route.request;path=req.url.split('/api/')[-1].split('?')[0];calls.append(path)
  user={'userId':1,'userCode':'demo_guest_upgrade','displayName':'验收同学','systemRole':'USER','mustChangePassword':False,'passwordEnabled':False}
  if path=='auth/refresh':value={'accessToken':'fixture','currentUser':user}
  elif path=='auth/me':value=user
  elif path=='college/workspace':
   if req.method=='PUT':state.clear();state.update(req.post_data_json['state']);revision+=1;value=revision
   else:value={'revision':revision,'payload':json.dumps(state)}
  elif path=='groups/my':value={'ownedGroups':[],'joinedGroups':[],'pendingInvitations':[]}
  elif path=='college/notices/fetch-url':value={'body':SOURCE,'title':'公开通知（测试）','publishedAt':'需核实','url':'https://example.edu.cn/notice'}
  elif path=='college/notice':value={'answer':'### 综合结论\n**请先核实附件**\n\n|事项|状态|\n|---|---|\n|报名|待确认|','eligibility':{'status':'GREEN','summary':'明示年级范围符合；综测需核实','evidences':[SOURCE.split('\n')[0]]},'missingDocuments':['院级认定目录和附件1'],'teacherInquiryScripts':['老师您好，请问附件1和提交份数如何获取？谢谢。'],'deadlines':[{'title':'报名截止','dateTime':'','needsVerify':True,'evidenceQuote':'截止12月10日17:00，提交至学院办公室。','location':'学院办公室','description':'提交材料'}],'checklist':[{'item':n,'required':True,'formatNote':f,'evidenceQuote':SOURCE.split('\n')[1]} for n,f in [('示例成绩单','PDF'),('示例报名表','XLSX'),('示例申请书','PDF')]]}
  elif path=='college/files/extract-text':value={'extractedText':'实验公式 y=A exp(-k x) cos(w x)，想观察衰减现象。','fileName':'实验材料.txt','charCount':35,'truncated':False}
  elif path=='college/practice':value={'answer':'### 实验条件\n**材料未给全参数，已按标准默认条件启动模拟实验**\n可调整滑块。','model':{'kind':'formula','title':'材料中的衰减曲线','expression':'A * exp(-k * x) * cos(w * x)','xVar':'x','xLabel':'时间 / s','yLabel':'振幅 / m','xMin':0,'xMax':10,'params':[{'name':'振幅','symbol':'A','value':1,'min':0,'max':10,'step':.1},{'name':'衰减系数','symbol':'k','value':.2,'min':0,'max':2,'step':.01},{'name':'角频率','symbol':'w','value':3,'min':.1,'max':10,'step':.1}]}}
  elif path=='assistant/sessions':value={'sessionId':9}
  elif path=='assistant/chat/stream':
   assert '数据分析实习' in req.post_data_json['message']
   return route.fulfill(content_type='text/event-stream',body='event: delta\ndata: '+json.dumps({'event':'delta','delta':'### 能力对标\n**有数据清洗记录**，请补全报告。'})+'\n\nevent: done\ndata: '+json.dumps({'event':'done','sessionId':9})+'\n\n')
  else:return route.fulfill(status=404,json={'success':False,'message':path})
  route.fulfill(json={'success':True,'data':value})
 with sync_playwright() as p:
  browser=p.chromium.launch(channel='msedge');context=browser.new_context(viewport={'width':1440,'height':1000},permissions=['clipboard-read','clipboard-write']);page=context.new_page();page.on('pageerror',lambda e:errors.append(str(e)));page.route(BASE+'/api/**',api)
  page.goto(BASE+'/app/college/opps');page.get_by_label('官网通知链接').fill('https://example.edu.cn/notice');page.get_by_role('button',name='抓取正文',exact=True).click();page.get_by_label('通知原文',exact=True).wait_for();expect(page.get_by_label('通知原文',exact=True)).to_have_value(SOURCE)
  page.get_by_role('button',name='开始综合分析并提取要求',exact=True).click();page.get_by_role('heading',name='综合结论',exact=True).wait_for();assert page.locator('.conclusions strong').inner_text()=='请先核实附件';assert page.locator('.conclusions table').count()==1
  page.get_by_role('button',name='全部加入清单',exact=True).click();page.get_by_role('button',name='下一步：进入「理清单」备齐并核对材料 →',exact=True).click();before=calls.count('college/files/extract-text');page.get_by_role('button',name='生成示例材料文件（含故意格式错误）',exact=True).click();page.get_by_text('文件扩展名与要求不符',exact=False).first.wait_for()
  page.get_by_role('button',name='⑤ 去办事',exact=True).click();assert page.get_by_role('button',name='纯前端一键打包提交材料（ZIP）',exact=True).is_disabled();page.get_by_role('button',name='④ 理清单',exact=True).click()
  articles=page.locator('[id^="material-"]');assert articles.count()==3
  for i in range(3):
   a=articles.nth(i);details=a.locator('details');details.evaluate('(e)=>e.open=true')
   if i==2:a.get_by_role('button',name='修复 / 替换示例为正确格式',exact=True).click()
   a.get_by_label('份数 / 提交次数（未说明先核实）',exact=True).fill('1');a.get_by_label('已核对文件内容确实满足要求',exact=True).check();a.get_by_label('已核实并确认提交份数 / 次数',exact=True).check()
  page.get_by_role('button',name='⑤ 去办事',exact=True).click()
  with page.expect_download() as d:page.get_by_role('button',name='纯前端一键打包提交材料（ZIP）',exact=True).click()
  with zipfile.ZipFile(d.value.path()) as z:assert z.testzip() is None;assert len(z.namelist())==4;assert '示例申请书.pdf' in z.namelist();assert '提交前核对清单.md' in z.namelist()
  assert calls.count('college/files/extract-text')==before,'Submission files left browser!'
  page.get_by_label('确认日期-0',exact=True).fill('2026-12-10T17:00');page.get_by_role('button',name='确认日期',exact=True).click()
  with page.expect_download() as d:page.get_by_role('button',name='导出此节点',exact=True).click()
  assert '20261210T090000Z' in Path(d.value.path()).read_text(encoding='utf-8')
  page.screenshot(path=str(Path(tempfile.gettempdir())/'argus-seven-notice.png'),full_page=True)
  with page.expect_download() as d:page.get_by_role('button',name='导出项目备份 (.json)',exact=True).click()
  backup=Path(d.value.path()).read_bytes();page.locator('input[accept=".json"]').set_input_files({'name':'backup.json','mimeType':'application/json','buffer':backup});page.get_by_text('已恢复文本要求。出于核对安全，文件、勾选与确认日期需要重新关联/确认。',exact=True).wait_for();page.get_by_role('button',name='⑤ 去办事',exact=True).click();assert page.get_by_role('button',name='纯前端一键打包提交材料（ZIP）',exact=True).is_disabled()
  print('PASS public source, original/attachments, candidates, bad-format repair, local ZIP, confirmed ICS and safe JSON import')
  page.goto(BASE+'/app/college/practice');page.get_by_role('button',name='或直接打开 7 套内置确定性经典实验 →',exact=True).click();page.wait_for_url('**/practice/lab');page.locator('#experiment-workspace').wait_for()
  for kind in ['rc','beam','normal','damped','projectile','power','binomial']:
   page.get_by_label('切换内置 7 套经典实验',exact=True).select_option(kind);assert page.locator('#experiment-workspace svg polyline').count()>0
  security=page.evaluate('''async()=>{const{renderSafeMarkdown}=await import('/src/utils/safeMarkdown.ts');const host=document.createElement('div');host.innerHTML=renderSafeMarkdown('### 标题\\n**加粗**\\n<script>window.XSS=1</script>\\n<img src=x onerror="window.XSS=2">\\n[bad](javascript:alert(1))\\n|a|b|\\n|---|---|\\n|c|d|');document.body.append(host);const safe=!host.querySelector('script,img,[onclick],[onerror],a[href^="javascript:"]')&&!window.XSS&&host.querySelector('h3')&&host.querySelector('strong')&&host.querySelector('table');host.remove();return !!safe}''');assert security
  page.screenshot(path=str(Path(tempfile.gettempdir())/'argus-seven-practice.png'),full_page=True);print('PASS legacy seven templates and safe Markdown; dynamic simulation covered in deepseek_practice_browser.py')
  page.goto(BASE+'/app/college/career');assert page.get_by_label('目标要求原文').count()==0;page.get_by_role('button',name='确认目标，进入下一步',exact=True).click();page.get_by_text('请先填写目标岗位 / 院校方向',exact=True).wait_for();page.get_by_role('button',name='数据分析实习',exact=True).click();page.get_by_role('button',name='确认目标，进入下一步',exact=True).click();page.get_by_role('button',name='材料已备齐，进入能力对标',exact=True).click();page.get_by_text('请补齐高亮内容，或明确选择只看通用准备建议',exact=True).wait_for();page.get_by_role('button',name='载入示例要求',exact=True).click();page.get_by_label('本人课程设计',exact=False).check();page.get_by_role('button',name='材料已备齐，进入能力对标',exact=True).click();page.get_by_role('heading',name='申请要求逐项对照',exact=True).wait_for();page.get_by_role('button',name='开始 AI 对标与打磨',exact=True).click();page.get_by_role('heading',name='能力对标',exact=True).wait_for();page.wait_for_timeout(1200)
  page.reload();page.get_by_role('heading',name='从证据到申请行动',exact=True).wait_for();page.screenshot(path=str(Path(tempfile.gettempdir())/'argus-seven-career.png'),full_page=True);page.get_by_role('button',name='基于当前目标与经历开始模拟面试',exact=True).click();page.wait_for_url('**/mock-interview?track=*');expect(page.get_by_label('目标岗位 / 专业方向',exact=True)).to_have_value('数据分析实习')
  print('PASS progressive unlock, missing hints, AI Markdown, saved direction resume and interview context')
  page.get_by_role('button',name='切换到深色模式',exact=True).click()
  for path in ['opps','career','practice']:
   page.goto(BASE+'/app/college/'+path);page.set_viewport_size({'width':390,'height':900});page.wait_for_timeout(300);assert page.evaluate('document.documentElement.scrollWidth<=innerWidth'),path
  page.screenshot(path=str(Path(tempfile.gettempdir())/'argus-seven-dark-mobile.png'),full_page=True)
  assert not errors,errors
  browser.close();print('PASS mobile no overflow and no browser runtime errors')
if __name__=='__main__':main()
