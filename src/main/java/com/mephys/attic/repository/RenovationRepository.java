package com.mephys.attic.repository;

import com.mephys.attic.model.Renovation;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Renovations, with their cost kept in cents, and which heirs have paid their part of each.
 */
@Repository
public class RenovationRepository {

	private static final String SELECT = "SELECT id, year, title, description, cost_cents, comment FROM renovation";

	private final JdbcClient jdbc;

	public RenovationRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** The latest year first, and within a year the latest added first */
	public List<Renovation> findAll() {
		return jdbc.sql(SELECT + " ORDER BY year DESC, created_at DESC").query(this::map).list();
	}

	public Optional<Renovation> findById(UUID id) {
		return jdbc.sql(SELECT + " WHERE id = ?").param(id.toString()).query(this::map).optional();
	}

	/** Add or change the renovation; its comment is changed with {@link #updateComment} */
	public void save(Renovation renovation) {
		jdbc.sql("""
				INSERT INTO renovation (id, year, title, description, cost_cents, created_at)
				VALUES (:id, :year, :title, :description, :costCents, :createdAt)
				ON CONFLICT (id) DO UPDATE SET year = excluded.year, title = excluded.title,
					description = excluded.description, cost_cents = excluded.cost_cents
				""")
			.param("id", renovation.id().toString())
			.param("year", renovation.year())
			.param("title", renovation.title())
			.param("description", renovation.description())
			.param("costCents", renovation.costEur().movePointRight(2).longValueExact())
			.param("createdAt", Instant.now().toString())
			.update();
	}

	public void updateComment(UUID id, @Nullable String comment) {
		jdbc.sql("UPDATE renovation SET comment = ? WHERE id = ?").params(comment, id.toString()).update();
	}

	/** Delete the renovation with its payments */
	public boolean deleteById(UUID id) {
		return jdbc.sql("DELETE FROM renovation WHERE id = ?").param(id.toString()).update() > 0;
	}

	/** The heirs who paid, by renovation */
	public Map<UUID, Set<UUID>> findPayments() {
		Map<UUID, Set<UUID>> payments = new HashMap<>();
		jdbc.sql("SELECT renovation_id, heir_id FROM renovation_payment").query((rs) -> {
			payments.computeIfAbsent(UUID.fromString(rs.getString("renovation_id")), (id) -> new HashSet<>())
				.add(UUID.fromString(rs.getString("heir_id")));
		});
		return payments;
	}

	public void setPaid(UUID renovationId, UUID heirId, boolean paid) {
		if (paid) {
			jdbc.sql("INSERT INTO renovation_payment (renovation_id, heir_id) VALUES (?, ?) ON CONFLICT DO NOTHING")
				.params(renovationId.toString(), heirId.toString())
				.update();
		}
		else {
			jdbc.sql("DELETE FROM renovation_payment WHERE renovation_id = ? AND heir_id = ?")
				.params(renovationId.toString(), heirId.toString())
				.update();
		}
	}

	private Renovation map(ResultSet rs, int rowNum) throws SQLException {
		return new Renovation(UUID.fromString(rs.getString("id")), rs.getInt("year"), rs.getString("title"),
				rs.getString("description"), BigDecimal.valueOf(rs.getLong("cost_cents"), 2), rs.getString("comment"));
	}

}
