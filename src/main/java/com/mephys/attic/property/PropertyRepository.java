package com.mephys.attic.property;

import com.mephys.attic.model.Picture;
import com.mephys.attic.model.PictureInfo;
import com.mephys.attic.repository.PictureRepository;
import com.mephys.attic.service.PictureStorage;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Collator;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class PropertyRepository {

	/** Sorts "Área" next to "Arca" rather than after "Zona" */
	private static final Collator COLLATOR = Collator.getInstance(Locale.ROOT);

	private final JdbcClient jdbc;

	private final PictureRepository pictures;

	private final PictureRepository documentFiles;

	PropertyRepository(JdbcClient jdbc, PictureStorage storage) {
		this.jdbc = jdbc;
		this.pictures = new PictureRepository(jdbc, storage, "property_picture", "property_id", "property");
		this.documentFiles = new PictureRepository(jdbc, storage, "property_document_file", "document_id",
				"property_document");
	}

	/**
	 * Insert or update the property and replace its facts. The kind of an existing property is
	 * never changed.
	 */
	Property save(Property property) {
		jdbc.sql("""
				INSERT INTO property (id, kind, name, address, value_cents, comments, created_at, updated_at)
				VALUES (:id, :kind, :name, :address, :valueCents, :comments,
					strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
				ON CONFLICT (id) DO UPDATE SET
					name = excluded.name, address = excluded.address, value_cents = excluded.value_cents,
					comments = excluded.comments, updated_at = excluded.updated_at
				""")
			.param("id", property.id().toString())
			.param("kind", property.kind().name())
			.param("name", property.name())
			.param("address", property.address())
			.param("valueCents",
					(property.valueEur() != null) ? property.valueEur().movePointRight(2).longValueExact() : null)
			.param("comments", property.comments())
			.update();
		jdbc.sql("DELETE FROM property_fact WHERE property_id = ?").param(property.id().toString()).update();
		List<PropertyFact> facts = property.facts();
		for (int position = 0; position < facts.size(); position++) {
			jdbc.sql("INSERT INTO property_fact (property_id, position, label, value) VALUES (?, ?, ?, ?)")
				.params(property.id().toString(), position, facts.get(position).label(), facts.get(position).value())
				.update();
		}
		return findById(property.id()).orElseThrow();
	}

	Optional<Property> findById(UUID id) {
		List<PropertyFact> facts = factsOf(id);
		return jdbc.sql("SELECT * FROM property WHERE id = ?")
			.param(id.toString())
			.query((rs, rowNum) -> map(rs, facts))
			.optional();
	}

	/**
	 * All properties by name, or those of one kind.
	 */
	List<Property> findAll(@Nullable PropertyKind kind) {
		Map<UUID, List<PropertyFact>> facts = jdbc
			.sql("SELECT property_id, label, value FROM property_fact ORDER BY property_id, position")
			.query((rs, rowNum) -> Map.entry(UUID.fromString(rs.getString("property_id")),
					new PropertyFact(rs.getString("label"), rs.getString("value"))))
			.list()
			.stream()
			.collect(Collectors.groupingBy(Map.Entry::getKey,
					Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
		JdbcClient.StatementSpec properties = (kind != null)
				? jdbc.sql("SELECT * FROM property WHERE kind = ?").param(kind.name())
				: jdbc.sql("SELECT * FROM property");
		return properties
			.query((rs, rowNum) -> map(rs, facts.getOrDefault(UUID.fromString(rs.getString("id")), List.of())))
			.list()
			.stream()
			.sorted(Comparator.comparing(Property::name, COLLATOR))
			.toList();
	}

	/**
	 * Labels used on any property, each once, sorted.
	 */
	List<String> labels() {
		return jdbc.sql("SELECT DISTINCT label FROM property_fact WHERE label <> ''")
			.query(String.class)
			.list()
			.stream()
			.sorted(COLLATOR)
			.toList();
	}

	/**
	 * Delete the property with its facts, photos, documents and all their files.
	 * @return {@code false} if the property did not exist
	 */
	boolean deleteById(UUID id) {
		findDocuments(id).forEach((document) -> documentFiles.deleteOwner(document.id()));
		return pictures.deleteOwner(id);
	}

	// Photos

	Optional<PictureInfo> addPicture(UUID propertyId, Picture picture) {
		return pictures.add(propertyId, picture);
	}

	List<PictureInfo> listPictures(UUID propertyId) {
		return pictures.list(propertyId);
	}

	Map<UUID, List<PictureInfo>> listAllPictures() {
		return pictures.listAll();
	}

	Optional<Picture> findPicture(UUID propertyId, UUID pictureId) {
		return pictures.find(propertyId, pictureId);
	}

	Optional<byte[]> findThumbnail(UUID propertyId, UUID pictureId) {
		return pictures.findThumbnail(propertyId, pictureId);
	}

	boolean deletePicture(UUID propertyId, UUID pictureId) {
		return pictures.delete(propertyId, pictureId);
	}

	void reorderPictures(UUID propertyId, List<UUID> pictureIds) {
		pictures.reorder(propertyId, pictureIds);
	}

	// Official documents

	PropertyDocument saveDocument(PropertyDocument document) {
		jdbc.sql("""
				INSERT INTO property_document (id, property_id, type, date, notes, created_at, updated_at)
				VALUES (:id, :propertyId, :type, :date, :notes,
					strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
				ON CONFLICT (id) DO UPDATE SET
					type = excluded.type, date = excluded.date, notes = excluded.notes, updated_at = excluded.updated_at
				""")
			.param("id", document.id().toString())
			.param("propertyId", document.propertyId().toString())
			.param("type", document.type().name())
			.param("date", (document.date() != null) ? document.date().toString() : null)
			.param("notes", document.notes())
			.update();
		return document;
	}

	Optional<PropertyDocument> findDocument(UUID id) {
		return jdbc.sql("SELECT * FROM property_document WHERE id = ?")
			.param(id.toString())
			.query(this::mapDocument)
			.optional();
	}

	/**
	 * The documents of a property, newest first; documents without date last.
	 */
	List<PropertyDocument> findDocuments(UUID propertyId) {
		return jdbc.sql("""
				SELECT * FROM property_document WHERE property_id = ?
				ORDER BY date IS NULL, date DESC, type, created_at
				""").param(propertyId.toString()).query(this::mapDocument).list();
	}

	boolean deleteDocument(UUID id) {
		return documentFiles.deleteOwner(id);
	}

	Optional<PictureInfo> addDocumentFile(UUID documentId, Picture file) {
		return documentFiles.add(documentId, file);
	}

	List<PictureInfo> listDocumentFiles(UUID documentId) {
		return documentFiles.list(documentId);
	}

	Map<UUID, List<PictureInfo>> listAllDocumentFiles() {
		return documentFiles.listAll();
	}

	Optional<Picture> findDocumentFile(UUID documentId, UUID fileId) {
		return documentFiles.find(documentId, fileId);
	}

	Optional<byte[]> findDocumentFileThumbnail(UUID documentId, UUID fileId) {
		return documentFiles.findThumbnail(documentId, fileId);
	}

	boolean deleteDocumentFile(UUID documentId, UUID fileId) {
		return documentFiles.delete(documentId, fileId);
	}

	void reorderDocumentFiles(UUID documentId, List<UUID> fileIds) {
		documentFiles.reorder(documentId, fileIds);
	}

	private List<PropertyFact> factsOf(UUID propertyId) {
		return jdbc.sql("SELECT label, value FROM property_fact WHERE property_id = ? ORDER BY position")
			.param(propertyId.toString())
			.query((rs, rowNum) -> new PropertyFact(rs.getString("label"), rs.getString("value")))
			.list();
	}

	private static Property map(ResultSet rs, List<PropertyFact> facts) throws SQLException {
		long cents = rs.getLong("value_cents");
		@Nullable BigDecimal value = rs.wasNull() ? null : BigDecimal.valueOf(cents, 2);
		return Property.of(UUID.fromString(rs.getString("id")), PropertyKind.valueOf(rs.getString("kind")),
				rs.getString("name"), rs.getString("address"), value, rs.getString("comments"), facts);
	}

	private PropertyDocument mapDocument(ResultSet rs, int rowNum) throws SQLException {
		String date = rs.getString("date");
		return new PropertyDocument(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("property_id")),
				PropertyDocumentType.valueOf(rs.getString("type")), (date != null) ? LocalDate.parse(date) : null,
				rs.getString("notes"));
	}

}
