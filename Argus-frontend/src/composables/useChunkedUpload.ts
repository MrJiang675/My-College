import axios from 'axios'
import { MAX_UPLOAD_BYTES, fileLimitMessage } from '@/constants/uploadLimits'
import { ref } from 'vue'
import { completeDocumentUpload, initDocumentUpload, uploadDocumentChunk } from '@/api/document'
import { extractApiError } from '@/api/http'

const CHUNK_SIZE = 5 * 1024 * 1024

export function useChunkedUpload() {
  const isUploading = ref(false)
  const progress = ref(0)
  const error = ref('')
  const stage = ref('')
  const hashingProgress = ref('')
  const chunkProgress = ref({ uploaded: 0, total: 0 })
  let abortController: AbortController | null = null

  const uploadFile = async (file: File, groupId: number): Promise<number | null> => {
    if (isUploading.value) return null
    reset()
    if (file.size > MAX_UPLOAD_BYTES) {
      error.value = fileLimitMessage(file)
      return null
    }
    isUploading.value = true
    stage.value = 'hashing'
    hashingProgress.value = '正在计算文件指纹...'
    const controller = new AbortController()
    abortController = controller

    try {
      const fileHash = await sha256(file, controller.signal)
      ensureNotAborted(controller.signal)
      hashingProgress.value = ''
      stage.value = 'init'

      const requestedChunkSize = Math.min(CHUNK_SIZE, file.size)
      const requestedChunkCount = Math.ceil(file.size / requestedChunkSize)
      const init = await initDocumentUpload(
        {
          groupId,
          fileName: file.name,
          fileSize: file.size,
          contentType: file.type || 'application/octet-stream',
          fileHash,
          chunkSize: requestedChunkSize,
          chunkCount: requestedChunkCount,
        },
        controller.signal,
      )

      ensureNotAborted(controller.signal)
      if (init.instantUpload) {
        if (init.documentId === null) {
          throw new Error('秒传响应缺少文档 ID')
        }
        progress.value = 100
        stage.value = ''
        return init.documentId
      }
      if (!init.uploadId) {
        throw new Error('上传初始化失败')
      }

      const uploadId = init.uploadId
      const chunkSize = init.chunkSize ?? requestedChunkSize
      const chunkCount = init.chunkCount ?? requestedChunkCount
      const uploadedChunks = new Set(init.uploadedChunks)
      chunkProgress.value = { uploaded: uploadedChunks.size, total: chunkCount }
      progress.value = Math.round((uploadedChunks.size / chunkCount) * 100)
      stage.value = 'uploading'

      for (let index = 0; index < chunkCount; index++) {
        ensureNotAborted(controller.signal)
        if (uploadedChunks.has(index)) continue

        const start = index * chunkSize
        const chunk = file.slice(start, Math.min(file.size, start + chunkSize))
        const chunkHash = await sha256(chunk, controller.signal)
        await retryChunk(
          () =>
            uploadDocumentChunk(
              {
                uploadId,
                chunkIndex: index,
                chunkHash,
                chunk,
              },
              undefined,
              controller.signal,
            ),
          controller.signal,
        )

        uploadedChunks.add(index)
        chunkProgress.value = { uploaded: uploadedChunks.size, total: chunkCount }
        progress.value = Math.round((uploadedChunks.size / chunkCount) * 100)
      }

      stage.value = 'completing'
      const documentId = await completeDocumentUpload(init.uploadId, controller.signal)
      progress.value = 100
      stage.value = ''
      return documentId
    } catch (err) {
      stage.value = ''
      const canceled = controller.signal.aborted
      controller.abort()
      error.value = canceled ? '上传已取消' : extractApiError(err, '上传失败，请重试')
      stage.value = canceled ? 'CANCELED' : 'FAILED'
      return null
    } finally {
      if (abortController === controller) {
        abortController = null
      }
      isUploading.value = false
      hashingProgress.value = ''
    }
  }

  const reset = () => {
    abortController?.abort()
    abortController = null
    isUploading.value = false
    progress.value = 0
    error.value = ''
    stage.value = ''
    hashingProgress.value = ''
    chunkProgress.value = { uploaded: 0, total: 0 }
  }

  const cancel = () => {
    abortController?.abort()
    error.value = '上传已取消'
    stage.value = ''
  }

  return {
    isUploading,
    progress,
    error,
    stage,
    hashingProgress,
    chunkProgress,
    uploadFile,
    reset,
    cancel,
  }
}

async function sha256(data: Blob, signal: AbortSignal): Promise<string> {
  ensureNotAborted(signal)
  if (!globalThis.crypto?.subtle) {
    throw new Error('当前浏览器不支持文件校验，请使用 HTTPS 或最新版浏览器')
  }
  const buffer = await data.arrayBuffer()
  ensureNotAborted(signal)
  const digest = await globalThis.crypto.subtle.digest('SHA-256', buffer)
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('')
}

function ensureNotAborted(signal: AbortSignal) {
  if (signal.aborted) {
    throw new DOMException('上传已取消', 'AbortError')
  }
}

async function retryChunk(operation: () => Promise<unknown>, signal: AbortSignal) {
  for (let attempt = 0; attempt < 3; attempt++) {
    signal.throwIfAborted()
    try {
      return await operation()
    } catch (error) {
      const status = (error as { response?: { status: number } }).response?.status
      const retryable =
        (status !== undefined && status >= 500) || (axios.isAxiosError(error) && !error.response)
      if (signal.aborted || !retryable || attempt === 2) throw error
      await new Promise<void>((resolve, reject) => {
        const timer = setTimeout(
          () => {
            signal.removeEventListener('abort', abort)
            resolve()
          },
          250 * 2 ** attempt,
        )
        const abort = () => {
          clearTimeout(timer)
          signal.removeEventListener('abort', abort)
          reject(new DOMException('已取消', 'AbortError'))
        }
        signal.addEventListener('abort', abort, { once: true })
      })
    }
  }
}
