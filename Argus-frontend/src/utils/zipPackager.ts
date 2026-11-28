/** ZIP STORE (no compression), UTF-8 filenames, CRC-32; files never leave this browser. */
export type ZipEntry = { name: string; data: Blob | Uint8Array | string }
export function safeFileName(name: string) {
  return (
    name
      .normalize('NFKC')
      .replace(/[\\/:*?"<>|\x00-\x1f]/g, '_')
      .replace(/^\.+/, '')
      .trim()
      .slice(0, 160) || 'material'
  )
}
export function crc32(data: Uint8Array) {
  let c = 0xffffffff
  for (const b of data) {
    c ^= b
    for (let k = 0; k < 8; k++) c = (c >>> 1) ^ (c & 1 ? 0xedb88320 : 0)
  }
  return (c ^ 0xffffffff) >>> 0
}
function header(size: number) {
  const bytes = new Uint8Array(size)
  return { bytes, view: new DataView(bytes.buffer) }
}
export async function makeZip(entries: ZipEntry[], preservePaths = false): Promise<Blob> {
  if (!entries.length || entries.length > 100) throw Error('材料包须为 1—100 个文件')
  const chunks: BlobPart[] = [],
    central: BlobPart[] = [],
    used = new Set<string>()
  let offset = 0,
    centralSize = 0,
    total = 0
  for (const entry of entries) {
    if (
      preservePaths &&
      entry.name
        .split('/')
        .some((p) => !p || p === '.' || p === '..' || /[\\:*?\"<>|\x00-\x1f]/.test(p))
    )
      throw Error('不安全的归档路径')
    const name = preservePaths ? entry.name : safeFileName(entry.name)
    if (used.has(name.toLowerCase())) throw Error('文件名重复：' + name)
    used.add(name.toLowerCase())
    const bytes =
      typeof entry.data === 'string'
        ? new TextEncoder().encode(entry.data)
        : entry.data instanceof Blob
          ? new Uint8Array(await entry.data.arrayBuffer())
          : entry.data
    total += bytes.byteLength
    if (total > 100 * 1024 * 1024) throw Error('浏览器材料包上限 100MB，请拆分打包')
    const encoded = new TextEncoder().encode(name),
      crc = crc32(bytes),
      local = header(30),
      v = local.view
    v.setUint32(0, 0x04034b50, true)
    v.setUint16(4, 20, true)
    v.setUint16(6, 0x800, true)
    v.setUint16(12, 33, true)
    v.setUint32(14, crc, true)
    v.setUint32(18, bytes.length, true)
    v.setUint32(22, bytes.length, true)
    v.setUint16(26, encoded.length, true)
    chunks.push(local.bytes.buffer, encoded.buffer, bytes.slice().buffer)
    const c = header(46),
      cv = c.view
    cv.setUint32(0, 0x02014b50, true)
    cv.setUint16(4, 20, true)
    cv.setUint16(6, 20, true)
    cv.setUint16(8, 0x800, true)
    cv.setUint16(14, 33, true)
    cv.setUint32(16, crc, true)
    cv.setUint32(20, bytes.length, true)
    cv.setUint32(24, bytes.length, true)
    cv.setUint16(28, encoded.length, true)
    cv.setUint32(42, offset, true)
    central.push(c.bytes.buffer, encoded.buffer)
    centralSize += 46 + encoded.length
    offset += 30 + encoded.length + bytes.length
  }
  const end = header(22),
    e = end.view
  e.setUint32(0, 0x06054b50, true)
  e.setUint16(8, entries.length, true)
  e.setUint16(10, entries.length, true)
  e.setUint32(12, centralSize, true)
  e.setUint32(16, offset, true)
  return new Blob([...chunks, ...central, end.bytes.buffer], { type: 'application/zip' })
}
export function downloadBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob),
    a = document.createElement('a')
  a.href = url
  a.download = safeFileName(name)
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
