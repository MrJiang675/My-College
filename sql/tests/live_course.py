"""Live smoke test against a local, disposable instance with model and storage configured."""
import json
import time
from playwright.sync_api import sync_playwright
import live_workspace as api

SOURCE = "电容初始电压为零。理想串联 RC 电路使用直流阶跃 6 V、电阻 12 kΩ 和电容 50 μF。时间常数为 0.6 秒。实验要求在三个时间常数后记录电压。"

def main():
    api.BASE = "http://127.0.0.1:15173/api"
    token = api.login(api.account())
    group = api.call("POST", "/groups", {"name":"电路课讲义"}, token)
    with sync_playwright() as w:
        c = w.request.new_context(base_url=api.BASE+"/", extra_http_headers={"Authorization":"Bearer "+token})
        upload = c.post("documents/upload", multipart={"groupId":str(group),"file":{
            "name":"circuit.txt","mimeType":"text/plain","buffer":SOURCE.encode()}})
        assert upload.ok, upload.status
        doc_id = upload.json()["data"]
        for _ in range(90):
            docs=c.get("documents",params={"groupId":group}).json()["data"]
            doc=next(v for v in docs if v["documentId"]==doc_id)
            if doc["status"]=="READY":break
            assert doc["status"]!="FAILED",doc["failureReason"]
            time.sleep(1)
        assert doc["status"]=="READY"
        response=c.post("qa/stream-ask",data={"groupId":group,"question":"讲义要求在几个时间常数后记录电压？"},timeout=90000)
        assert response.ok
        answer=[];citations=[];evidence=[]
        for event in response.text().replace("\r\n","\n").split("\n\n"):
            lines=event.splitlines()
            tag=next((s[6:].strip() for s in lines if s.startswith("event:")),"")
            data="\n".join(s[5:].strip() for s in lines if s.startswith("data:"))
            if tag=="token":answer.append(data)
            if tag=="citations":citations=json.loads(data)
            if tag=="evidence-overview":evidence=json.loads(data)["groups"]
            if tag=="error":raise AssertionError(data)
        assert any(v in "".join(answer) for v in ("三","3")),answer
        assert any(v["documentId"]==doc_id for v in citations)
        assert any(g["documentId"]==doc_id and any("时间常数" in (s["snippet"] or "") for s in g["snippets"]) for g in evidence)
        print("PASS: real upload, ingestion, streamed answer and source citation")
        current=None
        for q,expected in [("请生成指导书中的充电实验",[12,50,6]),("电阻改成上次的两倍，保留电容与电源。",[24,50,6])]:
            res=c.post("college/practice",data={"question":q,"source":SOURCE,"current":current},timeout=90000)
            assert res.ok
            current=res.json()["data"]["model"]
            assert current["kind"]=="rc" and current["values"]==expected,current
        print("PASS: guide interpretation and follow-up parameter update")
        c.dispose()

if __name__=="__main__":main()

