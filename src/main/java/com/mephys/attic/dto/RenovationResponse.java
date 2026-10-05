package com.mephys.attic.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A renovation with a line for each heir who pays that year, and anyone else marked as paid.
 * {@code dueEur} is the heir's part of the cost, {@code null} for one who does not pay that year.
 * {@code paidEur} adds up the parts paid, {@code missingEur} those still to pay.
 */
public record RenovationResponse(UUID id, int year, String title, @Nullable String description, BigDecimal costEur,
		@Nullable String comment, List<Line> lines, BigDecimal paidEur, BigDecimal missingEur) {

	public record Line(UUID heirId, String heir, boolean deceased, @Nullable BigDecimal dueEur, boolean paid) {
	}

}
