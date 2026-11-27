<script setup lang="ts">
type Notice = { id: string; title: string; url: string }
defineProps<{ mod: string; notices: Notice[] }>()
const emit = defineEmits<{ load: []; use: [notice: Notice] }>()
</script>
<template>
  <details v-if="mod === 'opps'" class="source-panel panel">
    <summary>
      <span>公共来源 · 清华大学计算机系通知公告</span>
      <small>其他院系通知可直接粘贴</small>
    </summary>
    <div class="source-panel__actions">
      <button type="button" class="btn-secondary" @click="emit('load')">读取院系公告</button>
      <a href="https://www.cs.tsinghua.edu.cn/index/tzgg.htm" target="_blank" rel="noopener"
        >查看原站 ↗</a
      >
    </div>
    <ul v-if="notices.length" class="notice-list">
      <li v-for="notice in notices" :key="notice.id">
        <a :href="notice.url" target="_blank" rel="noopener">{{ notice.title }}</a>
        <button type="button" @click="emit('use', notice)">引用正文</button>
      </li>
    </ul>
  </details>
</template>
<style scoped src="../college.css"></style>
