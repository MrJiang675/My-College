"""Opt-in smoke against isolated argus_test; uses real upstream AI, no credentials in code."""
from playwright.sync_api import sync_playwright
BASE='http://127.0.0.1:15174/api/'
def main():
 with sync_playwright() as p:
  guest=p.request.new_context(base_url=BASE)
  assert guest.post('college/notices/fetch-url',data={'url':'https://example.com'}).status==401
  r=guest.post('auth/demo-guest');assert r.ok,r.status
  client=p.request.new_context(base_url=BASE,extra_http_headers={'Authorization':'Bearer '+r.json()['data']['accessToken']})
  for url in ['http://127.0.0.1/','http://10.0.0.1/','http://192.168.1.1/','http://[::1]/','file:///etc/passwd']:
   r=client.post('college/notices/fetch-url',data={'url':url},timeout=12000);assert r.status==400,(url,r.status)
  print('PASS live auth and internal/file URL rejection')
  for source,expected in [('物理实验：研究阻尼简谐振动，振幅1米，阻尼比0.15，其余参数未给。','damped'),('实验关系 y = A * exp(-k * x) * cos(w * x)，A=1，k=0.2，w=3，x范围0到10。请按通用 formula 画这条数学函数，不要套内置物理实验。','formula')]:
   r=client.post('college/practice',data={'question':'想做模拟实验，请按给定关系启动画图，缺少的次要参数用标准默认条件并明确说明','source':source,'current':None},timeout=45000)
   if not r.ok:raise AssertionError(('practice upstream failed',r.status,r.json().get('message')))
   data=r.json()['data'];assert data.get('model',{}).get('kind')==expected,(expected,data.get('model'))
   print('PASS live AI protocol:',expected)
  r=client.post('college/notices/fetch-url',data={'url':'https://www.cs.tsinghua.edu.cn/index/tzgg.htm'},timeout=12000)
  if r.ok:assert r.json()['data']['body'];print('PASS live public HTML extraction')
  else:print('LIMIT live external public site unavailable:',r.status,r.json().get('message'))
  client.dispose();guest.dispose()
if __name__=='__main__':main()
