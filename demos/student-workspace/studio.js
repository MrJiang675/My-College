const sampleGuide='悬臂梁静载实验\n梁长：1 m；截面宽度：20 mm；截面高度：40 mm；载荷：50 N；材料：钢。\n左端固定，在梁上施加竖向集中力，观察自由端位移。';
function studioPage(){return `<section class="studio"><small>专业实践　/　力学实验</small><h1>你想验证什么？</h1><div class="bench"><article class="paper"><div class="heading"><h2>悬臂梁 · 静载挠度</h2><small>左端固定</small></div><div class="numbers"><b id="tip"></b> mm <small>自由端向下位移</small></div><canvas class="plot" id="plot" aria-label="梁的变形对比曲线"></canvas><div id="legend"></div><label>载荷位置 <output id="position"></output><input type="range" id="loadAt" min="5" max="100" step="1"></label><small id="conditions"></small><details><summary>模型与指导书</summary><p id="guideText"></p><small>矩形等截面，小变形线弹性模型。钢取 E=200 GPa，铝取 E=69 GPa。图中竖向变形按比例放大。</small></details><button id="keep" class="subtle">保存本次对比</button><p id="saved" role="status"></p></article><section class="talk"><h2>实验对话</h2><p class="muted">上传指导书，或直接用当前示例试验。</p><label>导入文本指导书（.txt / .md）<input id="guideFile" type="file" accept=".txt,.md,text/plain"></label><div id="turns" aria-live="polite"></div><form id="say"><label>调整实验<textarea id="request" placeholder="把材料换成铝，和刚才比较"></textarea></label><div class="row"><button class="btn dark">运行 →</button><button type="button" class="subtle" data-ask="把载荷移到中点">移到中点</button><button type="button" class="subtle" data-ask="把材料换成铝">换成铝</button></div></form><footer>本地规则演示 · 支持材料、梁长、截面尺寸、载荷与位置。可在左侧展开示例指导书查看输入格式。</footer></section></div></section>`}
function mountStudio(record){
 const base={length:1,width:20,height:40,force:50,at:1,material:'钢'};
 const state=record.studio ||= {now:{...base},prior:null,guide:sampleGuide,turns:[]};
 const end=(m,x=m.length)=>{const a=m.at*m.length,E=(m.material==='钢'?200:69)*1e9,I=(m.width/1000)*(m.height/1000)**3/12;return 1000*m.force*(x<=a?x*x*(3*a-x):a*a*(3*x-a))/(6*E*I)};
 function chart(){
  const c=$('plot'),w=c.clientWidth,h=c.clientHeight;if(!w)return;
  c.width=w*devicePixelRatio;c.height=h*devicePixelRatio;const g=c.getContext('2d');g.scale(devicePixelRatio,devicePixelRatio);
  const m=state.now,old=state.prior,L=Math.max(m.length,old?.length||0),peak=Math.max(end(m),old?end(old):0),span=w-62;
  const X=x=>30+span*x/L,Y=y=>54+128*y/peak;
  g.strokeStyle='#d6dfcf';for(let i=0;i<5;i++){g.beginPath();g.moveTo(24,54+i*32);g.lineTo(w-15,54+i*32);g.stroke()}
  function curve(model,color,dash){g.beginPath();g.strokeStyle=color;g.lineWidth=3;g.setLineDash(dash);for(let j=0;j<=100;j++){const x=model.length*j/100;j?g.lineTo(X(x),Y(end(model,x))):g.moveTo(X(x),Y(0))}g.stroke();g.setLineDash([])}
  if(old)curve(old,'#b56a45',[5,5]);curve(m,'#295842',[]);g.fillStyle='#365b46';g.fillRect(23,41,7,26);g.font='13px sans-serif';g.fillText('↓ '+m.force+' N',Math.min(w-50,X(m.length*m.at)),39);g.fillText('固定端',12,h-13);g.fillText(L+' m',w-53,h-13);
 }
 function paint(){
  const m=state.now;$('tip').textContent=end(m).toFixed(3);$('loadAt').value=m.at*100;$('position').textContent=Math.round(m.at*100)+'% 梁长';
  $('conditions').textContent=m.material+' · 梁长 '+m.length+' m · 截面 '+m.width+' × '+m.height+' mm · 集中力 '+m.force+' N';
  $('guideText').textContent=state.guide;$('legend').textContent='实线 · 当前'+(state.prior?'　虚线 · 上一次（'+end(state.prior).toFixed(3)+' mm）':'');
  $('turns').replaceChildren();for(const [who,msg] of state.turns){const el=document.createElement('p'),b=document.createElement('b');el.className='turn';b.textContent=who;el.append(b,document.createTextNode(msg));$('turns').append(el)}$('turns').scrollTop=$('turns').scrollHeight;
  $('saved').textContent=state.saved||'';chart();save();
 }
 function run(text,file=false){
  if(!text.trim())return;let m={...(file?base:state.now)},found=false;
  const reply=msg=>{state.turns.push([file?'指导书':'我',file?'导入指导书':text],['实验助手',msg]);state.turns=state.turns.slice(-16);paint()};
  if(/简支|两端固定|均布|动力学/.test(text)){reply('当前实验台支持左端固定、单个集中力。请换用这一模型的指导书。');return}
  for(const [re,k] of [[/(?:梁长|长度)\s*[:：为到成]?\s*([\d.]+)\s*(mm|cm|m|毫米|厘米|米)/i,'length'],[/宽(?:度)?\s*[:：为到成]?\s*([\d.]+)\s*(mm|毫米)/i,'width'],[/高(?:度)?\s*[:：为到成]?\s*([\d.]+)\s*(mm|毫米)/i,'height'],[/(?:集中力|载荷|力)\s*[:：为到成]?\s*([\d.]+)\s*(N|牛顿)/i,'force']]){
   const hit=text.match(re);if(hit){m[k]=Number(hit[1])*(k==='length'?({mm:.001,毫米:.001,cm:.01,厘米:.01}[hit[2]]||1):1);found=true}
  }
  if(/[铝钢]/.test(text)){m.material=text.includes('铝')?'铝':'钢';found=true}
  if(/中点|中间|自由端加载/.test(text)){m.at=/中点|中间/.test(text)?.5:1;found=true}
  if(!found||Object.values(m).some(x=>typeof x==='number'&&(!Number.isFinite(x)||x<=0))){reply('请给出可识别的条件，例如“梁长 1.2 m”“载荷 40 N”或“移到中点”。当前实验保持原设置。');return}
  state.prior={...state.now};state.now=m;if(file)state.guide=text;
  reply((file?'已导入可识别条件，缺项沿用示例默认值。':'已更新。')+'自由端位移从 '+end(state.prior).toFixed(3)+' mm 变为 '+end(m).toFixed(3)+' mm。');
 }
 $('say').onsubmit=e=>{e.preventDefault();run($('request').value);$('request').value=''};
 document.querySelectorAll('[data-ask]').forEach(b=>b.onclick=()=>run(b.dataset.ask));
 $('guideFile').onchange=async e=>{const f=e.target.files[0];if(f){if(f.size>64000){alert('请选择小于 64 KB 的文本指导书');return}run(await f.text(),true)}};
 $('loadAt').onchange=e=>{state.prior={...state.now};state.now.at=Number(e.target.value)/100;paint()};
 $('keep').onclick=()=>{state.snapshot=JSON.parse(JSON.stringify({now:state.now,prior:state.prior}));state.saved='已保存 · '+(state.prior?end(state.prior).toFixed(3)+' → ':'')+end(state.now).toFixed(3)+' mm（'+state.now.material+'）';paint()};
 const canvas=$('plot');const observer=new ResizeObserver(()=>{if(canvas.isConnected)chart();else observer.disconnect()});observer.observe(canvas);paint();
}
