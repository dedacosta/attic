package com.mephys.attic.repository;

import com.mephys.attic.dto.InventoryRoomName;
import com.mephys.attic.model.InventoryItem;
import com.mephys.attic.model.InventoryRoom;
import com.mephys.attic.model.Picture;
import com.mephys.attic.model.PictureInfo;
import com.mephys.attic.service.PictureStorage;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The inventory of each building: its rooms with their items and photos, and its two lists of
 * items without a room, the other items and the missing items.
 * <p>
 * Saving replaces several rows and must run in a transaction; the callers' controller methods
 * are {@code @Transactional}.
 */
@Repository
public class InventoryRepository {

	private final JdbcClient jdbc;

	private final PictureRepository pictures;

	public InventoryRepository(JdbcClient jdbc, PictureStorage storage) {
		this.jdbc = jdbc;
		this.pictures = new PictureRepository(jdbc, storage, "inventory_picture", "room_id", "inventory_room");
	}

	/**
	 * Insert or update the room and replace its items. A new room comes after the others of its
	 * building; the building, the place and the description of an existing room are not changed.
	 */
	public InventoryRoom save(InventoryRoom room) {
		jdbc.sql("""
				INSERT INTO inventory_room (id, property_id, position, title, description, pdf_title, comments,
					created_at, updated_at)
				VALUES (:id, :propertyId, (SELECT count(*) FROM inventory_room WHERE property_id = :propertyId),
					:title, :description, :pdfTitle, :comments,
					strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
				ON CONFLICT (id) DO UPDATE SET
					title = excluded.title, pdf_title = excluded.pdf_title, comments = excluded.comments,
					updated_at = excluded.updated_at
				""")
			.param("id", room.id().toString())
			.param("propertyId", room.propertyId().toString())
			.param("title", room.title())
			.param("description", room.description())
			.param("pdfTitle", room.pdfTitle())
			.param("comments", room.comments())
			.update();
		jdbc.sql("DELETE FROM inventory_item WHERE room_id = ?").param(room.id().toString()).update();
		insertItems(room.id(), null, true, room.items());
		return findById(room.id()).orElseThrow();
	}

	public Optional<InventoryRoom> findById(UUID id) {
		List<InventoryItem> items = jdbc
			.sql("SELECT description, quantity, value_cents FROM inventory_item WHERE room_id = ? ORDER BY position, id")
			.param(id.toString())
			.query((rs, rowNum) -> mapItem(rs))
			.list();
		return jdbc.sql("SELECT id, property_id, title, description, pdf_title, comments FROM inventory_room WHERE id = ?")
			.param(id.toString())
			.query((rs, rowNum) -> mapRoom(rs, items))
			.optional();
	}

	/**
	 * The rooms of the building, or of every building if none is given, in the order of the
	 * building's list.
	 */
	public List<InventoryRoom> findAll(@Nullable UUID propertyId) {
		Map<UUID, List<InventoryItem>> items = jdbc.sql("""
				SELECT room_id, description, quantity, value_cents FROM inventory_item
				WHERE room_id IS NOT NULL ORDER BY room_id, position, id
				""")
			.query((rs, rowNum) -> Map.entry(UUID.fromString(rs.getString("room_id")), mapItem(rs)))
			.list()
			.stream()
			.collect(Collectors.groupingBy(Map.Entry::getKey,
					Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
		return jdbc.sql("""
				SELECT id, property_id, title, description, pdf_title, comments FROM inventory_room
				WHERE :propertyId IS NULL OR property_id = :propertyId
				ORDER BY property_id, position, created_at, rowid
				""")
			.param("propertyId", (propertyId != null) ? propertyId.toString() : null)
			.query((rs, rowNum) -> mapRoom(rs, items.getOrDefault(UUID.fromString(rs.getString("id")), List.of())))
			.list();
	}

	/**
	 * Make the rooms of the building those of the list, in its order: rooms with an id are
	 * renamed, get the description of the list and keep their PDF title, comments, items and photos, rooms without one are added, and the
	 * building's rooms that are not in the list are deleted with all they have.
	 * @throws IllegalArgumentException if a room of the list is not a room of the building
	 */
	public void replaceRooms(UUID propertyId, List<InventoryRoomName> rooms) {
		Set<UUID> removed = new HashSet<>(findAll(propertyId).stream().map(InventoryRoom::id).toList());
		for (int position = 0; position < rooms.size(); position++) {
			InventoryRoomName room = rooms.get(position);
			if (room.id() == null) {
				jdbc.sql("""
						INSERT INTO inventory_room (id, property_id, position, title, description, created_at, updated_at)
						VALUES (?, ?, ?, ?, ?, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
						""")
					.params(UUID.randomUUID().toString(), propertyId.toString(), position, room.title(), room.description())
					.update();
			}
			else if (removed.remove(room.id())) {
				jdbc.sql("""
						UPDATE inventory_room SET position = :position, title = :title, description = :description,
							updated_at = CASE WHEN title = :title AND description IS :description THEN updated_at
								ELSE strftime('%Y-%m-%dT%H:%M:%fZ', 'now') END
						WHERE id = :id
						""")
					.param("position", position)
					.param("title", room.title())
					.param("description", room.description())
					.param("id", room.id().toString())
					.update();
			}
			else {
				throw new IllegalArgumentException("the rooms must be rooms of the building, each once");
			}
		}
		removed.forEach(pictures::deleteOwner);
	}

	/**
	 * Delete the room with its items, photos and their files.
	 */
	public boolean deleteById(UUID id) {
		return pictures.deleteOwner(id);
	}

	/**
	 * Delete the inventory of a building that is about to be deleted, so that the files of the
	 * photos of its rooms go too.
	 */
	public void deleteByProperty(UUID propertyId) {
		findAll(propertyId).forEach((room) -> pictures.deleteOwner(room.id()));
		jdbc.sql("DELETE FROM inventory_item WHERE property_id = ?").param(propertyId.toString()).update();
	}

	/**
	 * The items of the building that are in no room, in their order.
	 */
	public List<InventoryItem> findOtherItems(UUID propertyId) {
		return findBuildingItems(propertyId, true);
	}

	public List<InventoryItem> replaceOtherItems(UUID propertyId, List<InventoryItem> items) {
		return replaceBuildingItems(propertyId, true, items);
	}

	/**
	 * The items missing from the building, in their order.
	 */
	public List<InventoryItem> findMissingItems(UUID propertyId) {
		return findBuildingItems(propertyId, false);
	}

	public List<InventoryItem> replaceMissingItems(UUID propertyId, List<InventoryItem> items) {
		return replaceBuildingItems(propertyId, false, items);
	}

	// The two lists of a building are told apart by "existent": 1 for the other items, 0 for the missing ones

	private List<InventoryItem> findBuildingItems(UUID propertyId, boolean existent) {
		return jdbc.sql("""
				SELECT description, quantity, value_cents FROM inventory_item
				WHERE property_id = ? AND existent = ? ORDER BY position, id
				""").params(propertyId.toString(), existent).query((rs, rowNum) -> mapItem(rs)).list();
	}

	private List<InventoryItem> replaceBuildingItems(UUID propertyId, boolean existent, List<InventoryItem> items) {
		jdbc.sql("DELETE FROM inventory_item WHERE property_id = ? AND existent = ?")
			.params(propertyId.toString(), existent)
			.update();
		insertItems(null, propertyId, existent, items);
		return items;
	}

	public Optional<PictureInfo> addPicture(UUID roomId, Picture picture) {
		return pictures.add(roomId, picture);
	}

	public List<PictureInfo> listPictures(UUID roomId) {
		return pictures.list(roomId);
	}

	public Map<UUID, List<PictureInfo>> listAllPictures() {
		return pictures.listAll();
	}

	public Optional<Picture> findPicture(UUID roomId, UUID pictureId) {
		return pictures.find(roomId, pictureId);
	}

	public Optional<byte[]> findThumbnail(UUID roomId, UUID pictureId) {
		return pictures.findThumbnail(roomId, pictureId);
	}

	public boolean deletePicture(UUID roomId, UUID pictureId) {
		return pictures.delete(roomId, pictureId);
	}

	public void reorderPictures(UUID roomId, List<UUID> pictureIds) {
		pictures.reorder(roomId, pictureIds);
	}

	private void insertItems(@Nullable UUID roomId, @Nullable UUID propertyId, boolean existent,
			List<InventoryItem> items) {
		for (int position = 0; position < items.size(); position++) {
			InventoryItem item = items.get(position);
			jdbc.sql("""
					INSERT INTO inventory_item (room_id, property_id, position, description, quantity, value_cents,
						existent)
					VALUES (:roomId, :propertyId, :position, :description, :quantity, :valueCents, :existent)
					""")
				.param("roomId", (roomId != null) ? roomId.toString() : null)
				.param("propertyId", (propertyId != null) ? propertyId.toString() : null)
				.param("position", position)
				.param("description", item.description())
				.param("quantity", item.quantity())
				.param("valueCents", item.valueEur().movePointRight(2).longValueExact())
				.param("existent", existent)
				.update();
		}
	}

	private static InventoryRoom mapRoom(ResultSet rs, List<InventoryItem> items) throws SQLException {
		return new InventoryRoom(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("property_id")),
				rs.getString("title"), rs.getString("description"), rs.getString("pdf_title"), rs.getString("comments"),
				items);
	}

	private static InventoryItem mapItem(ResultSet rs) throws SQLException {
		return new InventoryItem(rs.getString("description"), rs.getInt("quantity"),
				BigDecimal.valueOf(rs.getLong("value_cents"), 2));
	}

}
