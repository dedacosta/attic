package com.mephys.attic.controller;

import com.mephys.attic.dto.InventoryItemRequest;
import com.mephys.attic.dto.InventoryRoomName;
import com.mephys.attic.dto.InventoryRoomRequest;
import com.mephys.attic.dto.InventoryRoomResponse;
import com.mephys.attic.dto.PictureResponse;
import com.mephys.attic.model.InventoryItem;
import com.mephys.attic.model.InventoryRoom;
import com.mephys.attic.model.PictureInfo;
import com.mephys.attic.model.PropertyKind;
import com.mephys.attic.repository.InventoryRepository;
import com.mephys.attic.repository.PropertyRepository;
import com.mephys.attic.service.PictureUploads;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * The inventory, kept per building: its rooms with their items, its other items and its
 * missing items.
 */
@RestController
@RequestMapping("/inventory")
class InventoryController {

	private final InventoryRepository repository;

	private final PropertyRepository properties;

	private final PictureUploads uploads;

	InventoryController(InventoryRepository repository, PropertyRepository properties, PictureUploads uploads) {
		this.repository = repository;
		this.properties = properties;
		this.uploads = uploads;
	}

	/**
	 * The rooms of the building, or of every building if none is given.
	 */
	@GetMapping
	List<InventoryRoomResponse> list(@RequestParam @Nullable UUID propertyId) {
		Map<UUID, List<PictureInfo>> pictures = repository.listAllPictures();
		return repository.findAll(propertyId)
			.stream()
			.map((room) -> InventoryRoomResponse.of(room, pictures.getOrDefault(room.id(), List.of())))
			.toList();
	}

	@GetMapping("/{id}")
	ResponseEntity<InventoryRoomResponse> get(@PathVariable UUID id) {
		return ResponseEntity.of(repository.findById(id).map(this::toResponse));
	}

	@PostMapping
	@Transactional
	ResponseEntity<InventoryRoomResponse> create(@RequestBody InventoryRoomRequest request) {
		InventoryRoom room = repository.save(request.toRoom(null, building(request.propertyId())));
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(room.id());
		return ResponseEntity.created(location).body(InventoryRoomResponse.of(room, List.of()));
	}

	@PutMapping("/{id}")
	@Transactional
	ResponseEntity<InventoryRoomResponse> replace(@PathVariable UUID id, @RequestBody InventoryRoomRequest request) {
		InventoryRoom existing = repository.findById(id).orElse(null);
		if (existing == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(toResponse(repository.save(request.toRoom(id, existing.propertyId()))));
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable UUID id) {
		return repository.deleteById(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
	}

	/**
	 * Make the rooms of the building those of the list, in its order. Rooms left out are deleted
	 * with their items and photos.
	 */
	@PutMapping("/rooms")
	@Transactional
	List<InventoryRoomResponse> replaceRooms(@RequestParam UUID propertyId, @RequestBody List<InventoryRoomName> rooms) {
		repository.replaceRooms(building(propertyId), rooms);
		return list(propertyId);
	}

	/**
	 * The items of the building that are in no room.
	 */
	@GetMapping("/other-items")
	List<InventoryItem> otherItems(@RequestParam UUID propertyId) {
		return repository.findOtherItems(propertyId);
	}

	/**
	 * Replace the items of the building that are in no room.
	 */
	@PutMapping("/other-items")
	@Transactional
	List<InventoryItem> replaceOtherItems(@RequestParam UUID propertyId,
			@RequestBody List<InventoryItemRequest> items) {
		return repository.replaceOtherItems(building(propertyId), InventoryItemRequest.toItems(items));
	}

	@PostMapping(path = "/{id}/pictures", consumes = "image/*")
	ResponseEntity<PictureResponse> addPicture(@PathVariable UUID id,
			@RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType, @RequestBody byte[] data) {
		String base = InventoryRoomResponse.base(id);
		return repository.addPicture(id, uploads.read(contentType, data))
			.map((info) -> ResponseEntity.created(URI.create(info.url(base))).body(PictureResponse.of(base, info)))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/{id}/pictures/{pictureId}")
	ResponseEntity<byte[]> getPicture(@PathVariable UUID id, @PathVariable UUID pictureId) {
		return PictureUploads.pictureResponse(repository.findPicture(id, pictureId), "picture");
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
	 * Put the room's photos in this order; the first becomes the cover.
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

	/**
	 * The id, if it is that of a building: land has no inventory.
	 */
	private UUID building(@Nullable UUID propertyId) {
		if (propertyId == null || properties.findById(propertyId)
			.filter((property) -> property.kind() == PropertyKind.BUILDING)
			.isEmpty()) {
			throw new IllegalArgumentException("the inventory is kept per building");
		}
		return propertyId;
	}

	/**
	 * The items missing from the building.
	 */
	@GetMapping("/missing-items")
	List<InventoryItem> missingItems(@RequestParam UUID propertyId) {
		return repository.findMissingItems(propertyId);
	}

	/**
	 * Replace the items missing from the building.
	 */
	@PutMapping("/missing-items")
	@Transactional
	List<InventoryItem> replaceMissingItems(@RequestParam UUID propertyId,
			@RequestBody List<InventoryItemRequest> items) {
		return repository.replaceMissingItems(building(propertyId), InventoryItemRequest.toItems(items));
	}

	private InventoryRoomResponse toResponse(InventoryRoom room) {
		return InventoryRoomResponse.of(room, repository.listPictures(room.id()));
	}

}
