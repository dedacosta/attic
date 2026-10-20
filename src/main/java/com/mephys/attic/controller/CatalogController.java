package com.mephys.attic.controller;

import com.mephys.attic.dto.CatalogItemRequest;
import com.mephys.attic.dto.CatalogItemResponse;
import com.mephys.attic.dto.PictureResponse;
import com.mephys.attic.model.CatalogItem;
import com.mephys.attic.model.Heir;
import com.mephys.attic.model.InventoryRoom;
import com.mephys.attic.model.PictureInfo;
import com.mephys.attic.model.PropertyKind;
import com.mephys.attic.repository.CatalogRepository;
import com.mephys.attic.repository.HeirRepository;
import com.mephys.attic.repository.InventoryRepository;
import com.mephys.attic.repository.PropertyRepository;
import com.mephys.attic.service.PictureUploads;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/catalog")
class CatalogController {

	private final CatalogRepository repository;

	private final InventoryRepository rooms;

	private final PropertyRepository properties;

	private final HeirRepository heirs;

	private final PictureUploads uploads;

	CatalogController(CatalogRepository repository, InventoryRepository rooms, PropertyRepository properties,
			HeirRepository heirs, PictureUploads uploads) {
		this.repository = repository;
		this.rooms = rooms;
		this.properties = properties;
		this.heirs = heirs;
		this.uploads = uploads;
	}

	@GetMapping
	List<CatalogItemResponse> list() {
		Map<UUID, List<PictureInfo>> pictures = repository.listAllPictures();
		List<Heir> all = heirs.findAll();
		return repository.findAll()
			.stream()
			.map((item) -> CatalogItemResponse.of(item, all, pictures.getOrDefault(item.id(), List.of())))
			.toList();
	}

	@GetMapping("/{id}")
	ResponseEntity<CatalogItemResponse> get(@PathVariable UUID id) {
		return ResponseEntity.of(repository.findById(id).map(this::toResponse));
	}

	@PostMapping
	@Transactional
	ResponseEntity<CatalogItemResponse> create(@RequestBody CatalogItemRequest request) {
		CatalogItem item = repository.save(toItem(request, null));
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(item.id());
		return ResponseEntity.created(location).body(CatalogItemResponse.of(item, heirs.findAll(), List.of()));
	}

	@PutMapping("/{id}")
	@Transactional
	ResponseEntity<CatalogItemResponse> replace(@PathVariable UUID id, @RequestBody CatalogItemRequest request) {
		if (repository.findById(id).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(toResponse(repository.save(toItem(request, id))));
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable UUID id) {
		return repository.deleteById(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
	}

	@PostMapping(path = "/{id}/pictures", consumes = "image/*")
	ResponseEntity<PictureResponse> addPicture(@PathVariable UUID id,
			@RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType, @RequestBody byte[] data) {
		String base = CatalogItemResponse.base(id);
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

	/**
	 * The item of the request. Its room is a room of its building, which is that of the room when
	 * none is given, its building is a building, not a land parcel, and its owners are heirs.
	 */
	private CatalogItem toItem(CatalogItemRequest request, @Nullable UUID id) {
		if (request.ownerIds() != null) {
			Set<UUID> known = heirs.findAll().stream().map(Heir::id).collect(Collectors.toSet());
			if (!known.containsAll(request.ownerIds())) {
				throw new IllegalArgumentException("an owner is not an heir");
			}
		}
		UUID propertyId = request.propertyId();
		UUID roomId = request.roomId();
		if (roomId != null) {
			InventoryRoom room = rooms.findById(roomId)
				.orElseThrow(() -> new IllegalArgumentException("room does not exist"));
			if (propertyId != null && !propertyId.equals(room.propertyId())) {
				throw new IllegalArgumentException("room is not a room of the building");
			}
			propertyId = room.propertyId();
		}
		else if (propertyId != null && properties.findById(propertyId)
			.filter((property) -> property.kind() == PropertyKind.BUILDING)
			.isEmpty()) {
			throw new IllegalArgumentException("an item is in a building");
		}
		return request.toItem(id, propertyId, roomId);
	}

	private CatalogItemResponse toResponse(CatalogItem item) {
		return CatalogItemResponse.of(item, heirs.findAll(), repository.listPictures(item.id()));
	}

}
