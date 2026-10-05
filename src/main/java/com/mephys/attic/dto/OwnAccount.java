package com.mephys.attic.dto;

import com.mephys.attic.model.Role;

import org.jspecify.annotations.Nullable;

public record OwnAccount(String username, Role role, @Nullable String email, @Nullable String phone) {
}
