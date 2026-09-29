import type { Messages } from '../i18n'

// The same rules as the server
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const PHONE = /^[+0-9 ()-]{3,30}$/

/** The error to show for these contact details, or null when they are fine (both are optional) */
export function contactError(email: string, phone: string, t: Messages): string | null {
  if (email.trim() && !EMAIL.test(email.trim())) {
    return t.errorEmail
  }
  if (phone.trim() && (!PHONE.test(phone.trim()) || phone.replace(/\D/g, '').length < 3)) {
    return t.errorPhone
  }
  return null
}
