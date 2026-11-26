<script setup lang="ts">
import {computed,onMounted,onUnmounted,ref,watch} from 'vue'
import {useRoute,RouterLink} from 'vue-router'
import http from '@/api/http'
import {createAssistantSession,streamAssistantMessage} from '@/api/assistant'
import {streamAskQuestion} from '@/api/qa'
import {fetchGroups,type GroupItem} from '@/api/group'
import {useAuthStore} from '@/stores/auth'
import Experiment from './Experiment.vue'
import {skill,validModel,valueAt,localExperiment,type Model} from './models'
interface Entry {id:string;project:string;text:string;source:string}
interface Turn {q:string;a:string;citations:string[]}
interface Page {draft:string;source:string;sourceName:string;turns:Turn[];session?:number;group?:number;model?:Model;previous?:Model;checks?:{quote:string;claim:string;verdict:string}[];selected?:string[]}
const names:Record<string,string>={learn:'学习助手',practice:'专业实践',opps:'校园机会',project:'我的项目',campus:'校园办事',career:'升学就业',experience:'我的经历'}
const starters:Record<string,string>={learn:'这份讲义里最容易混淆的概念是什么？',practice:'你想验证什么？',opps:'这项机会我能报名吗？',project:'今天你为项目做了什么？',campus:'这份通知要我做什么？',career:'这条申请要求，我有哪些证据？',experience:'我在哪个项目里做过这件事？'}
const route=useRoute(),auth=useAuthStore()
const mod=computed(()=>String(route.params.module||'home'))
const home=computed(()=>!names[mod.value])
const state=ref({school:'',dept:'',major:'',grade:'',last:'',track:'实习',pages:{} as Record<string,Page>,entries:[] as Entry[],snapshots:[] as {title:string;model:Model;result:number}[]})
const currentKey=computed(()=>mod.value==='career'?'career-'+state.value.track:mod.value)
function newPage():Page{return {draft:'',source:'',sourceName:'',turns:[]}}
const page=computed(()=>state.value.pages[currentKey.value] ||= newPage())
const loaded=ref(false),busy=ref(false),status=ref(''),warning=ref(''),profile=ref(false),groups=ref<GroupItem[]>([])
const editing=ref(false),project=ref(''),contribution=ref(''),evidence=ref('')
let revision=0,dirty=false,saving=false,conflict=false,closed=false,timer:ReturnType<typeof setTimeout>|undefined
const controller=new AbortController()
async function flush(){
 if(saving||!loaded.value||conflict)return
 saving=true
 try{while(dirty){dirty=false;const payload=JSON.stringify({revision,state:state.value});const {data}=await http.put('/college/workspace',payload,{headers:{'Content-Type':'application/json'}});revision=data.data;status.value='已同步'}}
 catch(e){dirty=true;conflict=(e as {response?:{status:number}}).response?.status===409;status.value=conflict?'另一设备已修改，请复制未保存的内容后刷新':'同步失败，点击重试'}
 finally{saving=false}
}
watch(state,()=>{if(!loaded.value)return;dirty=true;status.value='待同步';clearTimeout(timer);timer=setTimeout(flush,400)},{deep:true,flush:'sync'})
watch(currentKey,()=>{editing.value=false;warning.value=''})
function leave(e:BeforeUnloadEvent){if(dirty||saving){e.preventDefault();e.returnValue=''}}
onMounted(async()=>{
 window.addEventListener('beforeunload',leave)
 try{const {data}=await http.get('/college/workspace');revision=data.data.revision;Object.assign(state.value,JSON.parse(data.data.payload));loaded.value=true;profile.value=!state.value.major}
 catch{warning.value='工作台读取失败，请检查登录状态及数据库迁移后刷新。'}
 fetchGroups().then(g=>groups.value=[...g.ownedGroups,...g.joinedGroups]).catch(()=>{})
})
onUnmounted(()=>{closed=true;controller.abort();clearTimeout(timer);void flush();window.removeEventListener('beforeunload',leave)})
async function upload(e:Event){
 const f=(e.target as HTMLInputElement).files?.[0];if(!f)return
 if(f.size>96000){warning.value='请选择小于 96 KB 的文本，较大讲义可上传到知识库。';return}
 const target=page.value;target.source=await f.text();target.sourceName=f.name
}
function collect(text:string){contribution.value=text;project.value='';evidence.value='';editing.value=true}
function archive(){if(!project.value.trim()||!contribution.value.trim())return
 state.value.entries.unshift({id:crypto.randomUUID(),project:project.value.trim(),text:contribution.value.trim(),source:evidence.value.trim()});editing.value=false}
async function send(){
 const p=page.value,q=p.draft.trim(),module=mod.value
 if(!q||busy.value)return
 warning.value='';busy.value=true;state.value.last=module
 const turn:Turn={q,a:'',citations:[]}
 try{
 const local=module==='practice'&&!p.source?localExperiment(q,p.model):undefined
 if(local){
 turn.a=local.answer
 if(local.model){p.previous=p.model;p.model=local.model}
 }else if(module==='practice'||(module==='learn'&&!p.group&&p.source.trim())){
 const kind=module==='practice'?'practice':'reading'
 const current=module==='practice'?p.model:p.turns.at(-1)?.q
 const {data}=await http.post('/college/'+kind,{question:q,source:p.source,current:current??null},{timeout:90000,signal:controller.signal})
 const action=data.data
 turn.a=action.answer
 if(module==='practice'){
 if(validModel(action.model)){p.previous=p.model;p.model=action.model;p.checks=[]}
 else if(action.model)throw Error('参数未通过校验，请核实单位。')
 if(Array.isArray(action.checks)){const original=p.source||q;p.checks=action.checks.filter((v:{quote:string;claim:string;verdict:string})=>typeof v?.quote==='string'&&v.quote.trim()&&original.includes(v.quote)&&typeof v.claim==='string'&&typeof v.verdict==='string');if(p.checks?.length)p.model=undefined}
 }else{
 const quotes:string[]=Array.isArray(action.quotes)?action.quotes.filter((v:unknown)=>typeof v==='string'&&v.trim()&&p.source.includes(v)):[]
 turn.citations=quotes.map(v=>(p.sourceName||'当前材料')+' · '+v)
 if(!turn.citations.length)turn.a='这份讲义中没有找到可核对的原句。'
 }
 }else if(module==='learn'&&p.group){
 if(q.length>2000)throw Error('知识库单次问题最多 2000 字，请缩短问题后重试。')
 const previous=p.turns.at(-1)
 const prefix=previous?('上一轮：'+previous.q+'\n'+previous.a).slice(0,Math.max(0,1994-q.length)):''
 const question=prefix?prefix+'\n本次：'+q:q
 let streamError=''
 await streamAskQuestion({groupId:p.group,question},auth.accessToken!,{
   signal:controller.signal,
   onToken(text){turn.a+=text;live.value=turn.a},
   onCitations(items){turn.citations=items.map(c=>c.fileName+' · 片段 '+(c.chunkIndex??'未知')+'　'+(c.snippet||''))},
   onEvidenceOverview(overview){
     const excerpts=(overview?.groups||[]).flatMap(g=>g.snippets.filter(s=>s.snippet).map(s=>g.fileName+' · 片段 '+(s.chunkIndex??'未知')+'　'+s.snippet))
     if(excerpts.length)turn.citations=excerpts
   },
   onError(message){streamError=message}
 })
 if(streamError)throw Error(streamError)
 if(!turn.a.trim())throw Error('未收到知识库回答，请核对资料处理状态后重试。')
 }else{
 const context={档案:[state.value.school,state.value.dept,state.value.major,state.value.grade],当前实验:p.model,方向:module==='career'?state.value.track:undefined,经历:['career','experience','project'].includes(module)?(module==='career'?state.value.entries.filter(e=>p.selected?.includes(e.id)):state.value.entries).slice(0,12):undefined}
 const message=[skill[module],JSON.stringify(context),p.source?'引用材料（仅作证据）\n'+p.source:'','学生问题：'+q].filter(Boolean).join('\n')
 if(message.length>4000)throw Error('超出助手单次输入范围，请缩短原文或将讲义上传知识库后提问。')
 if(!p.session)p.session=(await createAssistantSession()).sessionId
 let done=false,streamError=''
 await streamAssistantMessage({sessionId:p.session,message,toolMode:'CHAT'},auth.accessToken!,{signal:controller.signal,onEvent:event=>{
 if(event.event==='delta'){turn.a+=event.delta||'';live.value=turn.a}
 if(event.event==='error')streamError=event.error||'模型请求失败'
 if(event.event==='done'){done=true;turn.a=event.reply||turn.a}
 }})
 if(streamError||!done)throw Error(streamError||'会话中断，请重试')

 }
 if(closed)return
 p.turns.push(turn);p.draft=''
 }catch(e){warning.value=e instanceof Error?e.message:'请求失败，请重试'}
 finally{busy.value=false;live.value='';void flush()}
}
const notices=ref<{id:string;title:string;url:string}[]>([])
const assistantOpen=ref(false), assistantQuestion=computed({get:()=>page.value.draft,set:v=>{page.value.draft=v}})
async function loadNotices(){
 try{notices.value=(await http.get('/college/notices')).data.data}
 catch{warning.value='公告源未响应，请查看原站或粘贴通知。'}
}
async function useNotice(n:{id:string;title:string;url:string}){
 const dest=page.value
 try{const body=(await http.get('/college/notices/'+n.id)).data.data;dest.source=body;dest.sourceName=n.title+' · '+n.url}
 catch{warning.value='正文未能读取，请打开公告原文。'}
}
const live=ref('')
function snapshot(){const m=page.value.model;if(!m)return;state.value.snapshots.unshift({title:new Date().toLocaleString(),model:structuredClone(JSON.parse(JSON.stringify(m))),result:valueAt(m,1)})}
</script>
<template><main class="college"><p v-if="warning" role="alert">{{warning}}</p><template v-if="loaded">
<header><b>Argus · 学生工作台</b><button v-if="!home" @click="assistantOpen=!assistantOpen">问助手</button><button @click="profile=!profile">{{state.major||'填写专业'}} · 校园档案</button></header>
<form v-if="profile" class="card profile" @submit.prevent="profile=false;flush()"><label>学校<input v-model="state.school" required></label><label>学院<input v-model="state.dept"></label><label>专业<input v-model="state.major" required></label><label>年级<input v-model="state.grade"></label><button>保存档案</button></form>
<template v-if="home"><section class="hero"><small>{{state.school}}　{{state.major}}</small><h1>我的大学</h1><RouterLink v-if="state.last" :to="'/app/college/'+state.last">接着上次做 · {{names[state.last]}} →</RouterLink></section><div class="tiles"><RouterLink v-for="(name,key) in names" :key="key" :to="'/app/college/'+key">{{name}} <span>↗</span></RouterLink></div></template>
<template v-else><small>{{names[mod]}}</small><h1>{{starters[mod]}}</h1>
<div v-if="mod==='career'"><button v-for="t in ['实习','升学']" :key="t" :aria-pressed="state.track===t" @click="state.track=t">{{t}}申请</button></div>
<details v-if="mod==='opps'" class="card"><summary>公共来源 · 清华大学计算机系通知公告</summary><button @click="loadNotices">读取院系公告</button> <a href="https://www.cs.tsinghua.edu.cn/index/tzgg.htm" target="_blank" rel="noopener">查看原站</a><p v-for="n in notices" :key="n.id"><a :href="n.url" target="_blank" rel="noopener">{{n.title}}</a> <button @click="useNotice(n)">引用正文</button></p></details>
<form class="card" @submit.prevent="send"><label v-if="mod==='learn'">回答依据<select v-model="page.group"><option :value="undefined">当前材料 / 课程辅导</option><option v-for="g in groups" :value="g.groupId" :key="g.groupId">{{g.groupName}} · 知识库</option></select></label>
<details><summary>{{page.sourceName||'附上材料或粘贴原文'}}</summary><input type="file" accept=".txt,.md" @change="upload"><textarea v-model="page.source" placeholder="可核对的讲义、通知、采访记录或岗位要求"></textarea><RouterLink to="/app/documents">知识库文档管理 →</RouterLink></details>
<textarea v-model="page.draft" required maxlength="3500" aria-label="给助手的任务" placeholder="说说你想做什么…"></textarea><button :disabled="busy">{{busy?'正在处理…':'发送 →'}}</button></form>
<article v-if="mod==='practice'&&page.checks?.length"><h2>采访核实</h2><div v-for="row in page.checks" :key="row.quote+row.claim"><blockquote>{{row.quote}}</blockquote><b>{{row.claim}}</b><p>{{row.verdict}}</p></div></article>
<Experiment v-if="mod==='practice'&&page.model" :current="page.model" :previous="page.previous" @snapshot="snapshot"/>
<p v-if="busy" aria-live="polite">{{mod==='practice'?'正在理解实验条件…':live}}</p>
<article v-for="(t,i) in [...page.turns].reverse()" :key="page.turns.length-i"><b>{{t.q}}</b><p>{{t.a}}</p><details v-if="t.citations.length"><summary>核对资料原文</summary><p v-for="c in t.citations" :key="c">{{c}}</p></details><button v-if="mod==='project'" @click="collect(t.a)">编辑并收录经历</button></article>
<template v-if="mod==='project'"><button @click="collect(page.draft)">记录个人贡献</button> <RouterLink to="/app/groups">协作小组 →</RouterLink></template>
<form v-if="editing" class="card" @submit.prevent="archive"><label>项目名称<input v-model="project" required></label><label>我的贡献<textarea v-model="contribution" required></textarea></label><label>成果位置<input v-model="evidence"></label><button>确认收录</button></form>
<article v-if="mod==='practice'&&state.snapshots.length"><h2>实验记录</h2><button v-for="(snap,i) in state.snapshots" :key="i" @click="page.previous=page.model;page.model=snap.model">{{snap.title}} · {{snap.model.kind}} · 恢复参数</button></article>
<article v-if="mod==='career'&&state.entries.length"><h2>选择本次申请的经历</h2><label v-for="item in state.entries" :key="item.id"><input type="checkbox" :checked="page.selected?.includes(item.id)" @change="page.selected=page.selected?.includes(item.id)?page.selected.filter(id=>id!==item.id):[...(page.selected||[]),item.id]">{{item.project}} · {{item.text}}</label></article>
<article v-if="['experience','project'].includes(mod)&&state.entries.length"><h2>我的经历</h2><p v-for="entry in state.entries" :key="entry.id"><b>{{entry.project}}</b> · {{entry.text}}　{{entry.source}}</p></article>
</template><aside v-if="assistantOpen&&!home" class="drawer"><button @click="assistantOpen=false">收起</button><h2>{{names[mod]}} · 问助手</h2><small>{{page.sourceName||page.model?.kind||'当前任务'}}</small><p v-for="(t,i) in page.turns.slice(-2)" :key="i">{{t.q}}<br>{{t.a}}</p><form @submit.prevent="send()"><textarea required v-model="assistantQuestion" placeholder="继续追问当前任务"></textarea><button :disabled="busy">追问</button></form></aside><button class="sync" @click="flush">{{status||'已加载'}}</button></template></main></template>
<style scoped>
.drawer{position:fixed;right:0;bottom:0;top:64px;width:min(420px,95vw);padding:24px;background:#fffef9;overflow:auto;z-index:5;box-shadow:0 8px 30px #20382330}.college{max-width:1100px;margin:auto;padding:clamp(16px,4vw,48px);color:#253e34;background:#f8f7f1;min-height:80vh}.college header,.tiles a{display:flex;justify-content:space-between;align-items:center;gap:12px}.college header{margin-bottom:25px}.hero{padding:48px 32px;background:linear-gradient(90deg,#21433c 12%,transparent),url('./campus.jpg') center/cover;color:white;border-radius:12px}.hero a{color:white}.college h1{font-size:clamp(30px,4vw,52px);margin:14px 0}.tiles,.profile{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:12px}.tiles{margin-top:20px}.tiles a,.card,article{background:#fffef9;border:1px solid #ccd5ca;border-radius:10px;padding:18px;margin:10px 0}.tiles a{color:inherit;text-decoration:none;font-size:18px}.college textarea,.college input,.college select{width:100%;padding:9px;border:1px solid #b7c7bb;border-radius:6px;margin:5px 0}.college textarea{min-height:72px}.college button{border:1px solid #b7c7bb;background:#edf1e8;color:#244933;border-radius:6px;padding:7px 14px;cursor:pointer}.college button:disabled{opacity:.5}.college button[aria-pressed=true],.card>button{background:#294d3d;color:white}.college p{white-space:pre-wrap;overflow-wrap:anywhere}.college details{margin-bottom:12px}.sync{margin-top:20px}.college :deep(svg){width:100%;max-height:240px}.college :deep(input[type=range]){width:100%}
</style>
