export type Validity = 'none' | 'valid' | 'expiring' | 'expired'

/** Documents expiring within this many days are flagged, leaving time to renew them */
export const EXPIRING_SOON_DAYS = 90

function isoDate(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

/** Validity of a document on {@code today}; a document is still valid on its last day. */
export function validity(validUntil: string | null, today = new Date()): Validity {
  if (!validUntil) {
    return 'none'
  }
  const soon = new Date(today)
  soon.setDate(soon.getDate() + EXPIRING_SOON_DAYS)
  if (validUntil < isoDate(today)) {
    return 'expired'
  }
  return validUntil <= isoDate(soon) ? 'expiring' : 'valid'
}
