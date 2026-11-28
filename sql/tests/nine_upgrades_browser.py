"""Run with Vite on 127.0.0.1:15173. API fixtures, not production/model acceptance."""
import io, json, tempfile
from pathlib import Path
from PIL import Image
from playwright.sync_api import sync_playwright

BASE='http://127.0.0.1:15173'

def main():
    state={'school':'测试大学','dept':'测试学院','major':'电气工程','grade':'大二','pages':{},'entries':[{'id':'owned-1','project':'电路课设','text':'本人完成传感器标定','source':'校内报告','type':'课程设计'}],'snapshots':[]}
    revision=0;refreshes=0;expired=False;extractions=0;last_chat={};errors=[]
    def api(route):
        nonlocal revision,refreshes,expired,extractions,last_chat
        req=route.request;path=req.url.split('/api/')[-1]
        user={'userId':1,'userCode':'demo_guest_fixture','displayName':'体验同学','systemRole':'USER','mustChangePassword':False,'passwordEnabled':False}
        if path=='auth/refresh':
            refreshes+=1;value={'accessToken':'fresh' if expired else 'initial','currentUser':user}
        elif expired and req.headers.get('authorization')!='Bearer fresh':
            return route.fulfill(status=401,json={'success':False,'message':'expired'})
        elif path=='auth/me':value=user
        elif path=='college/workspace':
            if req.method=='PUT':
                change=req.post_data_json
                assert change['revision']==revision
                state.clear();state.update(change['state']);revision+=1;value=revision
            else:value={'revision':revision,'payload':json.dumps(state)}
        elif path in ('groups/my','groups'):value={'ownedGroups':[],'joinedGroups':[],'pendingInvitations':[]}
        elif path=='documents':value=[]
        elif path=='college/files/extract-text':
            extractions+=1;assert b'filename=' in req.post_data_buffer
            value={'extractedText':'RC 电路：R=12kΩ，C=50μF，电源6V。','fileName':'image.jpg','fileType':'jpg','charCount':32,'truncated':False,'model':'vision-fixture'}
        elif path=='college/interview/generate':
            assert '标定' in req.post_data_json['resumeText']
            value={'questions':[{'id':f'q{i}','index':i,'category':'个人贡献','question':f'第{i}道问题：请解释个人贡献','focusPoint':'核实证据'} for i in range(1,6)]}
        elif path=='college/interview/evaluate':
            assert len(req.post_data_json['answers'])==5
            value={'overallScore':80,'summaryLevel':'良好','dimensions':[{'name':n,'score':80} for n in ['表达逻辑','经历真实度与证据感','专业深度','目标匹配度']],'questionFeedbacks':[{'score':80,'strengths':'行动清晰','weaknesses':'需要补充证明','improvedAnswerSample':'本人完成标定，报告位置【待补充】'} for _ in range(5)],'actionableAdvice':['补齐报告','录音练习','复习原理']}
        elif path=='assistant/sessions' and req.method=='POST':value={'sessionId':9}
        elif path=='assistant/chat/stream':
            last_chat=req.post_data_json
            return route.fulfill(content_type='text/event-stream',body='event: delta\ndata: {"event":"delta","delta":"**参数分析**：先核对假设。"}\n\nevent: done\ndata: {"event":"done","reply":"**参数分析**：先核对假设。"}\n\n')
        elif path.startswith('assistant/sessions'):value=[]
        elif path.startswith('qa/records'):value={'items':[],'total':0,'page':1,'pageSize':20}
        else:return route.fulfill(status=404,json={'success':False,'message':'fixture missing: '+path})
        route.fulfill(json={'success':True,'data':value})

    with sync_playwright() as w:
        browser=w.chromium.launch(channel='msedge')
        page=browser.new_page(viewport={'width':1440,'height':1000})
        page.on('pageerror',lambda e:errors.append(str(e)))
        page.on('dialog',lambda d:d.accept())
        page.route(BASE+'/api/**',api)
        page.goto(BASE+'/app/college/practice')
        page.get_by_role('heading',name='已注册确定性计算模型').wait_for()
        assert page.locator('.workspace-tree').is_visible()
        assert page.locator('.sidebar-nav').get_by_text('04 我的经历',exact=True).count()==0
        # Simultaneous REST 401s share one refresh and retry with the renewed header.
        before=refreshes;expired=True
        page.evaluate("async()=>{const {default:http}=await import('/src/api/http.ts');await Promise.all([http.get('/college/workspace'),http.get('/documents')])}")
        assert refreshes==before+1,(refreshes,before)
        print('PASS shared REST session refresh after expiry')
        # SSE retries only a rejected handshake, with cookie credentials.
        page.evaluate("async()=>{const {streamAssistantMessage}=await import('/src/api/assistant.ts');await streamAssistantMessage({sessionId:9,message:'测试',toolMode:'CHAT'},'stale',{onEvent:()=>{}})}")
        print('PASS SSE handshake refresh/retry')
        page.get_by_role('button',name='上传文件',exact=True).click()
        buf=io.BytesIO();Image.new('RGB',(1800,1200),'white').save(buf,format='PNG')
        page.locator('.material-uploader input[type=file]').set_input_files({'name':'screenshot.png','mimeType':'image/png','buffer':buf.getvalue()})
        page.get_by_text('提取完成，请核对修改后发送。',exact=True).wait_for()
        assert 'RC 电路' in page.get_by_role('textbox',name='材料原文',exact=True).input_value()
        count=extractions
        page.locator('.material-uploader input[type=file]').set_input_files({'name':'too-big.png','mimeType':'image/png','buffer':b'x'*(6*1024*1024)})
        page.get_by_text('图片上限 5 MB，请裁剪或压缩后重试。',exact=True).wait_for()
        assert extractions==count
        print('PASS compressed image extraction and oversized image rejected before API')
        page.get_by_role('button',name='问助手',exact=True).first.click()
        page.get_by_role('button',name='↗ 解释当前参数改变后曲线为什么这样变化',exact=True).click()
        page.locator('.context-messages').get_by_text('参数分析',exact=False).wait_for()
        assert last_chat.get('moduleCode')=='PRACTICE'
        assert 'RC 电路' in last_chat['message']
        page.keyboard.press('Escape')
        assert page.get_by_role('dialog',name='当前任务问助手').count()==0
        print('PASS contextual drawer streams with current material and module skill')
        page.goto(BASE+'/app/college/experience');page.wait_for_url('**/app/groups?tab=experience')
        page.get_by_role('button',name='我的经历 (1)',exact=True).count()
        page.locator('.entry-list').get_by_role('button',name='编辑',exact=True).click()
        page.get_by_label('个人贡献',exact=True).last.fill('本人完成标定与误差分析')
        page.get_by_role('button',name='完成编辑',exact=True).click()
        page.wait_for_timeout(1100)
        assert state['entries'][0]['text']=='本人完成标定与误差分析'
        print('PASS legacy experience redirect and editable saved contribution')
        page.goto(BASE+'/app/college/mock-interview')
        page.get_by_role('button',name='一键带入「我的经历」',exact=True).click()
        page.get_by_role('button',name='生成 5 道面试题',exact=True).click()
        for i in range(5):
            page.get_by_label('我的回答',exact=True).fill(f'本人完成标定的第{i+1}步，证据见项目报告。')
            if i<4:page.get_by_role('button',name='下一题',exact=True).click()
        page.get_by_role('button',name='提交答卷，生成 AI 评分与建议',exact=True).click()
        page.get_by_role('heading',name='良好',exact=True).wait_for()
        with page.expect_download():page.get_by_role('button',name='导出复盘 Markdown',exact=True).click()
        print('PASS interview import, five answers, report and Markdown export')
        page.evaluate('window.scrollTo(0,0)')
        page.screenshot(path=str(Path(tempfile.gettempdir())/'argus-nine-interview-light.png'),full_page=True)
        page.goto(BASE+'/app/qa');page.wait_for_url('**/app/intelligent?mode=knowledge')
        page.get_by_role('button',name='AI 对话助手',exact=True).click()
        page.wait_for_url('**/app/intelligent?mode=assistant')
        page.get_by_role('button',name='切换到深色模式',exact=True).click()
        assert page.evaluate('document.documentElement.dataset.theme')=='dark'
        page.goto(BASE+'/app/college/mock-interview')
        page.get_by_role('button',name='生成 5 道面试题',exact=True).wait_for()
        colors=page.locator('.interview-card').evaluate('(e)=>({fg:getComputedStyle(e).color,bg:getComputedStyle(e).backgroundColor})')
        assert colors['fg']!=colors['bg'],colors
        for width in [1440,390]:
            page.set_viewport_size({'width':width,'height':900});page.wait_for_timeout(300)
            assert page.evaluate('document.documentElement.scrollWidth<=innerWidth'),width
        assert page.locator('.app-layout').evaluate("e=>e.classList.contains('collapsed')")
        page.evaluate('window.scrollTo(0,0)')
        page.screenshot(path=str(Path(tempfile.gettempdir())/'argus-nine-mobile-dark.png'),full_page=True)
        print('PASS unified QA routes, dark theme and desktop/mobile layout')
        assert not errors,errors
        browser.close()
if __name__=='__main__':main()
