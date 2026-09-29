package com.mephys.attic.heir;

import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Request body for creating or replacing an heir. {@code heritageShare} is a fraction such as
 * "1/3".
 */
record HeirRequest(String name, @Nullable LocalDate birthDate, @Nullable String address,
		@Nullable String filiation, @Nullable Sex sex, @Nullable String heritageShare, @Nullable String comments) {

	Heir toHeir(@Nullable UUID id) {
		HeritageShare share = (heritageShare != null && !heritageShare.isBlank()) ? HeritageShare.parse(heritageShare)
				: null;
		return new Heir(id, name, birthDate, address, filiation, sex, share, comments, null, null);
	}

}
