package com.mephys.attic.dto;

import com.mephys.attic.model.RenovationExpense;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A renovation with its expenses, whose total is its cost, and a line for each heir who pays a part
 * in its year, as for the contribution, or is ticked off. {@code dueEur} is the heir's part of the cost,
 * {@code null} for one who does not pay that year; {@code paid} and {@code workmanship} tell whether
 * they paid it or did workmanship. {@code paidEur} adds up the parts settled by either,
 * {@code missingEur} those still to settle.
 */
public record RenovationResponse(UUID id, int year, String title, @Nullable String description, BigDecimal costEur,
		@Nullable String comment, List<RenovationExpense> expenses, List<Line> lines, BigDecimal paidEur,
		BigDecimal missingEur) {

	public record Line(UUID heirId, String heir, boolean deceased, @Nullable BigDecimal dueEur, boolean paid,
			boolean workmanship) {
	}

}
