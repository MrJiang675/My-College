const configured = Number(import.meta.env.VITE_MAX_UPLOAD_SIZE_MB ?? 10)
export const MAX_UPLOAD_MB = Number.isFinite(configured) && configured > 0 ? configured : 10
export const MAX_UPLOAD_BYTES = MAX_UPLOAD_MB * 1024 * 1024
export const MAX_INLINE_BYTES = 2 * 1024 * 1024
export function fileLimitMessage(file: File) {
  return (
    '当前单文件上限为 ' +
    MAX_UPLOAD_MB +
    ' MB。所选文件「' +
    file.name +
    '」约 ' +
    (file.size / 1024 / 1024).toFixed(1) +
    ' MB，已取消上传。请拆分或压缩后再试。'
  )
}
