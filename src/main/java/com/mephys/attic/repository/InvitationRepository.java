package com.mephys.attic.repository;

import com.mephys.attic.model.Role;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class InvitationRepository {

	private static final String SELECT = "SELECT id, role, heir_id, created_by, created_at, expires_at FROM invitation";

	private final JdbcClient jdbc;

	public InvitationRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public void create(StoredInvitation invitation, String tokenHash) {
		jdbc.sql("INSERT INTO invitation (id, token_hash, role, heir_id, created_by, created_at, expires_at)"
				+ " VALUES (?, ?, ?, ?, ?, ?, ?)")
			.params(invitation.id().toString(), tokenHash, invitation.role().name(),
					(invitation.heirId() != null) ? invitation.heirId().toString() : null, invitation.createdBy(),
					invitation.createdAt().toString(), invitation.expiresAt().toString())
			.update();
	}

	/** The invitations that can still be used, oldest first */
	public List<StoredInvitation> findPending(Instant now) {
		return jdbc.sql(SELECT + " WHERE expires_at > ? ORDER BY created_at").param(now.toString()).query(this::map).list();
	}

	public Optional<StoredInvitation> find(UUID id) {
		return jdbc.sql(SELECT + " WHERE id = ?").param(id.toString()).query(this::map).optional();
	}

	/** The invitation with this token, if it can still be used */
	public Optional<StoredInvitation> findPendingByTokenHash(String tokenHash, Instant now) {
		return jdbc.sql(SELECT + " WHERE token_hash = ? AND expires_at > ?")
			.params(tokenHash, now.toString())
			.query(this::map)
			.optional();
	}

	public boolean delete(UUID id) {
		return jdbc.sql("DELETE FROM invitation WHERE id = ?").param(id.toString()).update() > 0;
	}

	public void deleteExpired(Instant now) {
		jdbc.sql("DELETE FROM invitation WHERE expires_at <= ?").param(now.toString()).update();
	}

	private StoredInvitation map(ResultSet rs, int rowNum) throws SQLException {
		String heirId = rs.getString("heir_id");
		return new StoredInvitation(UUID.fromString(rs.getString("id")), Role.valueOf(rs.getString("role")),
				(heirId != null) ? UUID.fromString(heirId) : null, rs.getString("created_by"),
				Instant.parse(rs.getString("created_at")), Instant.parse(rs.getString("expires_at")));
	}

	public record StoredInvitation(UUID id, Role role, @Nullable UUID heirId, String createdBy, Instant createdAt,
			Instant expiresAt) {
	}

}
