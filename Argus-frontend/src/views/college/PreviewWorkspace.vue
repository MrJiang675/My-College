<script setup lang="ts">
import { useRoute } from 'vue-router'
import CollegeView from './CollegeView.vue'
import ThemeToggle from '@/components/ThemeToggle.vue'
const route = useRoute()
</script>
<template>
  <main class="preview">
    <header>
      <b>Argus · 前端样例预览</b><ThemeToggle /><RouterLink to="/login">登录真实工作台</RouterLink>
    </header>
    <p>当前为独立本地预览，内置演示带标识，自由提问需要后端与登录态。</p>
    <nav>
      <RouterLink
        v-for="item in [
          { key: 'learn', name: '学习助手' },
          { key: 'practice', name: '专业实践' },
          { key: 'opps', name: '校园机会与办事' },
          { key: 'experience', name: '我的经历' },
          { key: 'career', name: '升学就业' },
        ]"
        :key="item.key"
        :to="'/preview?module=' + item.key"
        >{{ item.name }}</RouterLink
      >
    </nav>
    <CollegeView :module-override="String(route.query.module || 'learn')" preview-mode />
  </main>
</template>
<style scoped>
.preview {
  padding: 20px;
  background: var(--bg-page);
  color: var(--text-primary);
  min-height: 100vh;
}
header,
nav {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  align-items: center;
  margin-bottom: 16px;
}
nav a {
  padding: 8px;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  color: var(--text-primary);
}
</style>
