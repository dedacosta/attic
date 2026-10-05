package com.mephys.attic.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A year of the annual contribution: a line for every heir, telling whether they pay that year
 * and which {@code portion} of the yearly amount they owe (see
 * {@link com.mephys.attic.model.Contributors}), and the total. {@code portion} is {@code null} for
 * those who do not pay, and {@code amountEur} until it is entered.
 */
public record ContributionYearResponse(int year, List<Line> lines, BigDecimal totalEur, @Nullable String comment) {

	public record Line(UUID heirId, String heir, boolean deceased, boolean pays, @Nullable Double portion,
			@Nullable BigDecimal amountEur) {
	}

}
