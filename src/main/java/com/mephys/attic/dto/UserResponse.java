package com.mephys.attic.dto;

import com.mephys.attic.model.Role;
import com.mephys.attic.repository.UserRepository;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

public record UserResponse(String username, Role role, Instant createdAt, @Nullable UUID heirId, @Nullable String email,
		@Nullable String phone) {

	public static UserResponse of(UserRepository.StoredUser user) {
		return new UserResponse(user.username(), user.role(), user.createdAt(), user.heirId(),
				user.contact().email(), user.contact().phone());
	}

}
