import { useEffect, useRef, useState } from 'react'
import { useI18n } from '../i18n'
import { useModal } from '../lib/useModal'
import type { PdfDocument } from '../lib/pdfPages'
import { CloseIcon, DownloadIcon, MinusIcon, PlusIcon } from './icons'

interface Props {
  /** A file of the app, by URL, or a PDF made in the browser */
  source: Blob | string
  /** Shown above the pages */
  title: string
  /**
   * Name of the file the Download button saves, e.g. "attic-heirs-2026-10-17.pdf". Without it the
   * browser takes the name the server gives.
   */
  fileName?: string
  onClose: () => void
}

/** Zoom levels, as a share of the width of the screen; the pages start at 1 */
const ZOOMS = [0.5, 0.75, 1, 1.5, 2, 3]

/** Pages wider than this, in CSS pixels, are hard to read on a large screen at zoom 1 */
const MAX_PAGE_WIDTH = 900

/** Room left around the pages, in CSS pixels */
const GUTTER = 32

/** Full-screen PDF, its pages one below the other, with zoom and download. */
export default function PdfViewer({ source, title, fileName, onClose }: Props) {
  const { t } = useI18n()
  const ref = useModal()
  const stage = useRef<HTMLDivElement>(null)
  const canvases = useRef<(HTMLCanvasElement | null)[]>([])
  const [pdf, setPdf] = useState<PdfDocument | null>(null)
  const [failed, setFailed] = useState(false)
  const [zoom, setZoom] = useState(ZOOMS.indexOf(1))
  const [stageWidth, setStageWidth] = useState(0)

  useEffect(() => {
    let opened: PdfDocument | null = null
    let closed = false
    setPdf(null)
    setFailed(false)
    // Loaded on demand: PDF.js is large
    import('../lib/pdfPages')
      .then(({ openPdf }) => openPdf(source))
      .then((doc) => {
        opened = doc
        if (closed) {
          void doc.loadingTask.destroy()
        } else {
          setPdf(doc)
        }
      })
      .catch(() => { if (!closed) setFailed(true) })
    return () => {
      closed = true
      void opened?.loadingTask.destroy()
    }
  }, [source])

  useEffect(() => {
    const element = stage.current
    if (!element) {
      return
    }
    const observer = new ResizeObserver(() => setStageWidth(element.clientWidth))
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  // Draw the pages again when they come, and when their width changes
  useEffect(() => {
    if (!pdf || stageWidth === 0) {
      return
    }
    const width = Math.min(stageWidth - GUTTER, MAX_PAGE_WIDTH) * ZOOMS[zoom]
    const controller = new AbortController()
    void import('../lib/pdfPages').then(async ({ drawPage }) => {
      try {
        // One after the other, from the first: the first pages are the ones seen first
        for (let number = 1; number <= pdf.numPages && !controller.signal.aborted; number++) {
          const canvas = canvases.current[number - 1]
          if (canvas) {
            await drawPage(pdf, number, canvas, width, controller.signal)
          }
        }
      } catch {
        if (!controller.signal.aborted) {
          setFailed(true)
        }
      }
    })
    return () => controller.abort()
  }, [pdf, zoom, stageWidth])

  function download() {
    const link = document.createElement('a')
    const url = typeof source === 'string' ? source : URL.createObjectURL(source)
    link.href = url
    link.download = fileName ?? ''
    link.click()
    if (typeof source !== 'string') {
      // Once the browser has started the download
      setTimeout(() => URL.revokeObjectURL(url), 1000)
    }
  }

  return (
    <dialog ref={ref} className="pdf-viewer" aria-label={title}
      // React passes cancel on to the dialog around this one, which would close it too
      onCancel={(e) => { e.preventDefault(); e.stopPropagation(); onClose() }}>
      <div className="pdf-viewer-bar">
        <span className="pdf-viewer-name">
          {title}
          {pdf && <span className="pdf-viewer-pages"> · {t.pdfPageCount(pdf.numPages)}</span>}
        </span>
        <button type="button" className="icon-button" onClick={() => setZoom((z) => z - 1)}
          disabled={zoom === 0} aria-label={t.zoomOut} title={t.zoomOut}>
          <MinusIcon />
        </button>
        <span className="pdf-viewer-zoom" aria-hidden="true">{Math.round(ZOOMS[zoom] * 100)} %</span>
        <button type="button" className="icon-button" onClick={() => setZoom((z) => z + 1)}
          disabled={zoom === ZOOMS.length - 1} aria-label={t.zoomIn} title={t.zoomIn}>
          <PlusIcon />
        </button>
        <button type="button" className="icon-button" onClick={download} aria-label={t.downloadPdf}
          title={t.downloadPdf}>
          <DownloadIcon />
        </button>
        <button type="button" className="icon-button" onClick={onClose} aria-label={t.close} title={t.close}>
          <CloseIcon />
        </button>
      </div>
      <div ref={stage} className="pdf-viewer-stage">
        {failed ? (
          <p className="pdf-viewer-message" role="alert">{t.errorPdfOpen}</p>
        ) : pdf ? (
          Array.from({ length: pdf.numPages }, (_, i) => (
            <canvas key={i} ref={(canvas) => { canvases.current[i] = canvas }} className="pdf-viewer-page"
              aria-label={t.page(i + 1, pdf.numPages)} />
          ))
        ) : (
          <p className="pdf-viewer-message">{t.loading}</p>
        )}
      </div>
    </dialog>
  )
}
