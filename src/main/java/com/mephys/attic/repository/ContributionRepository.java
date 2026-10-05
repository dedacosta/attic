package com.mephys.attic.repository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The years of the annual contribution and the amounts the heirs contributed, kept in cents.
 */
@Repository
public class ContributionRepository {

	private final JdbcClient jdbc;

	public ContributionRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** The years, the latest first */
	public List<StoredYear> findYears() {
		return jdbc.sql("SELECT year, comment FROM contribution_year ORDER BY year DESC")
			.query((rs, rowNum) -> new StoredYear(rs.getInt("year"), rs.getString("comment")))
			.list();
	}

	public Optional<StoredYear> findYear(int year) {
		return jdbc.sql("SELECT year, comment FROM contribution_year WHERE year = ?")
			.param(year)
			.query((rs, rowNum) -> new StoredYear(rs.getInt("year"), rs.getString("comment")))
			.optional();
	}

	public boolean yearExists(int year) {
		return findYear(year).isPresent();
	}

	public void updateComment(int year, @Nullable String comment) {
		jdbc.sql("UPDATE contribution_year SET comment = ? WHERE year = ?").params(comment, year).update();
	}

	/** @return whether the year was added, false if it already existed */
	public boolean createYear(int year) {
		return jdbc.sql("INSERT INTO contribution_year (year) VALUES (?) ON CONFLICT (year) DO NOTHING")
			.param(year)
			.update() > 0;
	}

	/** Delete the year with its amounts */
	public boolean deleteYear(int year) {
		return jdbc.sql("DELETE FROM contribution_year WHERE year = ?").param(year).update() > 0;
	}

	/** The amounts of every year, by year and heir */
	public Map<Integer, Map<UUID, BigDecimal>> findAmounts() {
		Map<Integer, Map<UUID, BigDecimal>> amounts = new HashMap<>();
		jdbc.sql("SELECT year, heir_id, amount_cents FROM contribution").query((rs) -> {
			amounts.computeIfAbsent(rs.getInt("year"), (year) -> new HashMap<>())
				.put(UUID.fromString(rs.getString("heir_id")), BigDecimal.valueOf(rs.getLong("amount_cents"), 2));
		});
		return amounts;
	}

	public void saveAmount(int year, UUID heirId, BigDecimal amountEur) {
		jdbc.sql("""
				INSERT INTO contribution (year, heir_id, amount_cents) VALUES (?, ?, ?)
				ON CONFLICT (year, heir_id) DO UPDATE SET amount_cents = excluded.amount_cents
				""")
			.params(year, heirId.toString(), amountEur.movePointRight(2).longValueExact())
			.update();
	}

	public void deleteAmount(int year, UUID heirId) {
		jdbc.sql("DELETE FROM contribution WHERE year = ? AND heir_id = ?").params(year, heirId.toString()).update();
	}

	public record StoredYear(int year, @Nullable String comment) {
	}

}
