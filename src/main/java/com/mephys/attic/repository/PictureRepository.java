package com.mephys.attic.repository;

import com.mephys.attic.model.Picture;
import com.mephys.attic.model.PictureInfo;
import com.mephys.attic.service.PictureStorage;
import com.mephys.attic.service.Thumbnails;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Stores one picture per owner row (an inventory item, a document, ...). The picture file
 * lives in {@link PictureStorage}; the picture table holds the file name and a thumbnail.
 * <p>
 * The picture table must have the columns {@code id, <owner column>, file_name, content_type,
 * thumbnail}, with the owner column unique and referencing the owner table
 * {@code ON DELETE CASCADE}.
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
	 * Store the picture as a new file, replacing any existing one.
	 * @return {@code false} if the owner does not exist
	 */
	public boolean save(UUID ownerId, Picture picture) {
		Optional<String> previousFile = findFile(ownerId);
		UUID pictureId = UUID.randomUUID();
		byte[] thumbnail = Thumbnails.create(picture.data()).orElse(null);
		String fileName = storage.store(pictureId, picture);
		int saved;
		try {
			saved = jdbc.sql("""
					INSERT INTO %1$s (id, %2$s, file_name, content_type, thumbnail)
					SELECT :id, id, :fileName, :contentType, :thumbnail FROM %3$s WHERE id = :ownerId
					ON CONFLICT (%2$s) DO UPDATE SET
						id = excluded.id, file_name = excluded.file_name, content_type = excluded.content_type,
						thumbnail = excluded.thumbnail
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
		if (saved == 0) {
			storage.delete(fileName);
			return false;
		}
		previousFile.ifPresent(storage::delete);
		return true;
	}

	public Optional<Picture> find(UUID ownerId) {
		return jdbc.sql("SELECT file_name, content_type FROM %s WHERE %s = ?".formatted(table, ownerColumn))
			.param(ownerId.toString())
			.query((rs, rowNum) -> {
				String contentType = rs.getString("content_type");
				return storage.read(rs.getString("file_name")).map((data) -> new Picture(contentType, data));
			})
			.optional()
			.flatMap((picture) -> picture);
	}

	/**
	 * Return the JPEG thumbnail of the owner's picture, if it has one.
	 */
	public Optional<byte[]> findThumbnail(UUID ownerId) {
		return jdbc
			.sql("SELECT thumbnail FROM %s WHERE %s = ? AND thumbnail IS NOT NULL".formatted(table, ownerColumn))
			.param(ownerId.toString())
			.query((rs, rowNum) -> rs.getBytes("thumbnail"))
			.optional();
	}

	public boolean delete(UUID ownerId) {
		Optional<String> file = findFile(ownerId);
		boolean deleted = jdbc.sql("DELETE FROM %s WHERE %s = ?".formatted(table, ownerColumn))
			.param(ownerId.toString())
			.update() > 0;
		file.ifPresent(storage::delete);
		return deleted;
	}

	/**
	 * Delete the owner row. Its picture row goes with it (cascade) and the file is removed.
	 * @return {@code false} if the owner did not exist
	 */
	public boolean deleteOwner(UUID ownerId) {
		Optional<String> file = findFile(ownerId);
		boolean deleted = jdbc.sql("DELETE FROM %s WHERE id = ?".formatted(ownerTable))
			.param(ownerId.toString())
			.update() > 0;
		file.ifPresent(storage::delete);
		return deleted;
	}

	public Optional<PictureInfo> findInfo(UUID ownerId) {
		return jdbc
			.sql("SELECT id, thumbnail IS NOT NULL AS has_thumbnail, content_type FROM %s WHERE %s = ?"
				.formatted(table, ownerColumn))
			.param(ownerId.toString())
			.query((rs, rowNum) -> mapInfo(rs))
			.optional();
	}

	/**
	 * Return picture information for every owner that has a picture.
	 */
	public Map<UUID, PictureInfo> findAllInfo() {
		return jdbc
			.sql("SELECT id, %1$s, thumbnail IS NOT NULL AS has_thumbnail, content_type FROM %2$s"
				.formatted(ownerColumn, table))
			.query((rs, rowNum) -> Map.entry(UUID.fromString(rs.getString(ownerColumn)), mapInfo(rs)))
			// list() rather than stream(): a JdbcClient stream keeps its connection until closed
			.list()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	private Optional<String> findFile(UUID ownerId) {
		return jdbc.sql("SELECT file_name FROM %s WHERE %s = ?".formatted(table, ownerColumn))
			.param(ownerId.toString())
			.query(String.class)
			.optional();
	}

	private static PictureInfo mapInfo(ResultSet rs) throws SQLException {
		return new PictureInfo(UUID.fromString(rs.getString("id")), rs.getBoolean("has_thumbnail"),
				rs.getString("content_type"));
	}

}
