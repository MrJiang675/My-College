"""Run against the built Vue app. APIs are fixtures, not live credentials."""
import json
import threading
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from playwright.sync_api import sync_playwright

root = Path(__file__).resolve().parents[1]
class Spa(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(root/'build/Argus-frontend/dist'), **kwargs)
    def do_GET(self):
        if self.path.startswith('/app/'):
            self.path = '/index.html'
        super().do_GET()

def check():
    server = ThreadingHTTPServer(('127.0.0.1',0), Spa)
    threading.Thread(target=server.serve_forever,daemon=True).start()
    state = {}
    revision = 0
    def api(route):
        nonlocal state,revision
        path = route.request.url.split('/api/')[-1]
        user = dict(userId=1,userCode='student',displayName='学生',systemRole='USER',mustChangePassword=False)
        if path=='auth/refresh': value=dict(accessToken='browser-fixture',currentUser=user)
        elif path=='auth/me': value=user
        elif path=='groups/my': value=dict(ownedGroups=[dict(groupId=9,groupName="课堂资料")],joinedGroups=[],pendingInvitations=[])
        elif path=='qa/stream-ask':
            question=route.request.post_data_json['question']
            assert len(question)<=2000
            if '追问' in question: assert question.endswith('保留本次问题')
            text='讲义解读'*600
            refs=json.dumps([dict(fileName='课堂讲义',chunkIndex=2,snippet='引用段落')])
            return route.fulfill(content_type='text/event-stream',body=f"event: token\ndata: {text}\n\nevent: citations\ndata: {refs}\n\n")
        elif path=='college/workspace':
            if route.request.method=='PUT':
                change=route.request.post_data_json
                assert change['revision']==revision
                state=change['state']
                revision+=1
                value=revision
            else: value=dict(revision=revision,payload=json.dumps(state))
        else: return route.fulfill(status=404)
        route.fulfill(json=dict(success=True,data=value))
    try:
        with sync_playwright() as w:
            with w.chromium.launch(channel='msedge') as browser:
                page=browser.new_page()
                page.route('**/api/**',api)
                page.goto(f'http://127.0.0.1:{server.server_port}/app/college/practice')
                form=page.locator('main.college form').filter(has=page.get_by_label('给助手的任务'))
                assert page.locator('article svg').count()==0
                assert form.get_by_role('button').count()==1
                form.get_by_label('给助手的任务').fill('使用RC充电示例')
                form.get_by_text('发送').click()
                page.get_by_text('τ = 1 s',exact=False).wait_for()
                form.get_by_label('给助手的任务').fill('把电阻翻倍')
                form.get_by_text('发送').click()
                page.get_by_text('τ = 2 s',exact=False).wait_for()
                page.get_by_text('保存实验条件与结果').click()
                page.get_by_text('已同步',exact=True).wait_for()
                page.reload()
                page.get_by_text('τ = 2 s',exact=False).wait_for()
                assert state['snapshots'][0]['model']['values']==[20,100,5]
                page.goto(f'http://127.0.0.1:{server.server_port}/app/college/learn')
                page.get_by_label('回答依据').select_option('9')
                question=page.get_by_label('给助手的任务')
                for text in ['解释讲义','追问'+'知'*1500+'保留本次问题']:
                    question.fill(text)
                    page.get_by_text('发送 →',exact=True).click()
                    page.wait_for_function("document.querySelector('[aria-label=给助手的任务]').value === ''")
                page.get_by_text('核对资料原文').first.click()
                page.get_by_text('课堂讲义 · 片段 2　引用段落').first.wait_for()
                page.set_viewport_size(dict(width=390,height=844))
                page.wait_for_function('document.documentElement.scrollWidth <= innerWidth',timeout=3000)
    finally: server.shutdown()

if __name__=='__main__': check()
