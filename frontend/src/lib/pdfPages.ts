import { getDocument, GlobalWorkerOptions, RenderingCancelledException, type PDFDocumentProxy } from 'pdfjs-dist'
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'

// Loaded with this module only, when a PDF is first shown: PDF.js is large
GlobalWorkerOptions.workerSrc = workerUrl

export type PdfDocument = PDFDocumentProxy

/**
 * Open a PDF for drawing its pages: a file of the app, fetched with the session cookie, or one
 * made in the browser.
 */
export async function openPdf(source: Blob | string): Promise<PdfDocument> {
  let data: ArrayBuffer
  if (typeof source === 'string') {
    const response = await fetch(source, { credentials: 'same-origin' })
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }
    data = await response.arrayBuffer()
  } else {
    data = await source.arrayBuffer()
  }
  return getDocument({ data }).promise
}

/**
 * Draw a page (counted from 1) into the canvas, `width` CSS pixels wide and sharp on the screen.
 * `signal` stops the drawing, e.g. when the zoom changes before it is done.
 */
export async function drawPage(pdf: PdfDocument, number: number, canvas: HTMLCanvasElement, width: number,
  signal: AbortSignal) {
  const page = await pdf.getPage(number)
  if (signal.aborted) {
    return
  }
  const scale = width / page.getViewport({ scale: 1 }).width
  const ratio = window.devicePixelRatio || 1
  const viewport = page.getViewport({ scale: scale * ratio })
  canvas.width = Math.floor(viewport.width)
  canvas.height = Math.floor(viewport.height)
  canvas.style.width = `${Math.floor(viewport.width / ratio)}px`
  canvas.style.height = `${Math.floor(viewport.height / ratio)}px`
  const task = page.render({ canvas, viewport })
  signal.addEventListener('abort', () => task.cancel())
  try {
    await task.promise
  } catch (e) {
    if (!(e instanceof RenderingCancelledException)) {
      throw e
    }
  }
}
