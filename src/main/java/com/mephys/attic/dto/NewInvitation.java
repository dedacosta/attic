package com.mephys.attic.dto;

import com.mephys.attic.model.Role;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

public record NewInvitation(@Nullable Role role, @Nullable UUID heirId) {
}
