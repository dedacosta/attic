import { useI18n } from '../i18n'
import type { Validity } from '../lib/validity'

export default function ValidityBadge({ validity }: { validity: Validity }) {
  const { t } = useI18n()
  if (validity === 'none') {
    return null
  }
  const label = validity === 'expired' ? t.statusExpired : validity === 'expiring' ? t.statusExpiring : t.statusValid
  return <span className={`tag tag-${validity}`}>{label}</span>
}
