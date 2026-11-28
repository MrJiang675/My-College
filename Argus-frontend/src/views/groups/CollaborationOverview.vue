<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import http, { extractApiError } from '@/api/http'
import { fetchGroups, type GroupItem } from '@/api/group'
const emit = defineEmits<{ select: [group: GroupItem]; create: []; join: []; demo: [] }>()
const app = useAppStore(),
  auth = useAuthStore(),
  filter = ref('全部'),
  message = ref(''),
  projectCount = ref(0)
const stats = ref<
  {
    groupId: number
    memberCount: number
    documentCount: number
    readyCount: number
    pendingRequestCount: number
  }[]
>([])
const scenes = ['全部', '竞赛组队', '课程设计', '科研合作', '学习互助']
const scene = (group: GroupItem) =>
  group.description?.match(/^【(竞赛组队|课程设计|科研合作|学习互助)】/)?.[1] || '未分类'
const visible = computed(() =>
  app.visibleGroups.filter((g) => filter.value === '全部' || scene(g) === filter.value),
)
const count = (id: number) => stats.value.find((s) => s.groupId === id)
async function load() {
  try {
    app.applyGroupQueryResult(await fetchGroups())
    stats.value = (await http.get('/groups/collaboration')).data.data
    const state = JSON.parse((await http.get('/college/workspace')).data.data.payload)
    projectCount.value = Object.entries(state.pages || {}).filter(
      ([key, value]) =>
        key.startsWith('project-') &&
        !!(value as { source?: string }).source &&
        (value as { projectStatus?: string }).projectStatus !== 'DONE',
    ).length
  } catch (e) {
    message.value = extractApiError(e, '协作概览暂未刷新，可继续使用下方管理功能。')
  }
}
onMounted(load)
defineExpose({ reload: load })
async function copyInvite(group: GroupItem) {
  const text = `【校园组队】${group.groupName}\n${group.description || '目标与缺口角色待组长补充'}\n加入方式：在 Argus 协作小组选择“通过邀请加入”，输入小组编码 ${group.groupCode} 提交申请，由组长审核。\n请先沟通分工与时间安排，再确认加入。`
  try {
    await navigator.clipboard.writeText(text)
    message.value = '已复制招人/邀请文案，可发到班级群。'
  } catch {
    message.value = '复制失败，请手动复制小组编码：' + group.groupCode
  }
}
</script>
<template>
  <section class="collaboration-overview">
    <header>
      <div>
        <small>找队友 · 共用资料 · 说清个人贡献</small>
        <h2>校园协作空间</h2>
      </div>
      <div>
        <button @click="emit('create')">快速创建小组</button
        ><button @click="emit('join')">通过邀请加入</button><button @click="load">刷新</button>
      </div>
    </header>
    <div class="collab-stats">
      <div>
        <b>{{ app.visibleGroups.length }}</b
        ><span>我参与的小组</span>
      </div>
      <div>
        <b>{{ projectCount }}</b
        ><span>进行中协作项目（本人）</span>
      </div>
      <div>
        <b>{{ stats.reduce((n, s) => n + Number(s.documentCount), 0) }}</b
        ><span>团队共享资料</span>
      </div>
      <div>
        <b>{{ stats.reduce((n, s) => n + Number(s.pendingRequestCount), 0) }}</b
        ><span>待我处理的加入申请</span>
      </div>
    </div>
    <nav aria-label="小组场景筛选">
      <button v-for="s in scenes" :key="s" :aria-pressed="filter === s" @click="filter = s">
        {{ s }}
      </button>
    </nav>
    <div class="collab-cards">
      <article v-for="group in visible" :key="group.groupId">
        <small
          >{{ scene(group) }} · {{ group.relation === 'OWNER' ? '我是组长' : '我是成员' }}</small
        >
        <h3>{{ group.groupName }}</h3>
        <p>{{ group.description || '暂未填写目标和招募方向，可先沟通再确定分工。' }}</p>
        <p>
          {{ count(group.groupId)?.memberCount ?? '—' }} 名成员 ·
          {{ count(group.groupId)?.documentCount ?? '—' }} 份资料 · READY
          {{ count(group.groupId)?.readyCount ?? '—' }}
        </p>
        <button @click="emit('select', group)">进入小组工作区</button
        ><button @click="copyInvite(group)">一键复制招人/邀请文案</button>
      </article>
    </div>
    <p v-if="!visible.length">当前场景没有小组。可创建一个真实小组，或用已有编码申请加入。</p>
    <article v-if="auth.currentUser?.userCode.startsWith('demo_guest_')" class="demo-card">
      <small>评委隔离账号 · 只读协作场景示例，不计入真实成员和资料统计</small>
      <h3>校园出行调查队（演示）</h3>
      <p>
        三人分工：组长统筹、本人负责数据清洗、队友负责问卷与展示。共享资料示例：调研方案、数据字典。当前目标：明确各自贡献，整理比赛报名材料。
      </p>
      <button @click="emit('demo')">查看演示协作空间</button
      ><button @click="emit('create')">创建自己的真实小组</button>
    </article>
    <p v-if="message" role="status">{{ message }}</p>
  </section>
</template>
<style scoped>
.collaboration-overview {
  display: grid;
  gap: 16px;
  margin-bottom: 20px;
  color: var(--text-primary);
}
header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
header div:last-child,
nav {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
small {
  color: var(--text-secondary);
}
h2 {
  font-size: 22px;
}
button {
  border: 1px solid var(--border-default);
  background: var(--bg-surface);
  color: var(--text-primary);
  padding: 9px 12px;
  border-radius: 9px;
  cursor: pointer;
}
button[aria-pressed='true'] {
  background: var(--accent-soft);
  border-color: var(--accent-primary);
}
.collab-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}
.collab-stats div {
  display: grid;
  padding: 14px;
  border: 1px solid var(--border-default);
  background: var(--bg-surface);
  border-radius: 12px;
}
.collab-stats b {
  font-size: 24px;
}
.collab-stats span {
  font-size: 12px;
  color: var(--text-secondary);
}
.collab-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 12px;
}
article {
  padding: 18px;
  background: var(--bg-surface);
  border: 1px solid var(--border-default);
  border-radius: 14px;
  overflow-wrap: anywhere;
}
p {
  white-space: pre-wrap;
  line-height: 1.7;
  font-size: 13px;
  margin: 10px 0;
}
article button {
  margin: 4px;
}
.demo-card {
  border-style: dashed;
  background: var(--accent-soft);
}
@media (max-width: 600px) {
  .collab-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .collab-cards {
    grid-template-columns: 1fr;
  }
}
</style>
