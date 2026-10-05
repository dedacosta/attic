package com.mephys.attic.dto;

import com.mephys.attic.model.Role;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

public record NewUser(@Nullable String username, @Nullable String password, @Nullable Role role,
		@Nullable UUID heirId, @Nullable String email, @Nullable String phone) {
}
