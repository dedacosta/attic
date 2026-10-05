package com.mephys.attic.dto;

import org.jspecify.annotations.Nullable;

public record Credentials(@Nullable String username, @Nullable String password) {
}
