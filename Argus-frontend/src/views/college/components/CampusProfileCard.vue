<script setup lang="ts">
defineProps<{
  visible: boolean
  state: { school: string; dept: string; major: string; grade: string }
}>()
const emit = defineEmits<{ save: [] }>()
</script>
<template>
  <form v-if="visible" class="profile-card panel" @submit.prevent="emit('save')">
    <div class="panel-heading">
      <div>
        <h2>校园档案</h2>
        <p>填写后，助手会在核对资格与要求时优先按你的情况提醒；其他专业功能仍可随时使用。</p>
      </div>
    </div>
    <div class="profile-grid">
      <label>学校<input v-model="state.school" required placeholder="你的学校" /></label>
      <label>学院<input v-model="state.dept" placeholder="所在学院" /></label>
      <label>专业<input v-model="state.major" required placeholder="所学专业" /></label>
      <label>年级<input v-model="state.grade" placeholder="如：大三" /></label>
    </div>
    <button class="btn-primary" type="submit">保存档案</button>
  </form>
</template>
<style scoped src="../college.css"></style>
