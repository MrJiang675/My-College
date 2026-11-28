"""Requires the isolated local application at 15173. Creates disposable demo users."""
import json
from pathlib import Path
from playwright.sync_api import sync_playwright

BASE="http://127.0.0.1:15173"
def sign_in(browser):
    context=browser.new_context()
    page=context.new_page()
    errors=[]
    page.on("pageerror",lambda e:errors.append(str(e)))
    page.goto(BASE+"/login")
    with page.expect_response("**/api/auth/demo-guest") as response:
        page.get_by_role("button",name="评委一键体验（独立空间，免注册）").click()
    session=response.value.json()["data"]
    page.wait_for_url("**/app/college")
    page.get_by_role("heading",name="我的大学",exact=True).wait_for()
    if page.get_by_role("button",name="不再提示",exact=True).count(): page.get_by_role("button",name="不再提示",exact=True).click()
    return context,page,session,errors

def main():
    with sync_playwright() as w:
        browser=w.chromium.launch(channel="msedge")
        c1,p1,s1,e1=sign_in(browser)
        c2,p2,s2,e2=sign_in(browser)
        assert s1["currentUser"]["userId"]!=s2["currentUser"]["userId"]
        for page,school in [(p1,"第一位评委的学校"),(p2,"第二位评委的学校")]:
            page.get_by_role("button",name="校园档案",exact=False).first.click()
            page.get_by_label("学校",exact=True).fill(school)
            page.get_by_role("button",name="保存档案",exact=True).click()
            page.wait_for_timeout(700)
        for page,school in [(p1,"第一位评委的学校"),(p2,"第二位评委的学校")]:
            page.reload()
            page.get_by_text(school,exact=False).first.wait_for()
            assert page.get_by_text("保存冲突",exact=True).count()==0
        print("PASS independent browser users and saved profiles")
        p1.goto(BASE+"/app/college/practice")
        p1.get_by_role("button",name="正态分布与均值抽样 · 数学与统计",exact=True).click()
        p1.get_by_text("标准误",exact=True).first.wait_for()
        p1.get_by_role("button",name="参数对比挑战",exact=True).count()
        p1.get_by_role("button",name="保存当前快照",exact=True).click()
        p1.wait_for_timeout(600)
        p1.reload()
        p1.get_by_text("正态分布与均值抽样",exact=True).first.wait_for()
        print("PASS normal model and snapshot recovery")
        p1.goto(BASE+"/app/college/learn")
        preset=p1.get_by_role("button",name="应用拉格朗日中值定理要先核对哪些前提？",exact=True)
        with p1.expect_request("**/samples/sample-course-notes.md"):preset.click()
        p1.get_by_text("内置样例快速演示 · 支持继续自由追问",exact=True).wait_for()
        p1.get_by_text("核对资料原文",exact=False).first.wait_for()
        calls=[]
        def reading(route):
            calls.append(route.request.post_data_json["question"])
            route.fulfill(json={"success":True,"data":{"answer":"自定义后端回答","quotes":["连续性要求包含两个端点。"]}})
        p1.route("**/api/college/reading",reading)
        p1.get_by_label("给助手的任务").fill("解释一下端点条件，我自己提出的问题")
        p1.get_by_role("button",name="发送 →",exact=True).click()
        p1.get_by_text("自定义后端回答",exact=True).wait_for()
        assert calls==["解释一下端点条件，我自己提出的问题"]
        print("PASS preset is labeled and custom question requests API")
        group=c1.request.post(BASE+"/api/groups",headers={"Authorization":"Bearer "+s1["accessToken"]},data={"name":"评审上传隔离组"}).json()["data"]
        p1.goto(BASE+"/app/documents")
        p1.get_by_role("button",name="上传文件",exact=True).click()
        p1.locator("input[type=file]").set_input_files({"name":"too-large.pdf","mimeType":"application/pdf","buffer":b"x"*(51*1024*1024)})
        p1.get_by_text("当前单文件上限为",exact=False).wait_for()
        upload_calls=[]
        p1.on("request",lambda r:upload_calls.append(r.url) if "/documents/upload" in r.url else None)
        p1.wait_for_timeout(600)
        assert not upload_calls
        p1.get_by_text("关闭提示",exact=True).click()
        p1.get_by_role("button",name="取消",exact=True).click()
        p1.goto(BASE+"/app/college/learn")
        print("PASS oversized upload rejected before any upload request")
        p1.goto(BASE+"/app/college/project")
        p1.wait_for_url("**/app/groups?tab=projects")
        p1.get_by_role("button",name="载入典型学生成长示例",exact=True).click()
        p1.get_by_label("粘贴零散记录",exact=True).fill("项目：图书馆调研。本人负责问卷校验。成果 https://example.org/report")
        p1.get_by_role("button",name="整理为项目草稿",exact=True).click()
        p1.get_by_role("button",name="确认收录至我的经历",exact=True).click()
        p1.wait_for_timeout(700)
        p1.goto(BASE+"/app/college/experience")
        p1.get_by_text("图书馆调研",exact=True).wait_for()
        p1.goto(BASE+"/app/college/career")
        p1.get_by_role("heading",name="申请要求逐项对照",exact=True).wait_for()
        p1.goto(BASE+"/app/college/campus")
        p1.wait_for_url("**/app/college/opps?view=affairs")
        p1.get_by_role("heading",name="通知防坑行动台",exact=True).wait_for()
        print("PASS old-route redirects and project-to-experience-to-career")
        p1.goto(BASE+"/app/college/practice")
        p1.get_by_role("button",name="RC 充电",exact=True).click()
        for label in ["切换到深色模式","切换到浅色模式"]:
            p1.get_by_role("button",name=label,exact=True).click()
            p1.get_by_role("heading",name="RC 充电",exact=True).wait_for()
            assert p1.locator(".lab svg polyline").count()>0
        p1.screenshot(path=str(Path(__file__).resolve().parents[2]/"Argus-frontend/dist/acceptance-desktop.png"),full_page=True)
        for width in [1440,390]:
            p1.set_viewport_size({"width":width,"height":900})
            p1.wait_for_timeout(400)
            if not p1.evaluate("document.documentElement.scrollWidth<=innerWidth"):
                print(p1.evaluate("Array.from(document.querySelectorAll('body *')).filter(e=>e.getBoundingClientRect().right>innerWidth+1).slice(0,8).map(e=>[e.tagName,e.className,Math.round(e.getBoundingClientRect().width)])"))
                raise AssertionError(width)
        preview=browser.new_context()
        public=preview.new_page()
        public.route(BASE+"/api/**",lambda r:r.abort())
        public.goto(BASE+"/preview?module=learn")
        public.get_by_role("button",name="应用拉格朗日中值定理要先核对哪些前提？",exact=True).click()
        public.get_by_text("内置样例快速演示 · 支持继续自由追问",exact=True).wait_for()
        preview.close()
        print("PASS no-backend public sample preview")
        assert not e1+e2,e1+e2
        c1.close();c2.close();browser.close()
if __name__=="__main__":main()
