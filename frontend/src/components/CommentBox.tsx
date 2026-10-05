import { useEffect, useState } from 'react'
import { useI18n } from '../i18n'

interface Props {
  comment: string | null
  /** Called when leaving the box, with what it holds */
  onSave: (text: string) => Promise<void>
}

/** A comment two lines high, saved when leaving it, as on contribution years and renovations */
export default function CommentBox({ comment, onSave }: Props) {
  const { t } = useI18n()
  const [text, setText] = useState(comment ?? '')

  useEffect(() => setText(comment ?? ''), [comment])

  return (
    <label className="field contribution-comment-field">
      <span>{t.comments} <small>{t.optional}</small></span>
      <textarea rows={2} maxLength={1000} value={text} onChange={(e) => setText(e.target.value)}
        onBlur={() => onSave(text)} />
    </label>
  )
}
