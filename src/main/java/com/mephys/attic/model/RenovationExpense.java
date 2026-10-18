package com.mephys.attic.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * An expense of a renovation, and whether it is paid.
 */
public record RenovationExpense(String description, ExpenseKind kind, BigDecimal amountEur, boolean paid) {

	public static final int MAX_DESCRIPTION_LENGTH = 200;

	public RenovationExpense {
		if (description == null || description.isBlank()) {
			throw new IllegalArgumentException("description must not be blank");
		}
		description = description.strip();
		if (description.length() > MAX_DESCRIPTION_LENGTH) {
			throw new IllegalArgumentException(
					"description must have at most " + MAX_DESCRIPTION_LENGTH + " characters");
		}
		kind = (kind != null) ? kind : ExpenseKind.OTHER;
		amountEur = (amountEur != null) ? amountEur.setScale(2, RoundingMode.HALF_UP) : BigDecimal.valueOf(0, 2);
		if (amountEur.signum() < 0) {
			throw new IllegalArgumentException("amountEur must not be negative");
		}
	}

}
