package com.mephys.attic.controller;

import com.mephys.attic.dto.CommentRequest;
import com.mephys.attic.dto.PaymentRequest;
import com.mephys.attic.dto.RenovationRequest;
import com.mephys.attic.dto.RenovationResponse;
import com.mephys.attic.model.Contributors;
import com.mephys.attic.model.Heir;
import com.mephys.attic.model.Renovation;
import com.mephys.attic.repository.HeirRepository;
import com.mephys.attic.repository.RenovationRepository;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
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

/**
 * Renovations of the house. The cost of each is divided among those who pay the contribution in its
 * year, in proportion to their {@linkplain Contributors#portions portion}, and administrators tick
 * off who has paid. Everybody signed in sees them; administrators change them.
 */
@RestController
class RenovationController {

	private final RenovationRepository renovations;

	private final HeirRepository heirs;

	RenovationController(RenovationRepository renovations, HeirRepository heirs) {
		this.renovations = renovations;
		this.heirs = heirs;
	}

	@GetMapping("/renovations")
	List<RenovationResponse> list() {
		List<Heir> all = heirs.findAll();
		Map<UUID, Set<UUID>> payments = renovations.findPayments();
		return renovations.findAll()
			.stream()
			.map((renovation) -> response(renovation, all, payments.getOrDefault(renovation.id(), Set.of())))
			.toList();
	}

	@PostMapping("/renovations")
	ResponseEntity<RenovationResponse> create(@RequestBody RenovationRequest request) {
		Renovation renovation = request.toRenovation(null, null);
		renovations.save(renovation);
		return ResponseEntity.created(URI.create("/api/renovations/" + renovation.id())).body(response(renovation.id()));
	}

	/** Change the year, title, description or cost; the comment and payments stay */
	@PutMapping("/renovations/{id}")
	@Transactional
	ResponseEntity<RenovationResponse> replace(@PathVariable UUID id, @RequestBody RenovationRequest request) {
		Renovation existing = renovations.findById(id).orElse(null);
		if (existing == null) {
			return ResponseEntity.notFound().build();
		}
		renovations.save(request.toRenovation(id, existing.comment()));
		return ResponseEntity.ok(response(id));
	}

	@PutMapping("/renovations/{id}/comment")
	ResponseEntity<RenovationResponse> setComment(@PathVariable UUID id, @RequestBody CommentRequest request) {
		if (renovations.findById(id).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		renovations.updateComment(id, request.cleaned());
		return ResponseEntity.ok(response(id));
	}

	/** Tick off, or untick, that the heir has paid their part */
	@PutMapping("/renovations/{id}/payments/{heirId}")
	ResponseEntity<RenovationResponse> setPaid(@PathVariable UUID id, @PathVariable UUID heirId,
			@RequestBody PaymentRequest request) {
		if (renovations.findById(id).isEmpty() || heirs.findById(heirId).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		renovations.setPaid(id, heirId, request.paid());
		return ResponseEntity.ok(response(id));
	}

	/** Delete the renovation with its payments */
	@DeleteMapping("/renovations/{id}")
	ResponseEntity<Void> delete(@PathVariable UUID id) {
		return renovations.deleteById(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
	}

	private RenovationResponse response(UUID id) {
		return response(renovations.findById(id).orElseThrow(), heirs.findAll(),
				renovations.findPayments().getOrDefault(id, Set.of()));
	}

	/** Those who pay in the renovation's year, and anyone else ticked off, in the order of the Heirs tab */
	private static RenovationResponse response(Renovation renovation, List<Heir> all, Set<UUID> paid) {
		Map<UUID, BigDecimal> dues = Contributors.shares(renovation.costEur(),
				Contributors.portions(all, renovation.year()));
		List<RenovationResponse.Line> lines = new ArrayList<>();
		BigDecimal paidEur = BigDecimal.ZERO.setScale(2);
		BigDecimal missingEur = BigDecimal.ZERO.setScale(2);
		for (Heir heir : all) {
			BigDecimal due = dues.get(heir.id());
			boolean hasPaid = paid.contains(heir.id());
			if (due == null && !hasPaid) {
				continue;
			}
			lines.add(new RenovationResponse.Line(heir.id(), heir.name(), heir.deceased(), due, hasPaid));
			if (due != null && hasPaid) {
				paidEur = paidEur.add(due);
			}
			else if (due != null) {
				missingEur = missingEur.add(due);
			}
		}
		return new RenovationResponse(renovation.id(), renovation.year(), renovation.title(), renovation.description(),
				renovation.costEur(), renovation.comment(), lines, paidEur, missingEur);
	}

}
