package com.mephys.attic.repository;

import com.mephys.attic.model.ExpenseKind;
import com.mephys.attic.model.Renovation;
import com.mephys.attic.model.RenovationExpense;
import com.mephys.attic.model.RenovationPayment;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Renovations, with their expenses kept in cents, and which heirs have paid their part of each.
 */
@Repository
public class RenovationRepository {

	private static final String SELECT = "SELECT id, year, title, description, comment FROM renovation";

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
				INSERT INTO renovation (id, year, title, description, created_at)
				VALUES (:id, :year, :title, :description, :createdAt)
				ON CONFLICT (id) DO UPDATE SET year = excluded.year, title = excluded.title,
					description = excluded.description
				""")
			.param("id", renovation.id().toString())
			.param("year", renovation.year())
			.param("title", renovation.title())
			.param("description", renovation.description())
			.param("createdAt", Instant.now().toString())
			.update();
	}

	public void updateComment(UUID id, @Nullable String comment) {
		jdbc.sql("UPDATE renovation SET comment = :comment WHERE id = :id")
			.param("comment", comment)
			.param("id", id.toString())
			.update();
	}

	/** The expenses of every renovation, in their order */
	public Map<UUID, List<RenovationExpense>> findExpenses() {
		Map<UUID, List<RenovationExpense>> expenses = new HashMap<>();
		jdbc.sql("""
				SELECT renovation_id, description, kind, amount_cents, paid FROM renovation_expense
				ORDER BY renovation_id, position, id
				""").query((rs) -> {
			expenses.computeIfAbsent(UUID.fromString(rs.getString("renovation_id")), (_) -> new ArrayList<>())
				.add(mapExpense(rs));
		});
		return expenses;
	}

	public List<RenovationExpense> findExpenses(UUID renovationId) {
		return jdbc.sql("""
				SELECT description, kind, amount_cents, paid FROM renovation_expense
				WHERE renovation_id = ? ORDER BY position, id
				""").param(renovationId.toString()).query((rs, _) -> mapExpense(rs)).list();
	}

	/** Make the expenses of the renovation those of the list, in its order */
	public void replaceExpenses(UUID renovationId, List<RenovationExpense> expenses) {
		jdbc.sql("DELETE FROM renovation_expense WHERE renovation_id = ?").param(renovationId.toString()).update();
		for (int position = 0; position < expenses.size(); position++) {
			RenovationExpense expense = expenses.get(position);
			jdbc.sql("""
					INSERT INTO renovation_expense (renovation_id, position, description, kind, amount_cents, paid)
					VALUES (:renovationId, :position, :description, :kind, :amountCents, :paid)
					""")
				.param("renovationId", renovationId.toString())
				.param("position", position)
				.param("description", expense.description())
				.param("kind", expense.kind().name())
				.param("amountCents", expense.amountEur().movePointRight(2).longValueExact())
				.param("paid", expense.paid())
				.update();
		}
	}

	/** Delete the renovation with its expenses and payments */
	public boolean deleteById(UUID id) {
		return jdbc.sql("DELETE FROM renovation WHERE id = ?").param(id.toString()).update() > 0;
	}

	/** What the heirs gave, by renovation and heir */
	public Map<UUID, Map<UUID, RenovationPayment>> findPayments() {
		Map<UUID, Map<UUID, RenovationPayment>> payments = new HashMap<>();
		jdbc.sql("SELECT renovation_id, heir_id, paid, workmanship FROM renovation_payment").query((rs) -> {
			payments.computeIfAbsent(UUID.fromString(rs.getString("renovation_id")), (_) -> new HashMap<>())
				.put(UUID.fromString(rs.getString("heir_id")),
						new RenovationPayment(rs.getBoolean("paid"), rs.getBoolean("workmanship")));
		});
		return payments;
	}

	/** What the heir gave to the renovation; giving nothing leaves no row */
	public void setPayment(UUID renovationId, UUID heirId, RenovationPayment payment) {
		if (payment.any()) {
			jdbc.sql("""
					INSERT INTO renovation_payment (renovation_id, heir_id, paid, workmanship) VALUES (?, ?, ?, ?)
					ON CONFLICT DO UPDATE SET paid = excluded.paid, workmanship = excluded.workmanship
					""")
				.params(renovationId.toString(), heirId.toString(), payment.paid(), payment.workmanship())
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
				rs.getString("description"), rs.getString("comment"));
	}

	private static RenovationExpense mapExpense(ResultSet rs) throws SQLException {
		return new RenovationExpense(rs.getString("description"), ExpenseKind.valueOf(rs.getString("kind")),
				BigDecimal.valueOf(rs.getLong("amount_cents"), 2), rs.getBoolean("paid"));
	}

}
