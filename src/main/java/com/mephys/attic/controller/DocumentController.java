package com.mephys.attic.controller;

import com.mephys.attic.dto.DocumentRequest;
import com.mephys.attic.dto.DocumentResponse;
import com.mephys.attic.dto.PictureResponse;
import com.mephys.attic.model.DocumentType;
import com.mephys.attic.model.HeirDocument;
import com.mephys.attic.model.PictureInfo;
import com.mephys.attic.repository.DocumentRepository;
import com.mephys.attic.service.CurrentAccount;
import com.mephys.attic.service.PictureUploads;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
class DocumentController {

	private final DocumentRepository repository;

	private final PictureUploads uploads;

	private final CurrentAccount account;

	DocumentController(DocumentRepository repository, PictureUploads uploads, CurrentAccount account) {
		this.repository = repository;
		this.uploads = uploads;
		this.account = account;
	}

	@GetMapping("/document-types")
	List<DocumentType> types() {
		return List.of(DocumentType.values());
	}

	@GetMapping("/documents")
	List<DocumentResponse> list() {
		Map<UUID, List<PictureInfo>> pictures = repository.listAllPictures();
		return repository.findAll()
			.stream()
			.filter((named) -> account.maySee(named.document().heirId()))
			.map((named) -> DocumentResponse.of(named, pictures.getOrDefault(named.document().id(), List.of())))
			.toList();
	}

	@GetMapping("/documents/{id}")
	ResponseEntity<DocumentResponse> get(@PathVariable UUID id) {
		return ResponseEntity.of(repository.findById(id).filter(this::maySee).map(this::toResponse));
	}

	@PostMapping("/documents")
	ResponseEntity<DocumentResponse> create(@RequestBody DocumentRequest request) {
		HeirDocument document = repository.save(validated(request.toDocument(null)));
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(document.id());
		return ResponseEntity.created(location).body(toResponse(document.id()));
	}

	@PutMapping("/documents/{id}")
	ResponseEntity<DocumentResponse> replace(@PathVariable UUID id, @RequestBody DocumentRequest request) {
		if (repository.findById(id).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		repository.save(validated(request.toDocument(id)));
		return ResponseEntity.ok(toResponse(id));
	}

	@DeleteMapping("/documents/{id}")
	ResponseEntity<Void> delete(@PathVariable UUID id) {
		return repository.deleteById(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
	}

	/** A photo, or a PDF such as a scanned contract */
	@PostMapping(path = "/documents/{id}/pictures", consumes = { "image/*", MediaType.APPLICATION_PDF_VALUE })
	ResponseEntity<PictureResponse> addPicture(@PathVariable UUID id,
			@RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType, @RequestBody byte[] data) {
		String base = DocumentResponse.base(id);
		return repository.addPicture(id, uploads.readDocumentFile(contentType, data))
			.map((info) -> ResponseEntity.created(URI.create(info.url(base))).body(PictureResponse.of(base, info)))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/documents/{id}/pictures/{pictureId}")
	ResponseEntity<byte[]> getPicture(@PathVariable UUID id, @PathVariable UUID pictureId) {
		Optional<DocumentRepository.NamedDocument> document = repository.findById(id).filter(this::maySee);
		return PictureUploads.pictureResponse(document.flatMap((named) -> repository.findPicture(id, pictureId)),
				document.map(this::fileName).orElse("document"));
	}

	@GetMapping("/documents/{id}/pictures/{pictureId}/thumbnail")
	ResponseEntity<byte[]> getThumbnail(@PathVariable UUID id, @PathVariable UUID pictureId) {
		return PictureUploads.thumbnailResponse(maySee(id) ? repository.findThumbnail(id, pictureId) : Optional.empty());
	}

	@DeleteMapping("/documents/{id}/pictures/{pictureId}")
	@Transactional
	ResponseEntity<Void> deletePicture(@PathVariable UUID id, @PathVariable UUID pictureId) {
		return repository.deletePicture(id, pictureId) ? ResponseEntity.noContent().build()
				: ResponseEntity.notFound().build();
	}

	/**
	 * Put the document's photos in this order; the first becomes the cover.
	 */
	@PutMapping("/documents/{id}/pictures/order")
	@Transactional
	ResponseEntity<Void> reorderPictures(@PathVariable UUID id, @RequestBody List<UUID> pictureIds) {
		if (repository.findById(id).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		repository.reorderPictures(id, pictureIds);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Users see only the documents of the heir linked to their account; documents without heir
	 * are for administrators.
	 */
	private boolean maySee(DocumentRepository.NamedDocument named) {
		return account.maySee(named.document().heirId());
	}

	/** e.g. "Ana Costa - PASSPORT", the name a downloaded file gets */
	private String fileName(DocumentRepository.NamedDocument named) {
		String type = named.document().type().toString();
		return (named.heirName() != null) ? named.heirName() + " - " + type : type;
	}

	private boolean maySee(UUID documentId) {
		return repository.findById(documentId).filter(this::maySee).isPresent();
	}

	private HeirDocument validated(HeirDocument document) {
		if (document.heirId() != null && !repository.heirExists(document.heirId())) {
			throw new IllegalArgumentException("heir does not exist");
		}
		return document;
	}

	private DocumentResponse toResponse(UUID id) {
		return toResponse(repository.findById(id).orElseThrow());
	}

	private DocumentResponse toResponse(DocumentRepository.NamedDocument named) {
		return DocumentResponse.of(named, repository.listPictures(named.document().id()));
	}

}
