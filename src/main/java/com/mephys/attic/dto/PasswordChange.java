package com.mephys.attic.dto;

import org.jspecify.annotations.Nullable;

public record PasswordChange(@Nullable String currentPassword, @Nullable String newPassword) {
}
