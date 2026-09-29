package com.mephys.attic.inventory;

import com.mephys.attic.picture.PictureInfo;
import com.mephys.attic.picture.PictureResponse;
import com.mephys.attic.picture.PictureUploads;

import java.net.URI;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/items")
class InventoryController {

	private final InventoryRepository repository;

	private final PictureUploads uploads;

	InventoryController(InventoryRepository repository, PictureUploads uploads) {
		this.repository = repository;
		this.uploads = uploads;
	}

	@GetMapping
	List<InventoryItemResponse> list() {
		Map<UUID, List<PictureInfo>> pictures = repository.listAllPictures();
		return repository.findAll()
			.stream()
			.map((item) -> InventoryItemResponse.of(item, pictures.getOrDefault(item.id(), List.of())))
			.toList();
	}

	@GetMapping("/{id}")
	ResponseEntity<InventoryItemResponse> get(@PathVariable UUID id) {
		return ResponseEntity.of(repository.findById(id).map(this::toResponse));
	}

	@PostMapping
	ResponseEntity<InventoryItemResponse> create(@RequestBody InventoryItemRequest request) {
		InventoryItem item = repository.save(request.toItem(null));
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(item.id());
		return ResponseEntity.created(location).body(InventoryItemResponse.of(item, List.of()));
	}

	@PutMapping("/{id}")
	ResponseEntity<InventoryItemResponse> replace(@PathVariable UUID id, @RequestBody InventoryItemRequest request) {
		if (repository.findById(id).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(toResponse(repository.save(request.toItem(id))));
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable UUID id) {
		return repository.deleteById(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
	}

	@PostMapping(path = "/{id}/pictures", consumes = "image/*")
	ResponseEntity<PictureResponse> addPicture(@PathVariable UUID id,
			@RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType, @RequestBody byte[] data) {
		String base = InventoryItemResponse.base(id);
		return repository.addPicture(id, uploads.read(contentType, data))
			.map((info) -> ResponseEntity.created(URI.create(info.url(base))).body(PictureResponse.of(base, info)))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/{id}/pictures/{pictureId}")
	ResponseEntity<byte[]> getPicture(@PathVariable UUID id, @PathVariable UUID pictureId) {
		return PictureUploads.pictureResponse(repository.findPicture(id, pictureId));
	}

	@GetMapping("/{id}/pictures/{pictureId}/thumbnail")
	ResponseEntity<byte[]> getThumbnail(@PathVariable UUID id, @PathVariable UUID pictureId) {
		return PictureUploads.thumbnailResponse(repository.findThumbnail(id, pictureId));
	}

	@DeleteMapping("/{id}/pictures/{pictureId}")
	@Transactional
	ResponseEntity<Void> deletePicture(@PathVariable UUID id, @PathVariable UUID pictureId) {
		return repository.deletePicture(id, pictureId) ? ResponseEntity.noContent().build()
				: ResponseEntity.notFound().build();
	}

	/**
	 * Put the item's photos in this order; the first becomes the cover.
	 */
	@PutMapping("/{id}/pictures/order")
	@Transactional
	ResponseEntity<Void> reorderPictures(@PathVariable UUID id, @RequestBody List<UUID> pictureIds) {
		if (repository.findById(id).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		repository.reorderPictures(id, pictureIds);
		return ResponseEntity.noContent().build();
	}

	private InventoryItemResponse toResponse(InventoryItem item) {
		return InventoryItemResponse.of(item, repository.listPictures(item.id()));
	}

}
