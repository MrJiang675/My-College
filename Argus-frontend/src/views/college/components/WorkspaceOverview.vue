<script setup lang="ts">
const visible = defineModel<boolean>()
const emit = defineEmits<{ dismiss: [permanent: boolean] }>()
</script>
<template>
  <el-dialog v-model="visible" title="30 秒看懂 Argus" width="560px" append-to-body>
    <div class="overview">
      <p class="overview__intro">不用一次学会所有功能，沿着一条路径就能开始。</p>
      <ol>
        <li>
          <span>1</span>
          <div>
            <strong>先完善校园档案</strong>
            <p>学校、学院、专业和年级会让提醒更贴合你。</p>
          </div>
        </li>
        <li>
          <span>2</span>
          <div>
            <strong>学习或实践时直接问</strong>
            <p>贴讲义、输入实验条件，或粘贴校园通知。</p>
          </div>
        </li>
        <li>
          <span>3</span>
          <div>
            <strong>记得核对依据</strong>
            <p>展开引用、查看实验读数，并处理“需核实”项。</p>
          </div>
        </li>
        <li>
          <span>4</span>
          <div>
            <strong>把成果沉淀成经历</strong>
            <p>项目贡献经本人确认后，可在实习或升学申请中复用。</p>
          </div>
        </li>
      </ol>
    </div>
    <template #footer>
      <button type="button" class="btn-secondary" @click="emit('dismiss', true)">不再提示</button>
      <button type="button" class="btn-primary" @click="emit('dismiss', false)">开始使用</button>
    </template>
  </el-dialog>
</template>
<style scoped src="../college.css"></style>
