package com.mephys.attic.dto;

import com.mephys.attic.model.Heir;
import com.mephys.attic.model.HeritageShare;
import com.mephys.attic.model.Sex;

import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Request body for creating or replacing an heir. {@code heritageShare} is a fraction such as
 * "1/3". {@code parentId} makes the heir a child of another heir. {@code deceased} may be left out:
 * a date of death is enough.
 */
public record HeirRequest(String name, @Nullable LocalDate birthDate, @Nullable Boolean deceased,
		@Nullable LocalDate deathDate, @Nullable String address,
		@Nullable String filiation, @Nullable Sex sex, @Nullable String heritageShare, @Nullable String comments,
		@Nullable UUID parentId) {

	public Heir toHeir(@Nullable UUID id) {
		HeritageShare share = (heritageShare != null && !heritageShare.isBlank()) ? HeritageShare.parse(heritageShare)
				: null;
		return new Heir(id, name, birthDate, Boolean.TRUE.equals(deceased), deathDate, address, filiation, sex, share, comments, parentId, null, null);
	}

}
