package com.mephys.attic.property;

import com.mephys.attic.picture.PictureResponse;
import com.mephys.attic.picture.PictureUploads;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * The official documents of the house and the land (title deeds, plans, ...). Their files are
 * images or PDF.
 */
@RestController
class PropertyDocumentController {

	private final PropertyRepository repository;

	private final PictureUploads uploads;

	PropertyDocumentController(PropertyRepository repository, PictureUploads uploads) {
		this.repository = repository;
		this.uploads = uploads;
	}

	@GetMapping("/property-document-types")
	List<PropertyDocumentType> types() {
		return List.of(PropertyDocumentType.values());
	}

	@PostMapping("/properties/{propertyId}/documents")
	ResponseEntity<PropertyDocumentResponse> create(@PathVariable UUID propertyId,
			@RequestBody PropertyDocumentRequest request) {
		if (repository.findById(propertyId).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		PropertyDocument document = repository.saveDocument(request.toDocument(null, propertyId));
		return ResponseEntity.created(URI.create("/api/property-documents/" + document.id()))
			.body(PropertyDocumentResponse.of(document, List.of()));
	}

	@GetMapping("/property-documents/{id}")
	ResponseEntity<PropertyDocumentResponse> get(@PathVariable UUID id) {
		return ResponseEntity.of(repository.findDocument(id).map(this::toResponse));
	}

	@PutMapping("/property-documents/{id}")
	ResponseEntity<PropertyDocumentResponse> replace(@PathVariable UUID id,
			@RequestBody PropertyDocumentRequest request) {
		PropertyDocument existing = repository.findDocument(id).orElse(null);
		if (existing == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(toResponse(repository.saveDocument(request.toDocument(id, existing.propertyId()))));
	}

	/**
	 * Delete the document with all its files.
	 */
	@DeleteMapping("/property-documents/{id}")
	@Transactional
	ResponseEntity<Void> delete(@PathVariable UUID id) {
		return repository.deleteDocument(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
	}

	@PostMapping(path = "/property-documents/{id}/pictures", consumes = { "image/*", "application/pdf" })
	ResponseEntity<PictureResponse> addFile(@PathVariable UUID id,
			@RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType, @RequestBody byte[] data) {
		String base = PropertyDocumentResponse.base(id);
		return repository.addDocumentFile(id, uploads.readImageOrPdf(contentType, data))
			.map((info) -> ResponseEntity.created(URI.create(info.url(base))).body(PictureResponse.of(base, info)))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/property-documents/{id}/pictures/{fileId}")
	ResponseEntity<byte[]> getFile(@PathVariable UUID id, @PathVariable UUID fileId) {
		return PictureUploads.pictureResponse(repository.findDocumentFile(id, fileId));
	}

	@GetMapping("/property-documents/{id}/pictures/{fileId}/thumbnail")
	ResponseEntity<byte[]> getThumbnail(@PathVariable UUID id, @PathVariable UUID fileId) {
		return PictureUploads.thumbnailResponse(repository.findDocumentFileThumbnail(id, fileId));
	}

	@DeleteMapping("/property-documents/{id}/pictures/{fileId}")
	@Transactional
	ResponseEntity<Void> deleteFile(@PathVariable UUID id, @PathVariable UUID fileId) {
		return repository.deleteDocumentFile(id, fileId) ? ResponseEntity.noContent().build()
				: ResponseEntity.notFound().build();
	}

	/**
	 * Put the document's files in this order.
	 */
	@PutMapping("/property-documents/{id}/pictures/order")
	@Transactional
	ResponseEntity<Void> reorderFiles(@PathVariable UUID id, @RequestBody List<UUID> fileIds) {
		if (repository.findDocument(id).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		repository.reorderDocumentFiles(id, fileIds);
		return ResponseEntity.noContent().build();
	}

	private PropertyDocumentResponse toResponse(PropertyDocument document) {
		return PropertyDocumentResponse.of(document, repository.listDocumentFiles(document.id()));
	}

}
