package com.mephys.attic.inventory;

import com.mephys.attic.picture.Picture;
import com.mephys.attic.picture.PictureInfo;
import com.mephys.attic.picture.PictureRepository;
import com.mephys.attic.picture.PictureStorage;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class InventoryRepository {

	private final JdbcClient jdbc;

	private final PictureRepository pictures;

	InventoryRepository(JdbcClient jdbc, PictureStorage storage) {
		this.jdbc = jdbc;
		this.pictures = new PictureRepository(jdbc, storage, "inventory_picture", "item_id", "inventory_item");
	}

	InventoryItem save(InventoryItem item) {
		jdbc.sql("""
				INSERT INTO inventory_item (id, name, quantity, date, location, existent, value_cents, owner, comments,
					created_at, updated_at)
				VALUES (:id, :name, :quantity, :date, :location, :existent, :valueCents, :owner, :comments,
					strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
				ON CONFLICT (id) DO UPDATE SET
					name = excluded.name, quantity = excluded.quantity, date = excluded.date,
					location = excluded.location, existent = excluded.existent,
					value_cents = excluded.value_cents, owner = excluded.owner, comments = excluded.comments,
					updated_at = excluded.updated_at
				""")
			.param("id", item.id().toString())
			.param("name", item.name())
			.param("quantity", item.quantity())
			.param("date", (item.date() != null) ? item.date().toString() : null)
			.param("location", (item.location() != null) ? item.location().name() : null)
			.param("existent", item.existent())
			.param("valueCents", item.valueEur().movePointRight(2).longValueExact())
			.param("owner", item.owner())
			.param("comments", item.comments())
			.update();
		return item;
	}

	Optional<InventoryItem> findById(UUID id) {
		return jdbc.sql("SELECT * FROM inventory_item WHERE id = ?").param(id.toString()).query(this::map).optional();
	}

	List<InventoryItem> findAll() {
		return jdbc.sql("SELECT * FROM inventory_item ORDER BY name").query(this::map).list();
	}

	boolean deleteById(UUID id) {
		return pictures.deleteOwner(id);
	}

	Optional<PictureInfo> addPicture(UUID itemId, Picture picture) {
		return pictures.add(itemId, picture);
	}

	List<PictureInfo> listPictures(UUID itemId) {
		return pictures.list(itemId);
	}

	Map<UUID, List<PictureInfo>> listAllPictures() {
		return pictures.listAll();
	}

	Optional<Picture> findPicture(UUID itemId, UUID pictureId) {
		return pictures.find(itemId, pictureId);
	}

	Optional<byte[]> findThumbnail(UUID itemId, UUID pictureId) {
		return pictures.findThumbnail(itemId, pictureId);
	}

	boolean deletePicture(UUID itemId, UUID pictureId) {
		return pictures.delete(itemId, pictureId);
	}

	void reorderPictures(UUID itemId, List<UUID> pictureIds) {
		pictures.reorder(itemId, pictureIds);
	}

	private InventoryItem map(ResultSet rs, int rowNum) throws SQLException {
		String date = rs.getString("date");
		String location = rs.getString("location");
		return new InventoryItem(UUID.fromString(rs.getString("id")), rs.getString("name"), rs.getInt("quantity"),
				(date != null) ? LocalDate.parse(date) : null, (location != null) ? Location.valueOf(location) : null,
				rs.getBoolean("existent"), BigDecimal.valueOf(rs.getLong("value_cents"), 2), rs.getString("owner"),
				rs.getString("comments"));
	}

}
