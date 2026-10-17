import { AMOUNT_PATTERN, amountText, parseAmount } from './format'
import type { InventoryItem } from '../api/types'

/** An item while it is edited: the quantity and the value as typed */
export interface ItemForm {
  description: string
  quantity: string
  value: string
}

/** A new line: one of the item, without value */
export const NEW_ITEM: ItemForm = { description: '', quantity: '1', value: '' }

/** An item as it is edited. No value is shown as an empty field, not as 0. */
export function toItemForm(item: InventoryItem, locale: string): ItemForm {
  return {
    description: item.description,
    quantity: String(item.quantity),
    value: item.valueEur ? amountText(item.valueEur, locale) : '',
  }
}

/** The item of a line, or which of its fields is wrong. Its description is checked by the caller. */
export function parseItemForm(form: ItemForm): InventoryItem | 'quantity' | 'value' {
  const quantity = Number(form.quantity)
  if (form.quantity.trim() === '' || !Number.isInteger(quantity) || quantity < 0) {
    return 'quantity'
  }
  if (form.value.trim() !== '' && !AMOUNT_PATTERN.test(form.value.trim())) {
    return 'value'
  }
  return { description: form.description.trim(), quantity, valueEur: form.value.trim() ? parseAmount(form.value) : 0 }
}
