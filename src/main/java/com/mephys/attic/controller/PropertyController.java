package com.mephys.attic.controller;

import com.mephys.attic.dto.EstateResponse;
import com.mephys.attic.dto.PictureResponse;
import com.mephys.attic.dto.PropertyDocumentResponse;
import com.mephys.attic.dto.PropertyRequest;
import com.mephys.attic.dto.PropertyResponse;
import com.mephys.attic.model.Estate;
import com.mephys.attic.model.PictureInfo;
import com.mephys.attic.model.Property;
import com.mephys.attic.model.PropertyKind;
import com.mephys.attic.repository.PropertyRepository;
import com.mephys.attic.service.PictureUploads;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * The estate: its buildings and land parcels, with their details and photos. Their official
 * documents are in {@link PropertyDocumentController}.
 */
@RestController
class PropertyController {

	private final PropertyRepository repository;

	private final PictureUploads uploads;

	PropertyController(PropertyRepository repository, PictureUploads uploads) {
		this.repository = repository;
		this.uploads = uploads;
	}

	/** The buildings and the land parcels of the estate, each by name */
	@GetMapping("/estate")
	EstateResponse estate() {
		Map<UUID, List<PictureInfo>> pictures = repository.listAllPictures();
		Map<UUID, List<PictureInfo>> files = repository.listAllDocumentFiles();
		Estate estate = Estate.of(repository.findAll(null));
		Function<Property, PropertyResponse> response = (property) -> PropertyResponse.of(property,
				pictures.getOrDefault(property.id(), List.of()), documents(property.id(), files));
		return new EstateResponse(estate.buildings().stream().map(response).toList(),
				estate.lands().stream().map(response).toList());
	}

	/** All properties by name, or those of one kind */
	@GetMapping("/properties")
	List<PropertyResponse> list(@RequestParam(required = false) @Nullable PropertyKind kind) {
		Map<UUID, List<PictureInfo>> pictures = repository.listAllPictures();
		Map<UUID, List<PictureInfo>> files = repository.listAllDocumentFiles();
		return repository.findAll(kind)
			.stream()
			.map((property) -> PropertyResponse.of(property, pictures.getOrDefault(property.id(), List.of()),
					documents(property.id(), files)))
			.toList();
	}

	@GetMapping("/properties/{id}")
	ResponseEntity<PropertyResponse> get(@PathVariable UUID id) {
		return ResponseEntity.of(repository.findById(id).map(this::toResponse));
	}

	@PostMapping("/properties")
	@Transactional
	ResponseEntity<PropertyResponse> create(@RequestBody PropertyRequest request) {
		Property saved = repository.save(request.toProperty(null));
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(saved.id());
		return ResponseEntity.created(location).body(toResponse(saved));
	}

	@PutMapping("/properties/{id}")
	@Transactional
	ResponseEntity<PropertyResponse> replace(@PathVariable UUID id, @RequestBody PropertyRequest request) {
		Property existing = repository.findById(id).orElse(null);
		if (existing == null) {
			return ResponseEntity.notFound().build();
		}
		Property property = request.toProperty(id);
		if (property.kind() != existing.kind()) {
			throw new IllegalArgumentException("kind cannot change");
		}
		return ResponseEntity.ok(toResponse(repository.save(property)));
	}

	/**
	 * Delete the property with its details, photos, official documents and all their files.
	 */
	@DeleteMapping("/properties/{id}")
	@Transactional
	ResponseEntity<Void> delete(@PathVariable UUID id) {
		return repository.deleteById(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
	}

	@GetMapping("/property-labels")
	List<String> labels() {
		return repository.labels();
	}

	@PostMapping(path = "/properties/{id}/pictures", consumes = "image/*")
	ResponseEntity<PictureResponse> addPicture(@PathVariable UUID id,
			@RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType, @RequestBody byte[] data) {
		String base = PropertyResponse.base(id);
		return repository.addPicture(id, uploads.read(contentType, data))
			.map((info) -> ResponseEntity.created(URI.create(info.url(base))).body(PictureResponse.of(base, info)))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/properties/{id}/pictures/{pictureId}")
	ResponseEntity<byte[]> getPicture(@PathVariable UUID id, @PathVariable UUID pictureId) {
		return PictureUploads.pictureResponse(repository.findPicture(id, pictureId), "picture");
	}

	@GetMapping("/properties/{id}/pictures/{pictureId}/thumbnail")
	ResponseEntity<byte[]> getThumbnail(@PathVariable UUID id, @PathVariable UUID pictureId) {
		return PictureUploads.thumbnailResponse(repository.findThumbnail(id, pictureId));
	}

	@DeleteMapping("/properties/{id}/pictures/{pictureId}")
	@Transactional
	ResponseEntity<Void> deletePicture(@PathVariable UUID id, @PathVariable UUID pictureId) {
		return repository.deletePicture(id, pictureId) ? ResponseEntity.noContent().build()
				: ResponseEntity.notFound().build();
	}

	/**
	 * Put the property's photos in this order; the first becomes the cover.
	 */
	@PutMapping("/properties/{id}/pictures/order")
	@Transactional
	ResponseEntity<Void> reorderPictures(@PathVariable UUID id, @RequestBody List<UUID> pictureIds) {
		if (repository.findById(id).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		repository.reorderPictures(id, pictureIds);
		return ResponseEntity.noContent().build();
	}

	private PropertyResponse toResponse(Property property) {
		return PropertyResponse.of(property, repository.listPictures(property.id()),
				documents(property.id(), repository.listAllDocumentFiles()));
	}

	private List<PropertyDocumentResponse> documents(UUID propertyId, Map<UUID, List<PictureInfo>> files) {
		return repository.findDocuments(propertyId)
			.stream()
			.map((document) -> PropertyDocumentResponse.of(document, files.getOrDefault(document.id(), List.of())))
			.toList();
	}

}
