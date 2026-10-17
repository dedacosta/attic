/*
 * A ZIP archive of a few files, stored without compression: enough for a LaTeX source and its
 * photos, which are JPEGs and would not get smaller anyway.
 */

const CRC_TABLE = Array.from({ length: 256 }, (_, n) => {
  let c = n
  for (let k = 0; k < 8; k++) {
    c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
  }
  return c >>> 0
})

function crc32(data: Uint8Array): number {
  let crc = 0xffffffff
  for (const byte of data) {
    crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8)
  }
  return (crc ^ 0xffffffff) >>> 0
}

/** Little-endian fields of the given byte sizes */
function fields(...values: [number, 2 | 4][]): Uint8Array {
  const bytes = new Uint8Array(values.reduce((size, [, length]) => size + length, 0))
  const view = new DataView(bytes.buffer)
  let offset = 0
  for (const [value, length] of values) {
    if (length === 2) {
      view.setUint16(offset, value, true)
    } else {
      view.setUint32(offset, value, true)
    }
    offset += length
  }
  return bytes
}

/** The files, by their path in the archive, as a ZIP */
export function zip(files: { name: string; data: Uint8Array }[], now: Date): Blob {
  // MS-DOS time and date, as ZIP keeps them
  const time = (now.getHours() << 11) | (now.getMinutes() << 5) | (now.getSeconds() >> 1)
  const date = ((now.getFullYear() - 1980) << 9) | ((now.getMonth() + 1) << 5) | now.getDate()
  const parts: Uint8Array[] = []
  const directory: Uint8Array[] = []
  let offset = 0
  for (const { name, data } of files) {
    const path = new TextEncoder().encode(name)
    const crc = crc32(data)
    // Version 2.0, flag 0x800 for UTF-8 names, stored, then time, date, CRC and sizes
    const common: [number, 2 | 4][] = [[20, 2], [0x800, 2], [0, 2], [time, 2], [date, 2], [crc, 4], [data.length, 4],
      [data.length, 4], [path.length, 2], [0, 2]]
    const local = fields([0x04034b50, 4], ...common)
    parts.push(local, path, data)
    directory.push(fields([0x02014b50, 4], [20, 2], ...common, [0, 2], [0, 2], [0, 2], [0, 4], [offset, 4]), path)
    offset += local.length + path.length + data.length
  }
  const size = directory.reduce((total, part) => total + part.length, 0)
  const end = fields([0x06054b50, 4], [0, 2], [0, 2], [files.length, 2], [files.length, 2], [size, 4], [offset, 4], [0, 2])
  return new Blob([...parts, ...directory, end].map((part) => part as Uint8Array<ArrayBuffer>), { type: 'application/zip' })
}
