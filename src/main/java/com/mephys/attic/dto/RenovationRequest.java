package com.mephys.attic.dto;

import com.mephys.attic.model.Renovation;

import java.math.BigDecimal;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Request body for adding or changing a renovation; its comment is changed on its own */
public record RenovationRequest(@Nullable Integer year, @Nullable String title, @Nullable String description,
		@Nullable BigDecimal costEur) {

	public Renovation toRenovation(@Nullable UUID id, @Nullable String comment) {
		if (year == null) {
			throw new IllegalArgumentException("year must be between 1900 and 2999");
		}
		return new Renovation(id, year, title, description, costEur, comment);
	}

}
