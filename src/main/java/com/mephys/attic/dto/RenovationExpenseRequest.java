package com.mephys.attic.dto;

import com.mephys.attic.model.ExpenseKind;
import com.mephys.attic.model.RenovationExpense;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

/**
 * An expense in a request body. Omitted fields get their defaults: another kind of expense, no
 * amount, not paid.
 */
public record RenovationExpenseRequest(String description, @Nullable ExpenseKind kind, @Nullable BigDecimal amountEur,
		@Nullable Boolean paid) {

	public RenovationExpense toExpense() {
		return new RenovationExpense(description, kind, amountEur, paid != null && paid);
	}

}
