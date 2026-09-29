package com.mephys.attic.security;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class UserRepository {

	private static final String SELECT = "SELECT username, password_hash, role, created_at, heir_id, email, phone FROM app_user";

	private final JdbcClient jdbc;

	UserRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	boolean isEmpty() {
		return jdbc.sql("SELECT count(*) FROM app_user").query(Integer.class).single() == 0;
	}

	/** Usernames are compared ignoring case */
	Optional<StoredUser> find(String username) {
		return jdbc.sql(SELECT + " WHERE username = ?").param(username).query(this::map).optional();
	}

	List<StoredUser> findAll() {
		return jdbc.sql(SELECT + " ORDER BY created_at").query(this::map).list();
	}

	int count(Role role) {
		return jdbc.sql("SELECT count(*) FROM app_user WHERE role = ?").param(role.name()).query(Integer.class).single();
	}

	void create(String username, String passwordHash, Role role) {
		jdbc.sql("INSERT INTO app_user (username, password_hash, role, created_at) VALUES (?, ?, ?, ?)")
			.params(username, passwordHash, role.name(), Instant.now().toString())
			.update();
	}

	/** The account linked to an heir, if any */
	Optional<StoredUser> findByHeir(UUID heirId) {
		return jdbc.sql(SELECT + " WHERE heir_id = ?").param(heirId.toString()).query(this::map).optional();
	}

	boolean heirExists(UUID heirId) {
		return jdbc.sql("SELECT count(*) FROM heir WHERE id = ?").param(heirId.toString()).query(Integer.class)
			.single() > 0;
	}

	void updateContact(String username, Contact contact) {
		jdbc.sql("UPDATE app_user SET email = ?, phone = ? WHERE username = ?")
			.params(contact.email(), contact.phone(), username)
			.update();
	}

	void updateHeir(String username, @Nullable UUID heirId) {
		jdbc.sql("UPDATE app_user SET heir_id = ? WHERE username = ?")
			.params((heirId != null) ? heirId.toString() : null, username)
			.update();
	}

	void updatePassword(String username, String passwordHash) {
		jdbc.sql("UPDATE app_user SET password_hash = ? WHERE username = ?").params(passwordHash, username).update();
	}

	void updateRole(String username, Role role) {
		jdbc.sql("UPDATE app_user SET role = ? WHERE username = ?").params(role.name(), username).update();
	}

	boolean delete(String username) {
		return jdbc.sql("DELETE FROM app_user WHERE username = ?").param(username).update() > 0;
	}

	/**
	 * The value of a generated setting, created with {@code generator} the first time.
	 */
	String setting(String name, Supplier<String> generator) {
		jdbc.sql("INSERT INTO app_setting (name, value) VALUES (?, ?) ON CONFLICT (name) DO NOTHING")
			.params(name, generator.get())
			.update();
		return jdbc.sql("SELECT value FROM app_setting WHERE name = ?").param(name).query(String.class).single();
	}

	private StoredUser map(ResultSet rs, int rowNum) throws SQLException {
		String heirId = rs.getString("heir_id");
		return new StoredUser(rs.getString("username"), rs.getString("password_hash"), Role.valueOf(rs.getString("role")),
				Instant.parse(rs.getString("created_at")), (heirId != null) ? UUID.fromString(heirId) : null,
				new Contact(rs.getString("email"), rs.getString("phone")));
	}

	record StoredUser(String username, String passwordHash, Role role, Instant createdAt, @Nullable UUID heirId,
			Contact contact) {
	}

}
