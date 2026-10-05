package com.mephys.attic.controller;

import com.mephys.attic.dto.DocumentRequest;
import com.mephys.attic.dto.DocumentResponse;
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
import org.springframework.http.ResponseEntity;
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
		Map<UUID, PictureInfo> pictures = repository.findAllPictureInfo();
		return repository.findAll()
			.stream()
			.filter((named) -> account.maySee(named.document().heirId()))
			.map((named) -> DocumentResponse.of(named, pictures.get(named.document().id())))
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

	@GetMapping("/documents/{id}/picture")
	ResponseEntity<byte[]> getPicture(@PathVariable UUID id) {
		return PictureUploads.pictureResponse(maySee(id) ? repository.findPicture(id) : Optional.empty());
	}

	@PutMapping(path = "/documents/{id}/picture", consumes = "image/*")
	ResponseEntity<Void> putPicture(@PathVariable UUID id, @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType,
			@RequestBody byte[] data) {
		boolean saved = repository.savePicture(id, uploads.read(contentType, data));
		return saved ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
	}

	@GetMapping("/documents/{id}/thumbnail")
	ResponseEntity<byte[]> getThumbnail(@PathVariable UUID id) {
		return PictureUploads.thumbnailResponse(maySee(id) ? repository.findThumbnail(id) : Optional.empty());
	}

	@DeleteMapping("/documents/{id}/picture")
	ResponseEntity<Void> deletePicture(@PathVariable UUID id) {
		return repository.deletePicture(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
	}

	/** Users see only the documents of the heir linked to their account */
	private boolean maySee(DocumentRepository.NamedDocument named) {
		return account.maySee(named.document().heirId());
	}

	private boolean maySee(UUID documentId) {
		return repository.findById(documentId).filter(this::maySee).isPresent();
	}

	private HeirDocument validated(HeirDocument document) {
		if (!repository.heirExists(document.heirId())) {
			throw new IllegalArgumentException("heir does not exist");
		}
		return document;
	}

	private DocumentResponse toResponse(UUID id) {
		return toResponse(repository.findById(id).orElseThrow());
	}

	private DocumentResponse toResponse(DocumentRepository.NamedDocument named) {
		return DocumentResponse.of(named, repository.findPictureInfo(named.document().id()).orElse(null));
	}

}
