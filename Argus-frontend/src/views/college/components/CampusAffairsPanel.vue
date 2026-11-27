<script setup lang="ts">
defineProps<{ mod: string }>()
</script>
<template>
  <section v-if="['opps', 'campus'].includes(mod)" class="context-card panel">
    <span class="section-kicker">核对提醒</span>
    <h2>{{ mod === 'opps' ? '报名与综测' : '办事清单' }}</h2>
    <dl>
      <div>
        <dt>对象</dt>
        <dd>以通知原句为准</dd>
      </div>
      <div>
        <dt>材料</dt>
        <dd>核对是否缺少附件</dd>
      </div>
      <div>
        <dt>时间</dt>
        <dd>确认截止日期</dd>
      </div>
      <div>
        <dt>地点</dt>
        <dd>确认办理渠道</dd>
      </div>
    </dl>
    <el-tooltip content="通知原文未写明该项，请以学校或学院当期最新通知为准。" placement="left">
      <span class="status-badge status-badge--warning">需核实</span>
    </el-tooltip>
    <p v-if="mod === 'opps'">综测加分还需对照学院认定目录，缺少文件时请找老师核实。</p>
  </section>
</template>
<style scoped src="../college.css"></style>
