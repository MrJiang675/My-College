"""Strategic feature UI checks. Vite 15173; explicitly mocked APIs, not model acceptance."""
import json,tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright
BASE='http://127.0.0.1:15173'
def main():
 state={'school':'测试大学','dept':'信息工程学院','major':'信息工程','grade':'大二','track':'实习','pages':{'career-实习':{'draft':'','source':'数据分析岗位','sourceName':'岗位要求','turns':[],'selected':['own-1'],'targetRole':'数据分析实习'}},'entries':[{'id':'own-1','project':'校园调查','text':'本人清洗数据','team':'团队完成调研报告','source':'校内报告'}],'snapshots':[]}
 revision=0;errors=[];created=[];calls=[]
 groups=[{'groupId':10,'groupName':'校园创新队','groupCode':'TEAM10','description':'【竞赛组队】目标：创新赛；缺口：前端/PPT队友','pendingRequestCount':1},{'groupId':11,'groupName':'电路课程组','groupCode':'TEAM11','description':'【课程设计】整理课件'}]
 def api(route):
  nonlocal revision
  req=route.request;path=req.url.split('/api/')[-1].split('?')[0];calls.append(path)
  user={'userId':1,'userCode':'demo_guest_test','displayName':'测试同学','systemRole':'USER','mustChangePassword':False,'passwordEnabled':False}
  if path=='auth/refresh':value={'accessToken':'test-session','currentUser':user}
  elif path=='auth/me':value=user
  elif path=='college/workspace':
   if req.method=='PUT':
    body=req.post_data_json
    if body['revision']!=revision:return route.fulfill(status=409,json={'success':False,'message':'conflict'})
    state.clear();state.update(body['state']);revision+=1;value=revision
   else:value={'revision':revision,'payload':json.dumps(state)}
  elif path=='groups/my':value={'ownedGroups':groups,'joinedGroups':[],'pendingInvitations':[]}
  elif path=='groups/collaboration':value=[{'groupId':10,'memberCount':3,'documentCount':2,'readyCount':2,'pendingRequestCount':1},{'groupId':11,'memberCount':2,'documentCount':1,'readyCount':1,'pendingRequestCount':0}]
  elif path=='groups' and req.method=='POST':created.append(req.post_data_json);value=12
  elif path.endswith('/members'):value=[{'userId':1,'userCode':'one','displayName':'测试同学','role':'OWNER'},{'userId':2,'userCode':'two','displayName':'队友甲','role':'MEMBER'}]
  elif path.startswith('groups/') and ('requests' in path or 'invitations' in path):value=[]
  elif path=='documents':value=[]
  elif path=='college/project':value={'answer':'已区分贡献','projectName':'校园调查项目','teamOutcome':'团队完成两个院系的报告','personalContribution':'本人清洗数据并记录规则','evidenceLocation':'校内报告','missingEvidence':['公开链接'],'quotes':[]}
  elif path=='college/notice-application':
   assert req.post_data_json['selectedExperienceIds']==['own-1']
   value={'draft':'报名理由：本人负责数据清洗。个人优势：有规则记录。证明待补：报告公开链接。'}
  elif path=='college/interview/generate':
   body=req.post_data_json;assert body['targetRole']=='数据分析实习';assert body['selectedExperiences'][0]['id']=='own-1';assert body['interviewType']=='实习技术/业务面'
   value={'questions':[{'id':f'q{i}','index':i,'category':'贡献核实','question':f'第{i}题：你本人完成什么？','focusPoint':'个人证据','targetWeakness':'简历称参与，但缺少个人行动证据'} for i in range(1,6)]}
  elif path=='college/interview/evaluate':
   assert req.post_data_json['answers'][0]['targetWeakness'].startswith('简历称')
   value={'overallScore':70,'summaryLevel':'良好','dimensions':[{'name':n,'score':70} for n in ['表达逻辑','经历真实度与证据感','专业深度','目标岗位/院校匹配度']],'questionFeedbacks':[{'score':70,'strengths':'说出行动','weaknesses':'补充证据','improvedAnswerSample':'本人清洗数据，证明链接【待补充】','followUpHint':'若复现你的清洗规则，需要哪些原始记录？'} for _ in range(5)],'actionableAdvice':['整理规则','补全证明','录音训练']}
  else:return route.fulfill(status=404,json={'success':False,'message':path})
  route.fulfill(json={'success':True,'data':value})
 with sync_playwright() as w:
  browser=w.chromium.launch(channel='msedge');context=browser.new_context(viewport={'width':1440,'height':1000},permissions=['clipboard-read','clipboard-write'])
  page=context.new_page();page.on('pageerror',lambda e:errors.append(str(e)));page.route(BASE+'/api/**',api)
  page.goto(BASE+'/app/college/opps');panel=page.get_by_role('region',name='通知防坑行动台')
  panel.get_by_role('button',name='载入竞赛招募样例',exact=True).click();panel.get_by_text('绿灯 · 明示范围符合',exact=True).wait_for()
  panel.get_by_label('年级',exact=True).select_option('大一');panel.get_by_text('红灯 · 明示条件不符',exact=True).wait_for();assert panel.get_by_role('button',name='带入「我的经历」生成报名陈述').count()==0
  panel.get_by_label('年级',exact=True).select_option('大三');panel.get_by_text('绿灯 · 明示范围符合',exact=True).wait_for()
  panel.get_by_role('button',name='一键复制问老师话术',exact=True).click();panel.get_by_text('已复制，可直接发给辅导员/教务老师',exact=True).wait_for()
  with page.expect_download() as download:panel.get_by_role('button',name='一键导出日历 (.ics)',exact=True).click()
  text=Path(download.value.path()).read_text(encoding='utf-8');assert 'BEGIN:VEVENT' in text and 'TRIGGER:-P1D' in text
  panel.get_by_label('校园调查',exact=True).check();panel.get_by_role('button',name='带入「我的经历」生成报名陈述',exact=True).click();panel.get_by_role('textbox',name='报名陈述草稿').wait_for()
  page.wait_for_timeout(1100);assert '报名理由' in state['pages']['career-实习']['applicationDraft']
  page.evaluate('window.scrollTo(0,0)');page.screenshot(path=str(Path(tempfile.gettempdir())/'argus-strategy-notice.png'),full_page=True)
  print('PASS notice profile verdicts, teacher copy, calendar and owned-experience statement')
  page.goto(BASE+'/app/groups?tab=members');hub=page.locator('.collaboration-overview');hub.get_by_role('button',name='科研合作',exact=True).click();assert hub.get_by_role('heading',name='校园创新队').count()==0
  hub.get_by_role('button',name='竞赛组队',exact=True).click();hub.get_by_role('heading',name='校园创新队').wait_for()
  hub.get_by_role('button',name='一键复制招人/邀请文案',exact=True).click();assert 'TEAM10' in page.evaluate('navigator.clipboard.readText()')
  hub.get_by_role('button',name='进入小组工作区',exact=True).click();page.wait_for_url('**groupId=10')
  page.get_by_label('粘贴零散记录',exact=True).fill('项目：校园调查。我负责清洗数据，团队形成报告。')
  page.get_by_role('button',name='整理为项目草稿',exact=True).click();page.get_by_role('textbox',name='我的贡献',exact=True).wait_for();assert page.get_by_role('textbox',name='我的贡献',exact=True).input_value().startswith('本人')
  page.get_by_role('button',name='确认收录至我的经历',exact=True).click();page.wait_for_timeout(1100)
  assert state['entries'][0]['groupId']==10 and state['entries'][0]['team'].startswith('团队')
  print('PASS scenario discovery, invite copy and AI project-to-experience separation')
  page.goto(BASE+'/app/college/career');page.get_by_role('link',name='开始模拟面试',exact=True).click();page.wait_for_url('**/app/college/mock-interview?track=*')
  page.get_by_role('button',name='生成 5 道面试题',exact=True).click();page.get_by_text('面试官追问意图（命中简历哪处薄弱点）',exact=True).click();page.get_by_text('简历称参与，但缺少个人行动证据',exact=True).wait_for()
  for i in range(5):
   page.get_by_label('我的回答',exact=True).fill('本人完成清洗规则，原始记录待补充。')
   if i<4:page.get_by_role('button',name='下一题',exact=True).click()
  page.get_by_role('button',name='提交答卷，生成 AI 评分与建议',exact=True).click();page.get_by_text('若复现你的清洗规则，需要哪些原始记录？',exact=False).first.wait_for()
  with page.expect_download() as download:page.get_by_role('button',name='导出复盘 Markdown',exact=True).click()
  assert '二轮追问' in Path(download.value.path()).read_text(encoding='utf-8')
  print('PASS selected experiences and target carried into weakness-driven interview and follow-up report')
  page.route(BASE+'/api/college/notice',lambda r:r.fulfill(json={'success':True,'data':{'answer':'旧版回答','quotes':[]}}))
  page.goto(BASE+'/app/college/opps');page.get_by_role('textbox',name='材料原文',exact=True).fill('旧服务兼容性测试：具体资格以原文为准。')
  page.get_by_role('button',name='按当前档案重新核查',exact=True).click();page.get_by_text('后端未提供完整资格结构，请核对原文和档案，或升级配套后端。',exact=True).wait_for()
  print('PASS older notice backend response safely falls back to yellow without page error')
  page.get_by_role('button',name='切换到深色模式',exact=True).click()
  for width in [1440,390]:
   page.set_viewport_size({'width':width,'height':900});page.wait_for_timeout(250);assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
  assert not errors,errors
  context.close();browser.close()
if __name__=='__main__':main()
