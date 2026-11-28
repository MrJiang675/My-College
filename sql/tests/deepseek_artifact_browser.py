"""Replay the last real DeepSeek artifact through the actual app sandbox; does not fabricate model output."""
import json,tempfile
from pathlib import Path
from playwright.sync_api import sync_playwright
BASE='http://127.0.0.1:15173'
def main():
 result=json.loads((Path(tempfile.gettempdir())/'argus-deepseek-live-simulation.json').read_text(encoding='utf-8'))
 with sync_playwright() as p:
  browser=p.chromium.launch(channel='msedge');context=browser.new_context(viewport={'width':1440,'height':1000});r=context.request.post(BASE+'/api/auth/demo-guest');assert r.ok;page=context.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
  page.route(BASE+'/api/college/practice/simulate-stream',lambda r:r.fulfill(content_type='text/event-stream',body='event: done\ndata: '+json.dumps(result)+'\n\n'))
  page.goto(BASE+'/app/college/practice');page.get_by_label('实验名称（选填）').fill('光学折射与全反射（真实生成回放）');page.get_by_role('button',name='生成模拟实验 →',exact=True).click();page.wait_for_url('**/practice/lab');frame=page.frame_locator('iframe');frame.locator('canvas,svg').first.wait_for();page.wait_for_timeout(1500)
  assert page.get_by_text('沙盒运行报错',exact=True).count()==0, page.locator('.alert').inner_text() if page.locator('.alert').count() else errors
  assert frame.locator('input[type="range"]').count()>=1
  print('PASS real generated artifact renders with sliders, no reported runtime error')
  print('Canvas count',frame.locator('canvas').count(),'SVG count',frame.locator('svg').count())
  assert '不存在' in frame.locator('body').inner_text();assert '```' not in frame.locator('body').inner_text()
  sliders=frame.locator('input[type=range]');sliders.nth(1).fill('0.75');sliders.nth(0).fill('60');page.wait_for_timeout(100);assert '48.6' in frame.locator('body').inner_text() and '全反射' in frame.locator('body').inner_text();print('PASS repaired optical boundary: no critical angle for air-to-glass; 48.6 degrees at n2=0.75')
  frame.get_by_role('button',name='记录当前数据点',exact=False).first.click();page.get_by_role('button',name='导出 1 条记录',exact=True).wait_for();print('PASS real generated record bridge enables CSV export')
  page.screenshot(path=str(Path(tempfile.gettempdir())/'argus-deepseek-real-lab.png'),full_page=True)
  assert not errors,errors;browser.close()
if __name__=='__main__':main()
