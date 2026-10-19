package com.mephys.attic.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

/**
 * V1 is the whole schema: it replaces the migrations V2 to V10 that databases were brought up to
 * date with before. A database that ran all of them has the schema of V1, so its history is
 * dropped and Flyway marks it as V1 (see {@code spring.flyway.baseline-on-migrate}); only the
 * migrations after V1 then run. A database that stopped before V10 cannot be brought up to date
 * from V1 and is refused.
 */
@SuppressWarnings("SqlResolve") // Flyway's history and SQLite's catalog are not in the migrations
final class SquashedMigrations {

	/** The migrations that V1 replaces, in their order */
	static final List<String> REPLACED = List.of("V2__inventory_rooms.sql", "V3__inventory_per_building.sql",
			"V4__inventory_room_order.sql", "V5__inventory_room_pdf_title.sql", "V6__inventory_item_value.sql",
			"V7__inventory_room_description.sql", "V8__catalog_item_room.sql", "V9__renovation_expenses.sql",
			"V10__renovation_workmanship_by_heir.sql");

	private SquashedMigrations() {
	}

	/**
	 * Drop the history of a database that ran all the replaced migrations.
	 * @throws IllegalStateException if it ran only some of them
	 */
	static void convert(DataSource dataSource) throws SQLException {
		try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
			try (ResultSet table = statement.executeQuery(
					"SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'flyway_schema_history'")) {
				if (!table.next()) {
					return;
				}
			}
			Set<String> applied = new HashSet<>();
			try (ResultSet rows = statement
				.executeQuery("SELECT script FROM flyway_schema_history WHERE success = 1")) {
				while (rows.next()) {
					applied.add(rows.getString(1));
				}
			}
			List<String> ran = REPLACED.stream().filter(applied::contains).toList();
			if (ran.isEmpty()) {
				return;
			}
			if (ran.size() < REPLACED.size()) {
				throw new IllegalStateException("The database ran the migrations up to " + ran.getLast()
						+ " only. Start it once with the last version of Attic that has the migrations up to "
						+ REPLACED.getLast() + ", then with this one.");
			}
			statement.execute("DROP TABLE flyway_schema_history");
		}
	}

}
