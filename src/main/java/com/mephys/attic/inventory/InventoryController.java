package com.mephys.attic.inventory;

import com.mephys.attic.picture.PictureInfo;
import com.mephys.attic.picture.PictureUploads;

import java.net.URI;
import java.util.List;
import java.util.Map;
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
		Map<UUID, PictureInfo> pictures = repository.findAllPictureInfo();
		return repository.findAll()
			.stream()
			.map((item) -> InventoryItemResponse.of(item, pictures.get(item.id())))
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
		return ResponseEntity.created(location).body(InventoryItemResponse.of(item, null));
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

	@GetMapping("/{id}/picture")
	ResponseEntity<byte[]> getPicture(@PathVariable UUID id) {
		return PictureUploads.pictureResponse(repository.findPicture(id));
	}

	@PutMapping(path = "/{id}/picture", consumes = "image/*")
	ResponseEntity<Void> putPicture(@PathVariable UUID id, @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType,
			@RequestBody byte[] data) {
		boolean saved = repository.savePicture(id, uploads.read(contentType, data));
		return saved ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
	}

	@GetMapping("/{id}/thumbnail")
	ResponseEntity<byte[]> getThumbnail(@PathVariable UUID id) {
		return PictureUploads.thumbnailResponse(repository.findThumbnail(id));
	}

	@DeleteMapping("/{id}/picture")
	ResponseEntity<Void> deletePicture(@PathVariable UUID id) {
		return repository.deletePicture(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
	}

	private InventoryItemResponse toResponse(InventoryItem item) {
		return InventoryItemResponse.of(item, repository.findPictureInfo(item.id()).orElse(null));
	}

}
