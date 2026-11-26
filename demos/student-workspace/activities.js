function extraPage(id, record, course) {
  if (id === 'experience') {
    return section('我的经历', record.experience
      ? '<small>项目贡献</small><h3>'+esc(course[5])+'</h3><p>'+esc(record.experience)+'</p>'+link('career','用于申请准备 →')
      : '<h3>留下你的第一条项目经历</h3><p>在项目里写下自己负责的部分，再存入这里。</p>'+link('project','打开我的项目 →'),
      '<h2>材料来源</h2><p>这里保留你主动收录的项目记录。</p>');
  }
  if (id === 'career') {
    return section('升学就业','<label>准备方向<select id="track"><option>实习申请</option><option>升学申请</option></select></label><label>我想对照的一条申请要求<textarea id="requirement" placeholder="例如：能用具体项目说明自己如何验证方案"></textarea></label><h3>我的项目证据</h3><p>'+esc(record.experience||'还没有收录经历。')+'</p>'+link('experience','查看我的经历')+'<label>哪些细节能证明我符合要求？<textarea id="evidence"></textarea></label><button class="btn dark" id="draft">保存申请笔记</button><p role="status" id="feedback"></p>', '<h2>准备材料时</h2><p>写清你独立完成的工作，并记下可核实的成果。不同申请方向的笔记分别保存。</p>');
  }
  const labs=['悬臂梁挠度','RC 充电','采访核实'];
  return section('专业实践','<label>练习场景<select id="lab">'+labs.map(x=>'<option>'+x+'</option>').join('')+'</select></label><div id="experiment"></div>', '<h2>实验手记</h2><p>记下哪一步改变了你的判断。</p><label>我的观察<textarea id="observation"></textarea></label><small>自动保存在当前浏览器</small>');
}

function mountExtras(id,r,a) {
  if(id==='project'){
    const button=document.createElement('button');
    button.className='btn';button.textContent='存为我的经历';
    $('memoSave').after(button);
    button.onclick=()=>{const text=$('memo').value.trim();if(!text){$('msg').textContent='先写下你负责的部分。';return}r.project=text;r.experience=text;save();location.hash='experience'};
  }
  if(id==='career'){
    r.applications ||= {};
    $('track').value=r.track||'实习申请';
    function restore(){const note=r.applications[$('track').value]||{};$('requirement').value=note.requirement||'';$('evidence').value=note.evidence||'';$('feedback').textContent=''}
    restore();$('track').onchange=()=>{r.track=$('track').value;save();restore()};
    $('draft').onclick=()=>{r.applications[$('track').value]={requirement:$('requirement').value,evidence:$('evidence').value};save();$('feedback').textContent='已保存此方向的申请笔记。'};
  }
  if(id==='practice'){
    $('lab').selectedIndex=r.lab??(s.major.includes('机械')?0:s.major.includes('电气')?1:2);
    function experiment(){
      const mode=$('lab').selectedIndex,area=$('experiment');
      r.notes ||= {};$('observation').value=r.notes[mode]||'';
      $('observation').oninput=()=>{r.notes[mode]=$('observation').value;save()};
      if(mode===2){
        area.innerHTML='<h3>哪句话可以直接发表？</h3><blockquote>受访学生说：“我这周等车的时间比上周长。”</blockquote><button class="choice">全校班车候车时间增加。</button><button class="choice">一名受访学生表示，本周自己等车更久。</button><p role="status"></p>';
        area.querySelectorAll('button').forEach((b,i)=>b.onclick=()=>area.lastElementChild.textContent=i?'依据充分。原文支持这个人的经历。':'还需要班次记录或更广泛的调查。');return;
      }
      area.innerHTML='<h3>'+(mode?'改变电阻，观察充电时间':'改变梁长，观察端部挠度')+'</h3><p>'+(mode?'固定电容 100 μF，充至终值约 63.2%。':'末端集中力、材料和截面保持不变，以 1 m 梁为基准。')+'</p><label>'+(mode?'电阻（kΩ）':'梁长（m）')+'<input id="parameter" type="range" min="1" max="5" value="1"></label><output class="diagram" id="reading"></output>';
      const update=()=>{const n=Number($('parameter').value);$('reading').textContent=mode?n+' kΩ → '+n*100+' ms':n+' m → '+n**3+' 倍挠度'};$('parameter').oninput=update;update();
    }
    $('lab').onchange=()=>{r.lab=$('lab').selectedIndex;save();experiment()};experiment();
  }
  if(id==='campus'){
    $('app').querySelector('article').insertAdjacentHTML('beforeend','<hr><h3>通知解读</h3><label>通知原文<textarea id="notice"></textarea></label><button class="btn dark" id="extract">整理办理清单</button><div id="noticeResult" aria-live="polite"></div><small>本地关键词提取示例，请对照原文核实。</small>');
    $('notice').value=r.notice??'对象：本院大二学生。请于周五下午五点前提交申请。材料包括学生证和申请表。';
    $('extract').onclick=()=>{r.notice=$('notice').value;save();const lines=r.notice.split(/[。；\n]/).map(x=>x.trim()).filter(Boolean);$('noticeResult').innerHTML=[['适用对象',/对象|学生|年级/],['办理时间',/截至|截止|之前|前提交|日期|时间/],['所需材料',/材料|携带|附件/]].map(([label,re])=>'<p><b>'+label+'</b>　'+esc(lines.filter(x=>re.test(x)).join('。')||'未找到明确原句，需核实')+'</p>').join('')};
  }
}
