package com.mephys.attic.picture;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Stores an ordered list of pictures per owner row (an inventory item, a document, ...). The
 * first picture (position 0) is the cover. Picture files live in {@link PictureStorage}; the
 * picture table holds the file name, the position and a thumbnail.
 * <p>
 * The picture table must have the columns {@code id, <owner column>, position, file_name,
 * content_type, thumbnail}, with the owner column referencing the owner table
 * {@code ON DELETE CASCADE}. Positions are kept at 0..n-1 without gaps.
 * <p>
 * Changes of several rows ({@link #delete}, {@link #reorder}) must run in a transaction; the
 * callers' controller methods are {@code @Transactional}.
 */
public class PictureRepository {

	private final JdbcClient jdbc;

	private final PictureStorage storage;

	// Table and column names are fixed by the caller's code, never user input
	private final String table;

	private final String ownerColumn;

	private final String ownerTable;

	public PictureRepository(JdbcClient jdbc, PictureStorage storage, String table, String ownerColumn,
			String ownerTable) {
		this.jdbc = jdbc;
		this.storage = storage;
		this.table = table;
		this.ownerColumn = ownerColumn;
		this.ownerTable = ownerTable;
	}

	/**
	 * Store the picture as a new file, after the owner's other pictures.
	 * @return the new picture, or empty if the owner does not exist
	 */
	public Optional<PictureInfo> add(UUID ownerId, Picture picture) {
		UUID pictureId = UUID.randomUUID();
		byte[] thumbnail = Thumbnails.create(picture.data()).orElse(null);
		String fileName = storage.store(pictureId, picture);
		int added;
		try {
			// Positions have no gaps, so the number of pictures is the next position
			added = jdbc.sql("""
					INSERT INTO %1$s (id, %2$s, position, file_name, content_type, thumbnail)
					SELECT :id, o.id, (SELECT count(*) FROM %1$s WHERE %2$s = o.id), :fileName, :contentType, :thumbnail
					FROM %3$s o WHERE o.id = :ownerId
					""".formatted(table, ownerColumn, ownerTable))
				.param("id", pictureId.toString())
				.param("ownerId", ownerId.toString())
				.param("fileName", fileName)
				.param("contentType", picture.contentType())
				.param("thumbnail", thumbnail)
				.update();
		}
		catch (RuntimeException ex) {
			storage.delete(fileName);
			throw ex;
		}
		if (added == 0) {
			storage.delete(fileName);
			return Optional.empty();
		}
		return Optional.of(new PictureInfo(pictureId, picture.contentType(), thumbnail != null));
	}

	/**
	 * The owner's pictures, cover first.
	 */
	public List<PictureInfo> list(UUID ownerId) {
		return jdbc.sql("""
				SELECT id, content_type, thumbnail IS NOT NULL AS has_thumbnail FROM %s WHERE %s = ? ORDER BY position
				""".formatted(table, ownerColumn))
			.param(ownerId.toString())
			.query((rs, rowNum) -> mapInfo(rs))
			.list();
	}

	/**
	 * The pictures of every owner that has any, each list cover first.
	 */
	public Map<UUID, List<PictureInfo>> listAll() {
		return jdbc.sql("""
				SELECT id, %1$s, content_type, thumbnail IS NOT NULL AS has_thumbnail FROM %2$s ORDER BY %1$s, position
				""".formatted(ownerColumn, table))
			.query((rs, rowNum) -> Map.entry(UUID.fromString(rs.getString(ownerColumn)), mapInfo(rs)))
			// list() rather than stream(): a JdbcClient stream keeps its connection until closed
			.list()
			.stream()
			.collect(Collectors.groupingBy(Map.Entry::getKey,
					Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
	}

	public Optional<Picture> find(UUID ownerId, UUID pictureId) {
		return jdbc.sql("SELECT file_name, content_type FROM %s WHERE id = ? AND %s = ?".formatted(table, ownerColumn))
			.params(pictureId.toString(), ownerId.toString())
			.query((rs, rowNum) -> {
				String contentType = rs.getString("content_type");
				return storage.read(rs.getString("file_name")).map((data) -> new Picture(contentType, data));
			})
			.optional()
			.flatMap((picture) -> picture);
	}

	/**
	 * Return the JPEG thumbnail of the picture, if it belongs to the owner and has one.
	 */
	public Optional<byte[]> findThumbnail(UUID ownerId, UUID pictureId) {
		return jdbc
			.sql("SELECT thumbnail FROM %s WHERE id = ? AND %s = ? AND thumbnail IS NOT NULL".formatted(table,
					ownerColumn))
			.params(pictureId.toString(), ownerId.toString())
			.query((rs, rowNum) -> rs.getBytes("thumbnail"))
			.optional();
	}

	/**
	 * Delete one picture of the owner and its file; the pictures after it move up.
	 * @return {@code false} if the owner has no such picture
	 */
	public boolean delete(UUID ownerId, UUID pictureId) {
		Optional<String> file = jdbc
			.sql("SELECT file_name FROM %s WHERE id = ? AND %s = ?".formatted(table, ownerColumn))
			.params(pictureId.toString(), ownerId.toString())
			.query(String.class)
			.optional();
		if (file.isEmpty()) {
			return false;
		}
		jdbc.sql("DELETE FROM %s WHERE id = ?".formatted(table)).param(pictureId.toString()).update();
		writeOrder(ownerId, list(ownerId).stream().map(PictureInfo::id).toList());
		storage.delete(file.get());
		return true;
	}

	/**
	 * Put the owner's pictures in the given order; the first becomes the cover.
	 * @throws IllegalArgumentException unless the ids are exactly the owner's pictures, each once
	 */
	public void reorder(UUID ownerId, List<UUID> pictureIds) {
		List<UUID> current = list(ownerId).stream().map(PictureInfo::id).toList();
		if (pictureIds.size() != current.size() || new HashSet<>(pictureIds).size() != pictureIds.size()
				|| !new HashSet<>(pictureIds).equals(new HashSet<>(current))) {
			throw new IllegalArgumentException("the order must list each picture exactly once");
		}
		writeOrder(ownerId, pictureIds);
	}

	/**
	 * Delete the owner row. Its picture rows go with it (cascade) and the files are removed.
	 * @return {@code false} if the owner did not exist
	 */
	public boolean deleteOwner(UUID ownerId) {
		List<String> files = jdbc.sql("SELECT file_name FROM %s WHERE %s = ?".formatted(table, ownerColumn))
			.param(ownerId.toString())
			.query(String.class)
			.list();
		boolean deleted = jdbc.sql("DELETE FROM %s WHERE id = ?".formatted(ownerTable))
			.param(ownerId.toString())
			.update() > 0;
		files.forEach(storage::delete);
		return deleted;
	}

	private void writeOrder(UUID ownerId, List<UUID> pictureIds) {
		for (int position = 0; position < pictureIds.size(); position++) {
			jdbc.sql("UPDATE %s SET position = ? WHERE id = ? AND %s = ?".formatted(table, ownerColumn))
				.params(position, pictureIds.get(position).toString(), ownerId.toString())
				.update();
		}
	}

	private static PictureInfo mapInfo(ResultSet rs) throws SQLException {
		return new PictureInfo(UUID.fromString(rs.getString("id")), rs.getString("content_type"),
				rs.getBoolean("has_thumbnail"));
	}

}
