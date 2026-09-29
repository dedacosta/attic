/** Age in whole years on {@code today} for an ISO birth date. */
export function age(birthDate: string, today = new Date()): number {
  const [year, month, day] = birthDate.split('-').map(Number)
  const hadBirthday = today.getMonth() + 1 > month || (today.getMonth() + 1 === month && today.getDate() >= day)
  return today.getFullYear() - year - (hadBirthday ? 0 : 1)
}
