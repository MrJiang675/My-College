<script setup lang="ts">
defineProps<{ entries: { id: string; project: string; text: string }[]; selected?: string[] }>()
const emit = defineEmits<{ select: [id: string] }>()
</script>
<template>
  <section v-if="entries.length" class="entry-picker panel">
    <h2>选择本次申请的经历</h2>
    <label v-for="item in entries" :key="item.id">
      <input
        type="checkbox"
        :checked="selected?.includes(item.id)"
        @change="emit('select', item.id)"
      />
      <span
        ><strong>{{ item.project }}</strong
        ><small>{{ item.text }}</small></span
      >
    </label>
    <RouterLink v-if="!entries.length" to="/app/college/project">先到我的项目记录经历 →</RouterLink>
  </section>
</template>
<style scoped src="../college.css"></style>
