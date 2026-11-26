<script setup lang="ts">
import {computed,ref} from 'vue'
import {extent,fields,valueAt,type Model} from './models'
const props=defineProps<{current:Model, previous?:Model}>()
const emit=defineEmits<{snapshot:[]}>()
const point=ref(20)
const old=computed(()=>props.previous?.kind===props.current.kind?props.previous:undefined)
const xmax=computed(()=>Math.max(extent(props.current),old.value?extent(old.value):0))
const ymax=computed(()=>Math.max(valueAt(props.current,1),old.value?valueAt(old.value,1):0))
function coords(m:Model,r:number){return [12+466*extent(m)*r/xmax.value,154-140*valueAt(m,r)/ymax.value]}
function line(m:Model){return Array.from({length:101},(_,i)=>coords(m,i/100).join(',')).join(' ')}
</script>
<template><article><h2>{{current.kind==='rc'?'RC 充电曲线':'悬臂梁向下挠度'}}</h2>
<small>{{fields[current.kind].map((f,i)=>f+' = '+current.values[i]).join('　')}}</small>
<svg viewBox="0 0 500 168" role="img" aria-label="实线为当前模型，虚线为前一次模型"><path d="M12 10 V154 H486" fill="none" stroke="#9ca99d"/><polyline v-if="old" :points="line(old)" fill="none" stroke="#bc6940" stroke-dasharray="4 4"/><polyline :points="line(current)" fill="none" stroke="#245940" stroke-width="3"/><circle :cx="coords(current,point/100)[0]" :cy="coords(current,point/100)[1]" r="4" fill="#bc6940"/></svg>
<label>{{current.kind==='rc'?'观察时刻':'测量位置'}} <input v-model.number="point" type="range" min="0" max="100"></label>
<p>{{(extent(current)*point/100).toPrecision(4)}} {{current.kind==='rc'?'s':'m'}}　·　{{valueAt(current,point/100).toPrecision(4)}} {{current.kind==='rc'?'V':'mm'}}</p>
<p v-if="current.kind==='rc'">τ = {{extent(current)/5}} s · 初始电压 0 V</p><small v-if="old">虚线为上次实验</small> <button @click="emit('snapshot')">保存实验条件与结果</button></article></template>
