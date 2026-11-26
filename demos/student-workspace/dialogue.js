const prompts={
 read:['学习助手','哪部分课程内容想弄懂？','讲解本节原理','我想做一道自测'],
 quiz:['学习助手','想练哪部分内容？','讲解本节原理','我想做一道自测'],
 practice:['专业实践','你想验证什么？'],
 opps:['校园机会','这项机会，你想核实什么？','我能报名吗','综测加分有什么依据'],
 project:['我的项目','今天为项目做了什么？','记录贡献：','查看已有进展'],
 campus:['校园办事','你要办什么事？','解读当前通知','查看预约材料'],
 career:['升学就业','你在准备哪项申请？','准备实习申请','准备升学申请'],
 experience:['我的经历','想整理哪段经历？','查看项目贡献','用于申请准备']
};
function mountDialogue(id,r,a){
 const spec=prompts[id];if(!spec)return;
 const app=$('app'),result=document.createElement('div');
 while(app.firstChild)result.append(app.firstChild);
 const panel=document.createElement('section');panel.className='studio conversation';
 panel.innerHTML='<small>'+spec[0]+'</small><h1>'+spec[1]+'</h1><form class="paper"><label>给助手的任务<textarea required placeholder="描述你想做的事…"></textarea></label><div class="row"><button class="btn dark">发送 →</button>'+spec.slice(2).map((v,i)=>'<button class="subtle" type="button" data-pick="'+i+'">'+v+'</button>').join('')+'</div>'+(id==='practice'?'<label>或导入指导书（TXT / MD）<input type="file" accept=".txt,.md"></label>':'')+'</form><div class="dialogLog" aria-live="polite"></div><small>本地交互演示</small>';
 app.append(panel,result);
 const log=panel.querySelector('.dialogLog'),box=panel.querySelector('textarea');
 const key=id==='quiz'?'read':id;r.dialogues ||= {};const history=r.dialogues[key] ||= [];
 function display(){log.textContent='';for(const [q,v] of history.slice(-2)){const el=document.createElement('p');el.className='callout';const tag=document.createElement('strong');tag.textContent=q;el.append(tag,document.createElement('br'),document.createTextNode(v));log.append(el)}}
 function say(q,v){history.push([q,v]);if(history.length>8)history.shift();display();save()}
 function run(q){
 if(!q.trim())return;box.value='';
 if(id==='practice'){
 const rc=/\bRC\b|电容.*充电|充电.*电容/i.test(q),beam=/悬臂梁/.test(q);
 if(rc===beam){say(q,'请补充实验对象、要改变的量和要观察的量。本地版支持 RC 充电与悬臂梁静载。');return}
 if(rc){
 if(/放电|交流|振荡/.test(q)){say(q,'当前电学模型只支持理想直流阶跃充电。请确认实验条件。');return}
 const old=r.rc||{R:10,C:100,V:5};const val=(re,f)=>{const m=q.match(re);return m?Number(m[1]):f};
 const R=val(/([\d.]+)\s*k(?:Ω|ohm)/i,old.R),C=val(/([\d.]+)\s*[μµu]F/i,old.C),V=val(/([\d.]+)\s*V/i,old.V);
 if(![R,C,V].every(x=>Number.isFinite(x)&&x>0)){say(q,'请使用正数参数。');return}
 r.rc={R,C,V};const tau=R*C/1000;
 result.innerHTML='<article class="paper"><small>理想直流阶跃 · 初始电压 0 V</small><h2>RC 充电曲线</h2><canvas class="plot" width="760" height="230"></canvas><p>时间常数 τ = '+Number(tau.toPrecision(4))+' s</p><label>观察时刻（0 至 5τ）<input type="range" min="0" max="500" value="100"></label><output></output></article>';
 const slider=result.querySelector('input'),out=result.querySelector('output'),ctx=result.querySelector('canvas').getContext('2d');
 function plot(){const t=slider.value/100;ctx.clearRect(0,0,760,230);ctx.strokeStyle='#adb9aa';ctx.strokeRect(25,14,705,190);ctx.beginPath();for(let i=0;i<=500;i++){const x=25+i*1.41,y=204-188*(1-Math.exp(-i/100));i?ctx.lineTo(x,y):ctx.moveTo(x,y)}ctx.strokeStyle='#315a43';ctx.lineWidth=3;ctx.stroke();ctx.beginPath();ctx.arc(25+t*141,204-188*(1-Math.exp(-t)),5,0,2*Math.PI);ctx.fill();out.textContent=(t*tau).toFixed(3)+' s　·　电容电压 '+(V*(1-Math.exp(-t))).toFixed(3)+' V'}slider.oninput=plot;plot();
 say(q,'当前模型参数：电阻 '+R+' kΩ，电容 '+C+' μF，电源 '+V+' V。未指定的量沿用上次值或演示值。拖动时间查看电压。');r.activeLab='rc';save();return}
 result.innerHTML=studioPage();mountStudio(r);
 if(/载荷|梁长|材料|截面/.test(q)){document.getElementById('request').value=q;document.getElementById('say').requestSubmit()}
 say(q,'已打开悬臂梁实验台。可在台内调整材料、尺寸及载荷。');r.activeLab='beam';save();return}
 if(id==='read'||id==='quiz'){if(/自测|题/.test(q)){say(q,'已打开本节自测。作答后可核对讲义。');location.hash='quiz'}else if(/讲解|原理|理解/.test(q)){say(q,(options[s.major]||options['通用课程'])[3]);location.hash='read'}else say(q,'当前演示可讲解本节原理或进入本节自测。');return}
 if(id==='opps'){say(q,/加分|综测/.test(q)?'示例细则要求赛事列入本学年目录。当前缺少目录，无法确认加分。':/报名|资格/.test(q)?result.querySelector('article p').textContent:'可以核对报名范围或综测依据。');return}
 if(id==='campus'){if(/通知/.test(q)){if(q.includes('：'))$('notice').value=q.slice(q.indexOf('：')+1);$('extract').click();say(q,$('noticeResult').innerText)}else say(q,/预约|材料/.test(q)?result.querySelector('article p').textContent:'可输入“解读通知：”并粘贴原文。');return}
 if(id==='project'){if(/^记录贡献[：:]/.test(q)&&q.slice(5).trim()){r.project=q.slice(5).trim();$('memo').value=r.project;say(q,'已保存本周贡献。可在项目卡片内存为个人经历。')}else say(q,r.project||'还没有进展记录。输入“记录贡献：”并写下本周完成的工作。');return}
 if(id==='career'){if(/实习|升学/.test(q)){const track=$('track');track.value=q.includes('实习')?'实习申请':'升学申请';track.onchange();say(q,'已打开'+track.value+'。可用证据：'+(r.experience||'暂未收录项目经历')+'。在下方对照具体要求整理。')}else say(q,'请选择实习或升学申请方向。');return}
 if(/申请/.test(q)){say(q,'已进入申请准备。');location.hash='career'}else say(q,r.experience||'还未收录经历，可先从项目里保存贡献。')
 }
 panel.querySelector('form').onsubmit=e=>{e.preventDefault();let q=box.value.trim();if(id==='practice'&&r.activeLab==='rc'&&result.querySelector('canvas')&&/^[^电梁]*[\d.]+\s*(kΩ|[μµu]F|V)/i.test(q))q='RC '+q;run(q)};
 panel.querySelectorAll('[data-pick]').forEach(b=>b.onclick=()=>{const q=spec[2+Number(b.dataset.pick)];if(q.endsWith('：')){box.value=q;box.focus()}else run(q)});
 if(id==='practice')panel.querySelector('[type=file]').onchange=async e=>{const file=e.target.files[0];if(file&&file.size<=65536)run(await file.text());else if(file)say(file.name,'请选择 64 KB 以内的文本指导书。')};
 display();
}
