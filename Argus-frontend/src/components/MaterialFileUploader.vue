<script setup lang="ts">
import { onUnmounted, ref } from 'vue'
import http, { extractApiError } from '@/api/http'
const props = defineProps<{ disabled?: boolean }>()
const emit = defineEmits<{ extracted: [text: string, name: string] }>()
const input = ref<HTMLInputElement>(),
  busy = ref(false),
  message = ref(''),
  preview = ref('')
let controller: AbortController | undefined
function clear() {
  controller?.abort()
  if (preview.value) URL.revokeObjectURL(preview.value)
  preview.value = ''
  if (input.value) input.value.value = ''
}
onUnmounted(clear)
async function compress(file: File): Promise<File> {
  const bitmap = await createImageBitmap(file)
  try {
    if (bitmap.width * bitmap.height > 40_000_000) throw new Error('图片像素过大，请裁剪后上传。')
    const scale = Math.min(1, 1600 / bitmap.width, 6000 / bitmap.height),
      canvas = document.createElement('canvas')
    canvas.width = Math.round(bitmap.width * scale)
    canvas.height = Math.round(bitmap.height * scale)
    const context = canvas.getContext('2d')!
    context.fillStyle = '#fff'
    context.fillRect(0, 0, canvas.width, canvas.height)
    context.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
    const blob = await new Promise<Blob>((resolve, reject) =>
      canvas.toBlob((b) => (b ? resolve(b) : reject(Error('图片压缩失败'))), 'image/jpeg', 0.85),
    )
    return new File([blob], file.name.replace(/\.[^.]*$/, '') + '.jpg', { type: 'image/jpeg' })
  } finally {
    bitmap.close()
  }
}
async function upload(file?: File) {
  if (!file || busy.value || props.disabled) return
  clear()
  message.value = ''
  const image = /\.(png|jpe?g|webp)$/i.test(file.name) || file.type.startsWith('image/')
  if (!image && !/\.(txt|md|pdf|docx)$/i.test(file.name)) {
    message.value = '支持图片 / PDF / DOCX / TXT / MD'
    return
  }
  if (file.size > (image ? 5 : 15) * 1024 * 1024) {
    message.value = image
      ? '图片上限 5 MB，请裁剪或压缩后重试。'
      : '短材料上限 15 MB；更大讲义请上传知识库。'
    return
  }
  busy.value = true
  controller = new AbortController()
  try {
    if (image) {
      preview.value = URL.createObjectURL(file)
      file = await compress(file)
    }
    const form = new FormData()
    form.append('file', file)
    const { data } = await http.post('/college/files/extract-text', form, {
      timeout: 45000,
      signal: controller.signal,
    })
    emit('extracted', data.data.extractedText, data.data.fileName)
    message.value = data.data.truncated
      ? '已提取安全范围内的文字，内容有截断，请核对后发送。'
      : '提取完成，请核对修改后发送。'
  } catch (error) {
    if (!controller.signal.aborted) message.value = extractApiError(error, '提取失败，请重试。')
  } finally {
    busy.value = false
  }
}
function paste(event: ClipboardEvent) {
  const image = Array.from(event.clipboardData?.files || []).find((f) =>
    f.type.startsWith('image/'),
  )
  if (image) {
    event.preventDefault()
    void upload(image)
  }
}
function choose(event: Event) {
  void upload((event.target as HTMLInputElement).files?.[0])
}
defineExpose({ open: () => input.value?.click(), paste })
</script>
<template>
  <div class="material-uploader" tabindex="0" @paste="paste" aria-label="材料上传与截图粘贴">
    <button type="button" :disabled="busy || props.disabled" @click="input?.click()">
      {{ busy ? '正在提取文字…' : '上传文件' }}
    </button>
    <input
      ref="input"
      hidden
      type="file"
      accept=".png,.jpg,.jpeg,.webp,.pdf,.docx,.txt,.md"
      @change="choose"
    />
    <small>支持图片 / PDF / DOCX / TXT / MD；点击此区域后可 Ctrl+V 粘贴截图</small>
    <div v-if="preview" class="preview">
      <img :src="preview" alt="待识别材料预览" /><button type="button" @click="clear">
        删除图片 / 取消
      </button>
    </div>
    <p role="status">{{ message }}</p>
  </div>
</template>
<style scoped>
.material-uploader {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px dashed var(--border-default);
  border-radius: 12px;
  color: var(--text-secondary);
  background: var(--bg-input);
}
button {
  justify-self: start;
  padding: 9px 15px;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  background: var(--bg-elevated);
  color: var(--text-primary);
  cursor: pointer;
}
small {
  font-size: 12px;
}
.preview {
  display: flex;
  align-items: center;
  gap: 10px;
}
.preview img {
  max-height: 120px;
  max-width: 160px;
  object-fit: contain;
}
p {
  font-size: 13px;
}
</style>
