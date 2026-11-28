<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CollegeView from '../college/CollegeView.vue'
import GroupsPage from './GroupsPage.vue'
import DocumentListView from '../documents/DocumentListView.vue'
import http from '@/api/http'
const entryCount = ref(0)
function updateCount(event: Event) {
  entryCount.value = (event as CustomEvent).detail.entries || 0
}
onMounted(async () => {
  window.addEventListener('argus:workspace-progress', updateCount)
  try {
    entryCount.value =
      JSON.parse((await http.get('/college/workspace')).data.data.payload).entries?.length || 0
  } catch {}
})
onUnmounted(() => window.removeEventListener('argus:workspace-progress', updateCount))
const route = useRoute(),
  router = useRouter()
const tab = computed(() =>
  ['projects', 'documents', 'members', 'experience'].includes(String(route.query.tab))
    ? String(route.query.tab)
    : 'projects',
)
function select(value: string) {
  router.replace({ path: '/app/groups', query: { ...route.query, tab: value } })
}
</script>
<template>
  <section class="group-workspace">
    <nav aria-label="小组工作区">
      <button
        v-for="item in [
          { id: 'projects', label: '我的项目' },
          { id: 'documents', label: '小组资料与知识库' },
          { id: 'members', label: '成员与协作管理' },
        ]"
        :key="item.id"
        :aria-pressed="tab === item.id"
        @click="select(item.id)"
      >
        {{ item.label }}
      </button>
    </nav>
    <button
      class="experience-shortcut"
      @click="select(tab === 'experience' ? 'projects' : 'experience')"
    >
      {{ tab === 'experience' ? '返回我的项目' : '我的经历 (' + entryCount + ')' }}
    </button>
    <CollegeView v-if="tab === 'experience'" module-override="experience" />
    <CollegeView v-else-if="tab === 'projects'" module-override="project" /><DocumentListView
      v-else-if="tab === 'documents'"
    /><GroupsPage v-else />
  </section>
</template>
<style scoped>
nav {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
button {
  padding: 10px 14px;
  border: 1px solid var(--border-default);
  background: var(--surface-white);
  color: var(--text-primary);
  border-radius: 8px;
  cursor: pointer;
}
button[aria-pressed='true'] {
  background: var(--brand-primary);
  color: white;
}
</style>
