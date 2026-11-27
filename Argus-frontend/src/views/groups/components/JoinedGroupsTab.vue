<script setup lang="ts">
import type { GroupItem } from '@/api/group'
import GroupCard from './GroupCard.vue'
import EmptyState from '@/components/EmptyState.vue'

defineProps<{
  items: GroupItem[]
  leavingIds: Set<number>
}>()

const emit = defineEmits<{
  view: [groupId: number]
  leave: [groupId: number]
}>()

function formatDate(dateStr?: string) {
  if (!dateStr) return '未知时间'
  const d = new Date(dateStr)
  if (!isNaN(d.getTime())) {
    return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`
  }
  return dateStr.replace(/[ T][\s\S]*$/, '').replace(/-/g, '.')
}
</script>

<template>
  <div>
    <EmptyState
      v-if="items.length === 0"
      title="还没有加入课程组或项目组"
      description="接受邀请，或通过组织 ID 申请加入已有小组；也可以创建自己的专属课程组。"
    />

    <div v-else class="tab-card-grid">
      <GroupCard
        v-for="group in items"
        :key="group.groupId"
        :title="group.groupName"
        :description="group.description"
        :code="group.groupCode"
        :time="`创建于 ${formatDate(group.createdAt)}`"
        tagText="成员"
        tagType="success"
        @click="emit('view', group.groupId)"
      >
        <template #actions>
          <button
            class="minimal-action-btn minimal-action-btn--danger"
            type="button"
            :disabled="leavingIds.has(group.groupId)"
            @click.stop="emit('leave', group.groupId)"
          >
            {{ leavingIds.has(group.groupId) ? '退出中...' : '退出小组' }}
          </button>
        </template>
      </GroupCard>
    </div>
  </div>
</template>

<style scoped>
.tab-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 320px), 1fr));
  gap: 24px;
}


.minimal-action-btn {
  background: none;
  border: none;
  padding: 0;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  color: var(--text-secondary);
  transition: color 0.2s;
}

.minimal-action-btn:hover {
  color: var(--brand-primary);
}

.minimal-action-btn--danger:hover {
  color: var(--el-color-danger);
}

.minimal-action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

</style>
