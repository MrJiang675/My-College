"""Optional cloud-model smoke test, only on the isolated local argus_test instance."""
from playwright.sync_api import sync_playwright
BASE='http://127.0.0.1:15173/api/'
SOURCE='竞赛通知：报名仅面向信息工程学院大二、大三本科生。报名截止时间为2026年12月18日17:00。请提交报名表（PDF）及个人贡献说明（PDF），报送至学院教学办公室。通知未附本学年院级认定目录，院内初审日期需向老师核实。'
def main():
 with sync_playwright() as w:
  anonymous=w.request.new_context(base_url=BASE)
  assert anonymous.get('groups/collaboration').status==401
  def account():
   r=anonymous.post('auth/demo-guest');assert r.ok,r.status
   return w.request.new_context(base_url=BASE,extra_http_headers={'Authorization':'Bearer '+r.json()['data']['accessToken']})
  owner,other=account(),account()
  def read(response):
   assert response.ok,(response.status,response.json().get('message'))
   return response.json()['data']
  group=read(owner.post('groups',data={'name':'通知闭环实测小组','description':'【竞赛组队】目标：校园调查；缺口：前端同学'}))
  assert any(row['groupId']==group for row in read(owner.get('groups/collaboration')))
  assert not any(row['groupId']==group for row in read(other.get('groups/collaboration')))
  print('PASS live collaboration statistics scoped to membership; anonymous access denied')
  row=read(owner.get('college/workspace'));import json
  state=json.loads(row['payload']);state['entries']=[{'id':'owned-live','project':'校园调查','text':'本人负责清洗问卷并记录异常规则','team':'团队完成两个院系的调研报告','source':'校内课程报告，公开链接待补充'}]
  read(owner.put('college/workspace',data={'revision':row['revision'],'state':state}))
  profile={'school':'测试大学','college':'信息工程学院','major':'信息工程','grade':'大二'}
  notice=read(owner.post('college/notice',data={'question':'请给出资格、缺文件、问老师话术、时间和清单','source':SOURCE,'current':None,'campusProfile':profile,'enableProfileAdaptation':True},timeout=45000))
  for key in ['eligibility','missingDocuments','teacherInquiryScripts','deadlines','checklist']:assert key in notice,key
  assert notice['eligibility']['status'] in ['GREEN','YELLOW']
  assert all(quote in SOURCE for quote in notice['eligibility']['evidences'])
  assert notice['teacherInquiryScripts'] and notice['eligibility']['evidences']
  assert notice['deadlines'] and notice['checklist']
  assert notice['deadlines'][0]['dateTime'].startswith('2026-12-18')
  print('PASS live notice structured analysis with source-checked evidence and teacher inquiry')
  statement=read(owner.post('college/notice-application',data={'source':SOURCE,'selectedExperienceIds':['owned-live'],'campusProfile':profile},timeout=45000))
  assert '清洗' in statement['draft']
  assert other.post('college/notice-application',data={'source':SOURCE,'selectedExperienceIds':['owned-live']},timeout=45000).status==400
  print('PASS live notice application uses current-owner experiences only')
  project=read(owner.post('college/project',data={'question':'区分团队成果和本人贡献生成草稿','source':'项目：校园调查。队友小陈负责问卷。我负责清洗问卷并记录异常。团队完成两个院系的报告。证明位置是校内课程报告。','current':None},timeout=45000))
  assert project.get('personalContribution') and project.get('teamOutcome')
  assert '清洗' in project['personalContribution']
  print('PASS live project chat extraction separates individual contribution and team outcome')
  request={'resumeText':'信息工程专业大三。参与校园调查，声称效率提高但未留基线，个人行动与团队成果未完全拆分。','selectedExperiences':state['entries'],'targetRole':'数据分析实习','interviewType':'实习技术/业务面','campusProfile':profile}
  generated=read(owner.post('college/interview/generate',data=request,timeout=55000));assert len(generated['questions'])==5
  assert all(q.get('targetWeakness') for q in generated['questions'])
  request['answers']=[{'question':q['question'],'category':q['category'],'targetWeakness':q['targetWeakness'],'userAnswer':'本人负责清洗问卷和记录异常规则。没有保留效率基线，不能断言提高多少；原始记录和报告待补充。'} for q in generated['questions']]
  report=read(owner.post('college/interview/evaluate',data=request,timeout=55000));assert len(report['questionFeedbacks'])==5
  assert all(item.get('followUpHint') for item in report['questionFeedbacks'])
  print('PASS live weakness-driven interview and five follow-up hints')
  owner.dispose();other.dispose();anonymous.dispose()
if __name__=='__main__':main()
