package com.mephys.attic.document;

import com.mephys.attic.picture.Picture;
import com.mephys.attic.picture.PictureInfo;
import com.mephys.attic.picture.PictureRepository;
import com.mephys.attic.picture.PictureStorage;

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
class DocumentRepository {

	private static final String SELECT_NAMED = """
			SELECT d.*, p.name AS heir_name FROM heir_document d JOIN heir p ON p.id = d.heir_id""";

	private final JdbcClient jdbc;

	private final PictureRepository pictures;

	DocumentRepository(JdbcClient jdbc, PictureStorage storage) {
		this.jdbc = jdbc;
		this.pictures = new PictureRepository(jdbc, storage, "document_picture", "document_id", "heir_document");
	}

	HeirDocument save(HeirDocument document) {
		jdbc.sql("""
				INSERT INTO heir_document (id, heir_id, type, valid_until, comments, created_at, updated_at)
				VALUES (:id, :heirId, :type, :validUntil, :comments, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
				ON CONFLICT (id) DO UPDATE SET
					heir_id = excluded.heir_id, type = excluded.type, valid_until = excluded.valid_until,
					comments = excluded.comments, updated_at = excluded.updated_at
				""")
			.param("id", document.id().toString())
			.param("heirId", document.heirId().toString())
			.param("type", document.type().name())
			.param("validUntil", (document.validUntil() != null) ? document.validUntil().toString() : null)
			.param("comments", document.comments())
			.update();
		return document;
	}

	Optional<NamedDocument> findById(UUID id) {
		return jdbc.sql(SELECT_NAMED + " WHERE d.id = ?").param(id.toString()).query(this::mapNamed).optional();
	}

	/**
	 * All documents, grouped by heir name and then sorted by type.
	 */
	List<NamedDocument> findAll() {
		return jdbc.sql(SELECT_NAMED + " ORDER BY p.name COLLATE NOCASE, p.name, p.id, d.type")
			.query(this::mapNamed)
			.list();
	}

	boolean heirExists(UUID heirId) {
		return jdbc.sql("SELECT count(*) FROM heir WHERE id = ?").param(heirId.toString()).query(Integer.class)
			.single() > 0;
	}

	boolean deleteById(UUID id) {
		return pictures.deleteOwner(id);
	}

	/**
	 * Delete all documents of an heir, with their picture files.
	 * @return the number of deleted documents
	 */
	int deleteAllOfHeir(UUID heirId) {
		List<UUID> ids = jdbc.sql("SELECT id FROM heir_document WHERE heir_id = ?")
			.param(heirId.toString())
			.query((rs, rowNum) -> UUID.fromString(rs.getString("id")))
			.list();
		ids.forEach(pictures::deleteOwner);
		return ids.size();
	}

	boolean savePicture(UUID documentId, Picture picture) {
		return pictures.save(documentId, picture);
	}

	Optional<Picture> findPicture(UUID documentId) {
		return pictures.find(documentId);
	}

	Optional<byte[]> findThumbnail(UUID documentId) {
		return pictures.findThumbnail(documentId);
	}

	boolean deletePicture(UUID documentId) {
		return pictures.delete(documentId);
	}

	Optional<PictureInfo> findPictureInfo(UUID documentId) {
		return pictures.findInfo(documentId);
	}

	Map<UUID, PictureInfo> findAllPictureInfo() {
		return pictures.findAllInfo();
	}

	private NamedDocument mapNamed(ResultSet rs, int rowNum) throws SQLException {
		String validUntil = rs.getString("valid_until");
		HeirDocument document = new HeirDocument(UUID.fromString(rs.getString("id")),
				UUID.fromString(rs.getString("heir_id")), DocumentType.valueOf(rs.getString("type")),
				(validUntil != null) ? LocalDate.parse(validUntil) : null, rs.getString("comments"));
		return new NamedDocument(document, rs.getString("heir_name"));
	}

	/**
	 * A document together with the name of its heir.
	 */
	record NamedDocument(HeirDocument document, String heirName) {
	}

}
