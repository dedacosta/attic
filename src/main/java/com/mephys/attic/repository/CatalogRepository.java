package com.mephys.attic.repository;

import com.mephys.attic.model.CatalogItem;
import com.mephys.attic.model.Picture;
import com.mephys.attic.model.PictureInfo;
import com.mephys.attic.service.PictureStorage;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {

	private final JdbcClient jdbc;

	private final PictureRepository pictures;

	public CatalogRepository(JdbcClient jdbc, PictureStorage storage) {
		this.jdbc = jdbc;
		this.pictures = new PictureRepository(jdbc, storage, "catalog_picture", "item_id", "catalog_item");
	}

	public CatalogItem save(CatalogItem item) {
		jdbc.sql("""
				INSERT INTO catalog_item (id, name, quantity, date, property_id, room_id, existent, value_cents,
					comments, created_at, updated_at)
				VALUES (:id, :name, :quantity, :date, :propertyId, :roomId, :existent, :valueCents, :comments,
					strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
				ON CONFLICT (id) DO UPDATE SET
					name = excluded.name, quantity = excluded.quantity, date = excluded.date,
					property_id = excluded.property_id, room_id = excluded.room_id,
					existent = excluded.existent,
					value_cents = excluded.value_cents, comments = excluded.comments,
					updated_at = excluded.updated_at
				""")
			.param("id", item.id().toString())
			.param("name", item.name())
			.param("quantity", item.quantity())
			.param("date", (item.date() != null) ? item.date().toString() : null)
			.param("propertyId", (item.propertyId() != null) ? item.propertyId().toString() : null)
			.param("roomId", (item.roomId() != null) ? item.roomId().toString() : null)
			.param("existent", item.existent())
			.param("valueCents", item.valueEur().movePointRight(2).longValueExact())
			.param("comments", item.comments())
			.update();
		jdbc.sql("DELETE FROM catalog_item_owner WHERE item_id = ?").param(item.id().toString()).update();
		for (UUID heirId : item.ownerIds()) {
			jdbc.sql("INSERT INTO catalog_item_owner (item_id, heir_id) VALUES (?, ?)")
				.params(item.id().toString(), heirId.toString())
				.update();
		}
		return item;
	}

	public Optional<CatalogItem> findById(UUID id) {
		Map<UUID, List<UUID>> owners = owners(id);
		return jdbc.sql("SELECT * FROM catalog_item WHERE id = ?")
			.param(id.toString())
			.query((rs, _) -> map(rs, owners))
			.optional();
	}

	public List<CatalogItem> findAll() {
		Map<UUID, List<UUID>> owners = owners(null);
		return jdbc.sql("SELECT * FROM catalog_item ORDER BY name").query((rs, _) -> map(rs, owners)).list();
	}

	/** The heirs who own the item, or every item if none is given, by item */
	private Map<UUID, List<UUID>> owners(@Nullable UUID itemId) {
		Map<UUID, List<UUID>> owners = new HashMap<>();
		jdbc.sql("SELECT item_id, heir_id FROM catalog_item_owner WHERE :itemId IS NULL OR item_id = :itemId")
			.param("itemId", (itemId != null) ? itemId.toString() : null)
			.query((rs) -> {
				owners.computeIfAbsent(UUID.fromString(rs.getString("item_id")), (_) -> new ArrayList<>())
					.add(UUID.fromString(rs.getString("heir_id")));
			});
		return owners;
	}

	public boolean deleteById(UUID id) {
		return pictures.deleteOwner(id);
	}

	public Optional<PictureInfo> addPicture(UUID itemId, Picture picture) {
		return pictures.add(itemId, picture);
	}

	public List<PictureInfo> listPictures(UUID itemId) {
		return pictures.list(itemId);
	}

	public Map<UUID, List<PictureInfo>> listAllPictures() {
		return pictures.listAll();
	}

	public Optional<Picture> findPicture(UUID itemId, UUID pictureId) {
		return pictures.find(itemId, pictureId);
	}

	public Optional<byte[]> findThumbnail(UUID itemId, UUID pictureId) {
		return pictures.findThumbnail(itemId, pictureId);
	}

	public boolean deletePicture(UUID itemId, UUID pictureId) {
		return pictures.delete(itemId, pictureId);
	}

	public void reorderPictures(UUID itemId, List<UUID> pictureIds) {
		pictures.reorder(itemId, pictureIds);
	}

	private CatalogItem map(ResultSet rs, Map<UUID, List<UUID>> owners) throws SQLException {
		String date = rs.getString("date");
		UUID id = UUID.fromString(rs.getString("id"));
		return new CatalogItem(id, rs.getString("name"), rs.getInt("quantity"),
				(date != null) ? LocalDate.parse(date) : null, uuid(rs.getString("property_id")),
				uuid(rs.getString("room_id")), rs.getBoolean("existent"), BigDecimal.valueOf(rs.getLong("value_cents"), 2),
				owners.getOrDefault(id, List.of()), rs.getString("comments"));
	}

	private static @Nullable UUID uuid(@Nullable String id) {
		return (id != null) ? UUID.fromString(id) : null;
	}

}
