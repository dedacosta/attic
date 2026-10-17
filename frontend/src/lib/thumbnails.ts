import type { CatalogItem } from '../api/types'

/** Size of the photo next to an ID in the catalog exports, in mm */
export const THUMBNAIL_SIZE = 10

/** A thumbnail as a small square JPEG data URL, cut to its middle; null when it cannot be loaded */
async function loadThumbnail(url: string): Promise<string | null> {
  try {
    const response = await fetch(url)
    if (!response.ok) {
      return null
    }
    const image = await createImageBitmap(await response.blob())
    // 120 pixels for 10 mm: about 300 dpi, sharp enough in print
    const canvas = document.createElement('canvas')
    canvas.width = canvas.height = 120
    const side = Math.min(image.width, image.height)
    canvas.getContext('2d')?.drawImage(image, (image.width - side) / 2, (image.height - side) / 2, side, side, 0, 0, 120, 120)
    image.close()
    return canvas.toDataURL('image/jpeg', 0.85)
  } catch {
    return null
  }
}

/** The cover of each item, small, in the order of the items; null for an item without one */
export const loadThumbnails = (items: CatalogItem[]) =>
  Promise.all(items.map((item) =>
    item.pictures[0]?.thumbnailUrl ? loadThumbnail(item.pictures[0].thumbnailUrl) : Promise.resolve(null)))
