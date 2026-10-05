package com.mephys.attic.dto;

import org.jspecify.annotations.Nullable;

public record Registration(@Nullable String token, @Nullable String username, @Nullable String password,
		@Nullable String email, @Nullable String phone) {
}
