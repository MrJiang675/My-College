<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CollegeView from '../college/CollegeView.vue'
import GroupsPage from './GroupsPage.vue'
import DocumentListView from '../documents/DocumentListView.vue'
const route = useRoute(),
  router = useRouter()
const tab = computed(() =>
  ['projects', 'documents', 'members'].includes(String(route.query.tab))
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
    <CollegeView v-if="tab === 'projects'" module-override="project" /><DocumentListView
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
