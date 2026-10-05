package com.mephys.attic.dto;

import com.mephys.attic.model.Role;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

public record SessionResponse(boolean authenticated, @Nullable String username, @Nullable Role role,
		@Nullable UUID heirId, boolean setupRequired) {
}
