/** Exact fractions for heritage shares; BigInt so that sums of many shares cannot overflow. */
export interface Fraction {
  numerator: bigint
  denominator: bigint
}

const FRACTION = /^\s*(\d{1,9})\s*(?:\/\s*(\d{1,9})\s*)?$/

/** Parse "1/3" or "1"; null unless it is a fraction between 0 and 1 (the same rule as the server). */
export function parseFraction(text: string): Fraction | null {
  const match = FRACTION.exec(text)
  if (!match) {
    return null
  }
  const numerator = BigInt(match[1])
  const denominator = BigInt(match[2] ?? '1')
  return denominator > 0n && numerator <= denominator ? { numerator, denominator } : null
}

function gcd(a: bigint, b: bigint): bigint {
  while (b !== 0n) {
    ;[a, b] = [b, a % b]
  }
  return a
}

function reduce({ numerator, denominator }: Fraction): Fraction {
  const divisor = gcd(numerator, denominator) || 1n
  return { numerator: numerator / divisor, denominator: denominator / divisor }
}

export const ZERO: Fraction = { numerator: 0n, denominator: 1n }
export const ONE: Fraction = { numerator: 1n, denominator: 1n }

export function add(a: Fraction, b: Fraction): Fraction {
  return reduce({
    numerator: a.numerator * b.denominator + b.numerator * a.denominator,
    denominator: a.denominator * b.denominator,
  })
}

export function subtract(a: Fraction, b: Fraction): Fraction {
  return add(a, { numerator: -b.numerator, denominator: b.denominator })
}

/** -1, 0 or 1 */
export function compare(a: Fraction, b: Fraction): number {
  const difference = a.numerator * b.denominator - b.numerator * a.denominator
  return difference === 0n ? 0 : difference > 0n ? 1 : -1
}

/** "1/3", "1" for a whole, "0" for nothing */
export function formatFraction(fraction: Fraction): string {
  const { numerator, denominator } = reduce(fraction)
  return denominator === 1n ? String(numerator) : `${numerator}/${denominator}`
}

export function formatPercent(fraction: Fraction, locale: string): string {
  return new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 2 }).format(
    Number(fraction.numerator) / Number(fraction.denominator),
  )
}
