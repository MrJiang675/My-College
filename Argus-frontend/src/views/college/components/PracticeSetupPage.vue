<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import http, { extractApiError } from '@/api/http'
import { usePracticeSession, hasPracticeInput } from '../usePracticeSession'
import { defaultModel } from '../models'
const props = defineProps<{ preview?: boolean }>()
const state = usePracticeSession(),
  router = useRouter(),
  input = ref<HTMLInputElement>(),
  busy = ref(false),
  error = ref('')
let controller: AbortController | undefined,
  closed = false
onUnmounted(() => {
  closed = true
  controller?.abort()
})
onMounted(async () => {
  if (props.preview) return
  try {
    const { data } = await http.get('/college/workspace')
    const p = JSON.parse(data.data.payload)
    if (!closed)
      state.draft.campusProfile = {
        school: p.school || '',
        college: p.dept || '',
        major: p.major || '',
        grade: p.grade || '',
      }
  } catch {
    /* 档案是可选背景，不阻塞实验描述。 */
  }
})
async function upload(file?: File) {
  if (!file || busy.value) return
  error.value = ''
  busy.value = true
  controller = new AbortController()
  try {
    const image = file.type.startsWith('image/') || /\.(png|jpe?g|webp)$/i.test(file.name)
    if (file.size > (image ? 10 : 15) * 1024 * 1024)
      throw Error(image ? '原图上限 10MB，请裁剪后再选择' : '材料上限 15MB，大讲义请使用知识库')
    if (image) {
      const bitmap = await createImageBitmap(file)
      try {
        if (bitmap.width * bitmap.height > 40000000) throw Error('图片超过 4000 万像素，请裁剪')
        const ratio = Math.min(1, 1400 / bitmap.width, 6000 / bitmap.height),
          canvas = document.createElement('canvas')
        canvas.width = Math.max(1, Math.round(bitmap.width * ratio))
        canvas.height = Math.max(1, Math.round(bitmap.height * ratio))
        const ctx = canvas.getContext('2d')
        if (!ctx) throw Error('浏览器不支持图片处理')
        ctx.fillStyle = 'white'
        ctx.fillRect(0, 0, canvas.width, canvas.height)
        ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
        const data = canvas.toDataURL('image/jpeg', 0.85)
        if (data.length > 2621440) throw Error('压缩后图片仍大于 2.5MB，请裁剪')
        if (!closed) {
          state.draft.imageDataUrl = data
          state.draft.materialText = undefined
          state.fileName = file.name
        }
      } finally {
        bitmap.close()
      }
    } else {
      if (!/\.(pdf|docx|txt|md)$/i.test(file.name)) throw Error('支持 PDF / DOCX / TXT / MD / 图片')
      if (props.preview) throw Error('预览不提取文件，请登录后使用')
      const form = new FormData()
      form.append('file', file)
      const { data } = await http.post('/college/files/extract-text', form, {
        timeout: 45000,
        signal: controller.signal,
      })
      if (!closed) {
        state.draft.materialText = data.data.extractedText.slice(0, 16000)
        state.draft.imageDataUrl = undefined
        state.fileName = file.name
        if (data.data.truncated || data.data.extractedText.length > 16000)
          error.value = '本轮仅载入前 16000 字，请核对材料。'
      }
    }
  } catch (e) {
    if (!closed) error.value = extractApiError(e)
  } finally {
    busy.value = false
    if (input.value) input.value.value = ''
  }
}
function paste(e: ClipboardEvent) {
  const file = Array.from(e.clipboardData?.items || [])
    .find((i) => i.kind === 'file' && i.type.startsWith('image/'))
    ?.getAsFile()
  if (file) {
    e.preventDefault()
    upload(file)
  }
}
function clear() {
  state.fileName = ''
  state.draft.imageDataUrl = undefined
  state.draft.materialText = undefined
}
async function submit() {
  error.value = ''
  if (!hasPracticeInput(state.draft)) {
    error.value = '请先写下想做的实验，或上传一份实验材料'
    return
  }
  if (props.preview) {
    await router.push('/login')
    return
  }
  state.mode = 'ai'
  state.result = undefined
  await router.push('/app/college/practice/lab')
}
function builtin() {
  state.mode = 'builtin'
  state.model ??= defaultModel('rc')
  router.push(props.preview ? '/login' : '/app/college/practice/lab')
}
function fillExample(e: { title: string; goal: string }) {
  state.draft.experimentName = e.title
  state.draft.simulationGoal = e.goal
}
const examples = [
  {
    title: '光学折射与全反射',
    goal: '绘制入射光、折射光和全反射光路，可调整入射角与两侧折射率，显示临界角并记录数据点。',
  },
  {
    title: '简谐单摆与阻尼衰减',
    goal: '展示单摆装置与角位移随时间曲线，可调摆长、重力和阻尼，支持开始暂停与记录数据点，标注小角度近似。',
  },
  {
    title: '示波器李萨如图形',
    goal: '展示两路正弦信号的李萨如图形，可调频率比、相位差和振幅，显示实时读数并记录数据点。',
  },
]
</script>
<template>
  <main class="practice-setup" @paste="paste">
    <header>
      <small>02 专业实践</small>
      <h1>你想做什么模拟实验？</h1>
      <p>先告诉我实验主题和想观察的模拟效果，或上传实验材料与截图，提交后进入专属模拟实验画面。</p>
    </header>
    <form class="setup-card" @submit.prevent="submit">
      <section>
        <label
          >实验名称（选填）<input
            v-model="state.draft.experimentName"
            maxlength="200"
            placeholder="例如：霍尔效应测螺线管磁场、阻尼单摆共振、酸碱滴定…" /></label
        ><label
          >想要什么模拟效果？<textarea
            v-model="state.draft.simulationGoal"
            maxlength="3000"
            rows="3"
            placeholder="例如：展示可拖动探头和电流滑块的磁场动态分布，实时显示磁感应强度并支持记录数据点…"
          />
        </label>
      </section>
      <section
        class="material"
        @dragover.prevent
        @drop.prevent="upload($event.dataTransfer?.files[0])"
      >
        <b>上传文件 / 截图</b>
        <p>PDF / DOCX / TXT / MD / 图片，支持拖拽与 Ctrl+V 截图</p>
        <input
          ref="input"
          hidden
          type="file"
          accept=".pdf,.docx,.txt,.md,.png,.jpg,.jpeg,.webp"
          @change="upload(($event.target as HTMLInputElement).files?.[0])"
        /><button type="button" :disabled="busy" @click="input?.click()">
          {{ busy ? '正在处理材料…' : '选择文件 / 截图' }}
        </button>
        <div v-if="state.fileName" class="file-pill">
          {{ state.fileName }}<button type="button" @click="clear">移除</button>
        </div>
        <img v-if="state.draft.imageDataUrl" :src="state.draft.imageDataUrl" alt="实验截图缩略图" />
        <details v-if="state.draft.materialText">
          <summary>核对已提取正文</summary>
          <textarea
            v-model="state.draft.materialText"
            rows="5"
            maxlength="16000"
            aria-label="实验材料正文"
          />
        </details>
        <small
          >图片在本机压缩后直接发给 DeepSeek 看图；文本文件使用正文提取，不保存到工作台。</small
        >
      </section>
      <p v-if="error" role="alert" class="error">{{ error }}</p>
      <footer>
        <label class="mode"
          >生成方式<select v-model="state.draft.reasoningMode">
            <option value="fast">快速生成</option>
            <option value="deep">深度推导</option>
          </select></label
        ><button class="primary" :disabled="busy">生成模拟实验 →</button>
      </footer>
    </form>
    <div class="examples">
      <small>不知道怎么试？点一个填入：</small
      ><button v-for="e in examples" :key="e.title" @click="fillExample(e)">
        {{ e.title }}
      </button>
    </div>
    <button class="text-link" @click="builtin">或直接打开 7 套内置确定性经典实验 →</button>
  </main>
</template>
<style scoped>
.practice-setup {
  max-width: 760px;
  margin: 22px auto;
  padding: 0 16px 28px;
  color: var(--text-primary);
}
header {
  margin-bottom: 18px;
}
h1 {
  font-size: clamp(26px, 4vw, 34px);
  line-height: 1.4;
  margin: 8px 0;
}
p {
  line-height: 1.7;
  color: var(--text-secondary);
}
header small {
  color: var(--brand-primary);
}
.setup-card {
  display: grid;
  gap: 16px;
  background: var(--surface-white);
  padding: 24px;
  border: 1px solid var(--border-default);
  border-radius: 18px;
}
label {
  display: block;
  font-weight: 550;
}
label + label {
  margin-top: 16px;
}
input:not([type='file']),
textarea,
select {
  width: 100%;
  margin-top: 9px;
  padding: 11px 13px;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  color: inherit;
  background: var(--surface-subtle);
  font: inherit;
}
textarea {
  resize: vertical;
}
.material {
  padding: 16px;
  border: 1px dashed var(--border-default);
  border-radius: 12px;
}
.material p {
  font-size: 13px;
  margin: 8px 0;
}
.material small {
  display: block;
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 12px;
}
.material img {
  display: block;
  max-width: 180px;
  max-height: 120px;
  object-fit: contain;
  margin: 10px 0;
  border-radius: 6px;
}
.file-pill {
  margin-top: 10px;
  font-size: 13px;
  overflow-wrap: anywhere;
}
button {
  padding: 9px 13px;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  background: var(--surface-subtle);
  color: inherit;
  cursor: pointer;
  font: inherit;
}
button:disabled {
  opacity: 0.5;
  cursor: default;
}
.primary {
  background: var(--brand-primary);
  color: white;
  border-color: var(--brand-primary);
  font-weight: 600;
  padding: 13px 20px;
}
footer {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 12px;
}
.mode {
  font-size: 12px;
  color: var(--text-secondary);
}
.mode select {
  width: auto;
  display: block;
}
.examples {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
  margin: 18px 0 8px;
}
.examples button {
  font-size: 12px;
  padding: 6px 9px;
}
.examples small {
  color: var(--text-secondary);
}
.text-link {
  border: 0;
  background: transparent;
  color: var(--brand-primary);
  padding-left: 0;
  font-size: 13px;
}
.error {
  color: var(--status-danger, #c34d4d);
  margin: 0;
}
summary {
  cursor: pointer;
  font-size: 13px;
  margin: 12px 0;
}
@media (max-width: 600px) {
  .practice-setup {
    margin: 20px auto;
  }
  .setup-card {
    padding: 18px;
  }
  footer {
    align-items: center;
  }
  .primary {
    padding: 12px;
  }
}
</style>
