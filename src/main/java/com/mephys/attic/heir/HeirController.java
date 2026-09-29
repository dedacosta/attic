package com.mephys.attic.heir;

import com.mephys.attic.document.HeirDocuments;
import com.mephys.attic.security.CurrentAccount;

import java.net.URI;
import java.util.List;
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
		return repository.findAll().stream().filter((heir) -> account.maySee(heir.id())).map(HeirResponse::of)
			.toList();
	}

	@GetMapping("/heirs/{id}")
	ResponseEntity<HeirResponse> get(@PathVariable UUID id) {
		return ResponseEntity.of(repository.findById(id).filter((heir) -> account.maySee(id)).map(HeirResponse::of));
	}

	@PostMapping("/heirs")
	ResponseEntity<HeirResponse> create(@RequestBody HeirRequest request) {
		Heir heir = repository.save(request.toHeir(null));
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(heir.id());
		return ResponseEntity.created(location).body(HeirResponse.of(heir));
	}

	/**
	 * Administrators change anybody. A user may change their own card, but not its heritage share,
	 * which stays as the administrators set it.
	 */
	@PutMapping("/heirs/{id}")
	ResponseEntity<HeirResponse> replace(@PathVariable UUID id, @RequestBody HeirRequest request) {
		Heir existing = repository.findById(id).filter((heir) -> account.maySee(id)).orElse(null);
		if (existing == null) {
			return ResponseEntity.notFound().build();
		}
		Heir heir = request.toHeir(id);
		if (!account.isAdmin()) {
			heir = new Heir(id, heir.name(), heir.birthDate(), heir.address(), heir.filiation(),
					heir.sex(), existing.heritageShare(), heir.comments(), null, null);
		}
		return ResponseEntity.ok(HeirResponse.of(repository.save(heir)));
	}

	/**
	 * Delete the heir. Their documents are kept, without heir.
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

}
