package com.mephys.attic.dto;

import com.mephys.attic.model.Heir;
import com.mephys.attic.model.Sex;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

public record HeirResponse(UUID id, String name, @Nullable LocalDate birthDate, @Nullable String address,
		@Nullable String filiation, @Nullable Sex sex, @Nullable String heritageShare, @Nullable String comments,
		@Nullable Instant createdAt, @Nullable Instant updatedAt) {

	public static HeirResponse of(Heir heir) {
		return new HeirResponse(heir.id(), heir.name(), heir.birthDate(), heir.address(), heir.filiation(),
				heir.sex(), (heir.heritageShare() != null) ? heir.heritageShare().toString() : null,
				heir.comments(), heir.createdAt(), heir.updatedAt());
	}

}
