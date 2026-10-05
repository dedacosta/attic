package com.mephys.attic.controller;

import com.mephys.attic.dto.HeirRequest;
import com.mephys.attic.dto.HeirResponse;
import com.mephys.attic.model.Heir;
import com.mephys.attic.model.HeritageFlow;
import com.mephys.attic.model.HeritageShare;
import com.mephys.attic.model.Sex;
import com.mephys.attic.repository.HeirRepository;
import com.mephys.attic.service.CurrentAccount;
import com.mephys.attic.service.HeirDocuments;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
class HeirController {

	private final HeirRepository repository;

	private final HeirDocuments documents;

	private final CurrentAccount account;

	HeirController(HeirRepository repository, HeirDocuments documents, CurrentAccount account) {
		this.repository = repository;
		this.documents = documents;
		this.account = account;
	}

	@GetMapping("/sexes")
	List<Sex> sexes() {
		return List.of(Sex.values());
	}

	/**
	 * Everybody for administrators; for a user only the heir linked to their account.
	 */
	@GetMapping("/heirs")
	List<HeirResponse> list() {
		List<Heir> heirs = repository.findAll();
		Map<UUID, HeritageShare> shares = HeritageFlow.calculate(heirs);
		return heirs.stream()
			.filter((heir) -> account.maySee(heir.id()))
			.map((heir) -> HeirResponse.of(heir, shares))
			.toList();
	}

	@GetMapping("/heirs/{id}")
	ResponseEntity<HeirResponse> get(@PathVariable UUID id) {
		return ResponseEntity.of(repository.findById(id).filter((heir) -> account.maySee(id)).map(this::response));
	}

	@PostMapping("/heirs")
	@Transactional
	synchronized ResponseEntity<HeirResponse> create(@RequestBody HeirRequest request) {
		Heir heir = request.toHeir(null);
		checkParent(heir);
		heir = repository.save(heir);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(heir.id());
		return ResponseEntity.created(location).body(response(heir));
	}

	/**
	 * Administrators change anybody. A user may change their own card, but not its heritage share,
	 * parent or death, which stay as the administrators set them.
	 */
	@PutMapping("/heirs/{id}")
	@Transactional
	synchronized ResponseEntity<HeirResponse> replace(@PathVariable UUID id, @RequestBody HeirRequest request) {
		Heir existing = repository.findById(id).filter((heir) -> account.maySee(id)).orElse(null);
		if (existing == null) {
			return ResponseEntity.notFound().build();
		}
		Heir heir = request.toHeir(id);
		if (!account.isAdmin()) {
			heir = new Heir(id, heir.name(), heir.birthDate(), existing.deceased(), existing.deathDate(),
					heir.address(), heir.filiation(), heir.sex(), existing.heritageShare(), heir.comments(),
					existing.parentId(), null, null);
		}
		checkParent(heir);
		return ResponseEntity.ok(response(repository.save(heir)));
	}

	/**
	 * Delete the heir. Their documents are kept, without heir. Their children stay,
	 * without a parent.
	 */
	@DeleteMapping("/heirs/{id}")
	@Transactional
	ResponseEntity<Void> delete(@PathVariable UUID id) {
		if (repository.findById(id).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		documents.unlinkAllOf(id);
		repository.deleteById(id);
		return ResponseEntity.noContent().build();
	}

	/** The heir, with the share they receive from the family as it is now */
	private HeirResponse response(Heir heir) {
		return HeirResponse.of(heir, HeritageFlow.calculate(repository.findAll()));
	}

	/**
	 * The parent must exist and must not be the heir or one of the heir's descendants.
	 */
	private void checkParent(Heir heir) {
		UUID parentId = heir.parentId();
		Set<UUID> seen = new HashSet<>();
		while (parentId != null && seen.add(parentId)) {
			if (parentId.equals(heir.id())) {
				throw new IllegalArgumentException("an heir cannot be a child of their own descendant");
			}
			UUID id = parentId;
			parentId = repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("parent does not exist"))
				.parentId();
		}
	}

}
