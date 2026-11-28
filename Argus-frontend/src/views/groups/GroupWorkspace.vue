<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CollegeView from '../college/CollegeView.vue'
import GroupsPage from './GroupsPage.vue'
import DocumentListView from '../documents/DocumentListView.vue'
import http from '@/api/http'
import CollaborationOverview from './CollaborationOverview.vue'
import { useAppStore } from '@/stores/app'
import { fetchGroupMembers, type GroupItem, type GroupMemberItem } from '@/api/group'
const app = useAppStore(),
  manager = ref<InstanceType<typeof GroupsPage>>(),
  overview = ref<InstanceType<typeof CollaborationOverview>>()
const demo = ref(false),
  members = ref<GroupMemberItem[]>([]),
  memberError = ref('')
function openDemo() {
  demo.value = true
  router.replace({ path: '/app/groups', query: { tab: 'projects' } })
}
async function action(kind: 'create' | 'join') {
  demo.value = false
  select('members')
  await nextTick()
  if (kind === 'create') manager.value?.openCreate()
  else manager.value?.openJoin()
}
async function choose(group: GroupItem) {
  demo.value = false
  app.setCurrentGroupId(group.groupId)
  router.replace({
    path: '/app/groups',
    query: { tab: 'projects', groupId: String(group.groupId) },
  })
}
async function manage() {
  if (!app.currentGroupId) return
  select('members')
  await nextTick()
  manager.value?.openManage(app.currentGroupId, app.canManageCurrentGroup ? 'owner' : 'member')
}
watch(
  () => app.currentGroupId,
  async (id) => {
    if (!id) return
    try {
      members.value = await fetchGroupMembers(id)
      memberError.value = ''
    } catch {
      memberError.value = '成员暂未读取，请刷新或打开管理面板。'
    }
  },
  { immediate: true },
)
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
watch(
  [
    () => String(route.query.groupId || ''),
    () => app.visibleGroups.map((g) => g.groupId).join(','),
  ],
  () => {
    const id = Number(route.query.groupId)
    if (id && app.visibleGroups.some((g) => g.groupId === id)) app.setCurrentGroupId(id)
  },
  { immediate: true },
)
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
    <CollaborationOverview
      ref="overview"
      @create="action('create')"
      @join="action('join')"
      @select="choose"
      @demo="openDemo"
    />
    <p v-if="app.currentGroup && !demo">
      当前小组：{{ app.currentGroup.groupName }}
      <button @click="manage">邀请成员 / 管理申请</button>
    </p>
    <p v-if="demo">
      演示协作空间 · 示例角色不是实际用户 <button @click="demo = false">返回真实协作</button>
    </p>
    <nav aria-label="小组工作区">
      <button
        v-for="item in [
          { id: 'projects', label: '项目进展与分工' },
          { id: 'documents', label: '小组共享资料库' },
          { id: 'members', label: '成员分工与申请管理' },
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
    <template v-if="demo">
      <section class="demo-workspace" v-if="tab === 'documents'">
        <h3>共享资料示例 · 只读</h3>
        <p>调研方案.md · READY（演示）</p>
        <p>目标：调查两个院系的校园出行习惯；分工：问卷、清洗、展示各自留痕。</p>
        <p>数据字典.md · READY（演示）</p>
        <p>字段：匿名样本编号、通勤方式、用时；不收集姓名学号。</p>
      </section>
      <section class="demo-workspace" v-else-if="tab === 'members'">
        <h3>示例分工</h3>
        <p>组长：统筹进度与报名；本人：清洗数据并记录规则；队友：问卷与展示。</p>
        <p>此处不创建真实成员、邀请或申请。</p>
      </section>
      <CollegeView
        v-else
        project-example
        :module-override="tab === 'experience' ? 'experience' : 'project'"
      />
    </template>
    <template v-else>
      <CollegeView v-if="tab === 'experience'" module-override="experience" />
      <CollegeView v-else-if="tab === 'projects'" module-override="project" />
      <template v-else-if="tab === 'documents'"
        ><DocumentListView /><RouterLink
          :to="'/app/intelligent?mode=knowledge&groupId=' + app.currentGroupId"
          >基于本组资料智能问答 →</RouterLink
        ></template
      >
      <section v-else>
        <h3>当前小组成员与分工沟通</h3>
        <p v-if="memberError">{{ memberError }}</p>
        <p v-for="member in members" :key="member.userId">
          {{ member.displayName }} · {{ member.role === 'OWNER' ? '组长' : '成员' }}
        </p>
        <button @click="manage">邀请与审批管理</button>
      </section>
    </template>
    <GroupsPage ref="manager" v-show="!demo && tab === 'members'" />
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
