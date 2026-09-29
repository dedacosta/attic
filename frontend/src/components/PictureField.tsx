import { useEffect, useMemo } from 'react'
import { useI18n } from '../i18n'
import { ImageIcon } from './icons'
import type { PictureChange } from '../api/types'

export const PICTURE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/heic', 'image/avif']

// HEIC is accepted but not offered: then iPhones convert photos to JPEG, which gets a thumbnail
const OFFERED_PICTURE_TYPES = PICTURE_TYPES.filter((type) => type !== 'image/heic').join(',')

interface Props {
  /** Thumbnail or picture currently stored, if any */
  current: string | null
  /** Full-size picture currently stored, if any */
  fullUrl: string | null
  change: PictureChange
  onChange: (change: PictureChange) => void
  onError: (message: string | null) => void
  /** Only show the picture, without add / change / remove */
  readOnly?: boolean
}

/** Picture preview with add / change / remove buttons, used by the item and document forms. */
export default function PictureField({ current, fullUrl, change, onChange, onError, readOnly = false }: Props) {
  const { t } = useI18n()

  const newPictureUrl = useMemo(
    () => (change.kind === 'replace' ? URL.createObjectURL(change.file) : null),
    [change],
  )
  useEffect(() => () => { if (newPictureUrl) URL.revokeObjectURL(newPictureUrl) }, [newPictureUrl])

  const preview = change.kind === 'replace' ? newPictureUrl : change.kind === 'remove' ? null : current

  function choose(file: File | undefined) {
    if (!file) {
      return
    }
    if (!PICTURE_TYPES.includes(file.type)) {
      onError(t.errorPictureType)
      return
    }
    onError(null)
    onChange({ kind: 'replace', file })
  }

  return (
    <div className="picture-field">
      <div className="picture-preview">
        {preview ? <img src={preview} alt="" /> : <ImageIcon width={48} height={48} />}
      </div>
      <div className="picture-actions">
        <label className="button" hidden={readOnly}>
          {preview ? t.changePicture : t.addPicture}
          <input type="file" accept={OFFERED_PICTURE_TYPES} hidden
            onChange={(e) => { choose(e.target.files?.[0]); e.target.value = '' }} />
        </label>
        {preview && !readOnly && (
          <button type="button" className="button button-quiet"
            onClick={() => onChange(current ? { kind: 'remove' } : { kind: 'keep' })}>
            {t.removePicture}
          </button>
        )}
        {change.kind === 'keep' && fullUrl && (
          <a className="link" href={fullUrl} target="_blank" rel="noreferrer">
            {t.openFullPicture}
          </a>
        )}
      </div>
    </div>
  )
}
