package com.mephys.attic.repository;

import com.mephys.attic.model.Heir;
import com.mephys.attic.model.HeritageShare;
import com.mephys.attic.model.Sex;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class HeirRepository {

	private final JdbcClient jdbc;

	public HeirRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Heir save(Heir heir) {
		HeritageShare share = heir.heritageShare();
		jdbc.sql("""
				INSERT INTO heir (id, name, birth_date, address, filiation, sex, heritage_numerator,
					heritage_denominator, comments, created_at, updated_at)
				VALUES (:id, :name, :birthDate, :address, :filiation, :sex, :numerator, :denominator, :comments,
					strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
				ON CONFLICT (id) DO UPDATE SET
					name = excluded.name, birth_date = excluded.birth_date, address = excluded.address,
					filiation = excluded.filiation, sex = excluded.sex,
					heritage_numerator = excluded.heritage_numerator,
					heritage_denominator = excluded.heritage_denominator, comments = excluded.comments,
					updated_at = excluded.updated_at
				""")
			.param("id", heir.id().toString())
			.param("name", heir.name())
			.param("birthDate", (heir.birthDate() != null) ? heir.birthDate().toString() : null)
			.param("address", heir.address())
			.param("filiation", heir.filiation())
			.param("sex", (heir.sex() != null) ? heir.sex().name() : null)
			.param("numerator", (share != null) ? share.numerator() : null)
			.param("denominator", (share != null) ? share.denominator() : null)
			.param("comments", heir.comments())
			.update();
		// Read back for the timestamps the database set
		return findById(heir.id()).orElseThrow();
	}

	public Optional<Heir> findById(UUID id) {
		return jdbc.sql("SELECT * FROM heir WHERE id = ?").param(id.toString()).query(this::map).optional();
	}

	/**
	 * All heirs in the order they were added. Heirs from before timestamps were recorded come
	 * first, in the order SQLite inserted them (its rowid).
	 */
	public List<Heir> findAll() {
		return jdbc.sql("""
				SELECT * FROM heir
				ORDER BY created_at IS NOT NULL, created_at, rowid
				""").query(this::map).list();
	}

	/**
	 * Delete the heir; their documents must be deleted first.
	 */
	public boolean deleteById(UUID id) {
		return jdbc.sql("DELETE FROM heir WHERE id = ?").param(id.toString()).update() > 0;
	}

	private Heir map(ResultSet rs, int rowNum) throws SQLException {
		String birthDate = rs.getString("birth_date");
		String sex = rs.getString("sex");
		int denominator = rs.getInt("heritage_denominator");
		HeritageShare share = rs.wasNull() ? null : new HeritageShare(rs.getInt("heritage_numerator"), denominator);
		return new Heir(UUID.fromString(rs.getString("id")), rs.getString("name"),
				(birthDate != null) ? LocalDate.parse(birthDate) : null, rs.getString("address"),
				rs.getString("filiation"), (sex != null) ? Sex.valueOf(sex) : null, share, rs.getString("comments"),
				instant(rs.getString("created_at")), instant(rs.getString("updated_at")));
	}

	private static @Nullable Instant instant(@Nullable String timestamp) {
		return (timestamp != null) ? Instant.parse(timestamp) : null;
	}

}
