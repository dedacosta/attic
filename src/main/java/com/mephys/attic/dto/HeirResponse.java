package com.mephys.attic.dto;

import com.mephys.attic.model.Heir;
import com.mephys.attic.model.HeritageFlow;
import com.mephys.attic.model.HeritageShare;
import com.mephys.attic.model.Sex;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Response body for an heir. {@code heritageShare} is the share entered for an heir without a
 * parent; {@code calculatedShare} is what the heir receives through the {@link HeritageFlow}.
 */
public record HeirResponse(UUID id, String name, @Nullable LocalDate birthDate, boolean deceased,
		@Nullable LocalDate deathDate, @Nullable String address, @Nullable String filiation, @Nullable Sex sex,
		@Nullable String heritageShare, @Nullable String calculatedShare, @Nullable String comments,
		@Nullable UUID parentId, @Nullable Instant createdAt, @Nullable Instant updatedAt) {

	public static HeirResponse of(Heir heir, Map<UUID, HeritageShare> calculatedShares) {
		HeritageShare calculated = calculatedShares.get(heir.id());
		return new HeirResponse(heir.id(), heir.name(), heir.birthDate(), heir.deceased(), heir.deathDate(),
				heir.address(), heir.filiation(), heir.sex(),
				(heir.heritageShare() != null) ? heir.heritageShare().toString() : null,
				(calculated != null) ? calculated.toString() : null, heir.comments(), heir.parentId(),
				heir.createdAt(), heir.updatedAt());
	}

}
