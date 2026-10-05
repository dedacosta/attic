package com.mephys.attic.dto;

import com.mephys.attic.model.Role;
import com.mephys.attic.repository.InvitationRepository;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** {@code token} is only set in the answer to creating the invitation */
public record InvitationResponse(UUID id, Role role, @Nullable UUID heirId, String createdBy, Instant createdAt,
		Instant expiresAt, @Nullable String token) {

	public static InvitationResponse of(InvitationRepository.StoredInvitation invitation, @Nullable String token) {
		return new InvitationResponse(invitation.id(), invitation.role(), invitation.heirId(),
				invitation.createdBy(), invitation.createdAt(), invitation.expiresAt(), token);
	}

}
