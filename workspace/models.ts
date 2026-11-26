export type Model = {kind:'rc'|'beam', values:number[]}
export const fields = {
  rc:['电阻 / kΩ','电容 / μF','电源 / V'],
  beam:['梁长 / m','宽 / mm','高 / mm','集中力 / N','弹性模量 / GPa','加载位置 / 梁长']
}
export const skill:Record<string,string> = {
  learn:'辅导概念或出一道可作答的题；批改须利用前一轮题目。严格区分资料原文与解释。',
  practice:'识别学生要验证的现象。支持直流阶跃 RC 充电、矩形悬臂梁单集中力及新闻采访原句核实。其他实验只解释步骤和需测量量。只有模型明确、参数充分且无冲突时输出计算指令。未给关键参数时先追问，也可以征得同意后使用示例参数。更新只改变学生指定的量。RC values=[电阻kΩ,电容μF,电源V]；beam values=[梁长m,宽mm,高mm,力N,杨氏模量GPa,载荷位置占梁长比例]。单位换算。用 JSON 对象响应：{"answer":"解释或追问","model":null} 或 {"answer":"说明依据和假设","model":{"kind":"rc或beam","values":[...]}}。采访核实时输出 {"answer":"说明","checks":[{"quote":"材料中的原句","claim":"报道表述","verdict":"证据充分或待核实"}]}。不要生成程序。',
  opps:'根据提供的机会原文分别核对资格、截止日与综测认定。无本院本学年目录时标记综测待核实；每条结论引用对应原句。',
  campus:'从通知原文提取对象、材料、时间及办理地点，逐项附原句。缺失项写需核实。',
  project:'整理学生自己的项目贡献，区分个人和团队工作，保留成果位置，不补造指标。',
  career:'根据已保存经历和用户提供的申请要求逐项对照，写出可验证证据与缺口，不虚构成绩。',
  experience:'依据已有经历答复，明确来源项目及成果位置。'
}
export function validModel(value:unknown):value is Model{
  if(!value || typeof value!=='object')return false
  const m=value as Model
  return (m.kind==='rc'||m.kind==='beam') && Array.isArray(m.values) &&
    m.values.length===fields[m.kind].length && m.values.every(v=>Number.isFinite(v)&&v>0&&v<=1e9) &&
    (m.kind!=='beam'||m.values[5]!<=1)
}
export function valueAt(model:Model,ratio:number){
  if(model.kind==='rc')return model.values[2]! * -Math.expm1(-5*ratio)
  const [L,b,h,F,E,k]=model.values as [number,number,number,number,number,number]
  const a=L*k,x=L*ratio,I=b/1000*(h/1000)**3/12
  return 1000*F*(x<=a?x*x*(3*a-x):a*a*(3*x-a))/(6*E*1e9*I)
}
export function extent(m:Model){return m.kind==='rc'?5*m.values[0]!*m.values[1]!/1000:m.values[0]!}

// Resolve unambiguous parameter edits without spending a model call.
export function localExperiment(question:string, previous?:Model):{answer:string;model?:Model}|undefined {
  const q=question.trim()
  if(previous?.kind==='rc' && /^(?:把|将)?电阻(?:改为原来的两倍|翻倍|加倍)[。！!？?]?$/.test(q)){
    const model:Model={kind:'rc',values:[...previous.values]}
    model.values[0]=model.values[0]!*2
    if(!validModel(model))return {answer:'电阻超出可计算范围，请核对单位。'}
    return {model,answer:'电容与电源保持不变。电阻翻倍，时间常数也翻倍。'}
  }
  if(/^(我想做(一个)?)?(电学|力学)实验[。？?]?$/.test(q))return {answer:'想改变哪个量，观察什么现象？也可以上传指导书，我会先核对条件。'}
  if(/^(?:试用|使用|打开)(RC充电|悬臂梁)示例$/.test(q)){
    const model:Model=q.includes('RC')?{kind:'rc',values:[10,100,5]}:{kind:'beam',values:[1,20,40,50,200,1]}
    return {model,answer:'已使用界面所列的示例条件，可逐项核对或继续调整。'}
  }
}

