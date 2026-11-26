<script setup lang="ts">
import {ref} from 'vue'
import LoginModal from '@/components/LoginModal.vue'
const login=ref(false)
</script>
<template><main><header>Argus<span>●</span></header><section><div><small>大学生学习与成长工作台</small><h1>从你的问题，<br>开始今天的实践。</h1><p>调一调电阻，观察充电曲线；核对一份学院通知，弄清报名条件。把今天的项目贡献留下，下一次申请就能用上。</p><button @click="login=true">进入我的大学 →</button></div><img src="./campus.jpg" alt="校园图书馆"></section><LoginModal v-model:visible="login"/></main></template>
<style scoped>main{min-height:100vh;background:#f6f5ee;color:#203c31;padding:3vw 6vw}header{font:bold italic 44px Georgia}header span{color:#b85e39;font-size:16px}section{margin:6vh auto;max-width:1200px;display:grid;grid-template-columns:1fr 1fr;background:#204239;color:#fff9ef;border-radius:18px;overflow:hidden}section div{padding:clamp(22px,5vw,80px)}h1{font-size:clamp(30px,4.5vw,70px);line-height:1.2;margin:20px 0}p{line-height:1.9}img{width:100%;height:100%;object-fit:cover}button{background:#f3cc9e;border:0;border-radius:6px;padding:12px 20px;cursor:pointer}@media(max-width:700px){section{grid-template-columns:1fr}img{max-height:28vh}}</style>
