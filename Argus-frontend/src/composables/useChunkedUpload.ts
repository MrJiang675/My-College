import { ref } from 'vue'

export function useChunkedUpload() {
  const isUploading = ref(false)
  const progress = ref(0)
  const error = ref('')
  const stage = ref('')
  const hashingProgress = ref('')
  const chunkProgress = ref({ uploaded: 0, total: 0 })

  const uploadFile = async (file: File, groupId: number) => {
    throw new Error('已禁用分片上传，请上传小文件')
  }

  const reset = () => {
    isUploading.value = false
    progress.value = 0
    error.value = ''
    stage.value = ''
  }

  const cancel = () => {
    isUploading.value = false
  }

  return { isUploading, progress, error, stage, hashingProgress, chunkProgress, uploadFile, reset, cancel }
}
