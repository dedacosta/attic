package com.mephys.attic.dto;

import com.mephys.attic.model.Renovation;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Request body for adding or changing a renovation; its comment and its expenses are changed on their own */
public record RenovationRequest(@Nullable Integer year, @Nullable String title, @Nullable String description) {

	public Renovation toRenovation(@Nullable UUID id, @Nullable String comment) {
		if (year == null) {
			throw new IllegalArgumentException("year must be between 1900 and 2999");
		}
		return new Renovation(id, year, title, description, comment);
	}

}
