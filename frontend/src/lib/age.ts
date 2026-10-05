/** Age in whole years on {@code today} (e.g. the date of death) for an ISO birth date. */
export function age(birthDate: string, today = new Date()): number {
  const [year, month, day] = birthDate.split('-').map(Number)
  const hadBirthday = today.getMonth() + 1 > month || (today.getMonth() + 1 === month && today.getDate() >= day)
  return today.getFullYear() - year - (hadBirthday ? 0 : 1)
}

/** An ISO date (2024-03-01) as a local date, so the day does not shift with the time zone. */
export function localDate(isoDate: string): Date {
  const [year, month, day] = isoDate.split('-').map(Number)
  return new Date(year, month - 1, day)
}
