package com.mephys.attic.controller;

import com.mephys.attic.dto.CommentRequest;
import com.mephys.attic.dto.RenovationExpenseRequest;
import com.mephys.attic.dto.RenovationPaymentRequest;
import com.mephys.attic.dto.RenovationRequest;
import com.mephys.attic.dto.RenovationResponse;
import com.mephys.attic.model.Contributors;
import com.mephys.attic.model.Heir;
import com.mephys.attic.model.Renovation;
import com.mephys.attic.model.RenovationExpense;
import com.mephys.attic.model.RenovationPayment;
import com.mephys.attic.repository.HeirRepository;
import com.mephys.attic.repository.RenovationRepository;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * Renovations of the house, each a list of expenses. The cost of each, the total of its expenses, is
 * divided among those who pay the contribution in its year, in proportion to their
 * {@linkplain Contributors#portions portion}, and administrators tick off who has paid. Everybody
 * signed in sees them; administrators change them.
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
		Map<UUID, Map<UUID, RenovationPayment>> payments = renovations.findPayments();
		Map<UUID, List<RenovationExpense>> expenses = renovations.findExpenses();
		return renovations.findAll()
			.stream()
			.map((renovation) -> response(renovation, all, payments.getOrDefault(renovation.id(), Map.of()),
					expenses.getOrDefault(renovation.id(), List.of())))
			.toList();
	}

	@PostMapping("/renovations")
	ResponseEntity<RenovationResponse> create(@RequestBody RenovationRequest request) {
		Renovation renovation = request.toRenovation(null, null);
		renovations.save(renovation);
		return ResponseEntity.created(URI.create("/api/renovations/" + renovation.id())).body(response(renovation.id()));
	}

	/** Change the year, title or description; the comment, expenses and payments stay */
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

	/** Make the expenses of the renovation those of the list, in its order */
	@PutMapping("/renovations/{id}/expenses")
	@Transactional
	ResponseEntity<RenovationResponse> setExpenses(@PathVariable UUID id,
			@RequestBody List<RenovationExpenseRequest> request) {
		if (renovations.findById(id).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		renovations.replaceExpenses(id, request.stream().map(RenovationExpenseRequest::toExpense).toList());
		return ResponseEntity.ok(response(id));
	}

	/** Tick off, or untick, that the heir has paid their part or did workmanship */
	@PutMapping("/renovations/{id}/payments/{heirId}")
	@Transactional
	ResponseEntity<RenovationResponse> setPayment(@PathVariable UUID id, @PathVariable UUID heirId,
			@RequestBody RenovationPaymentRequest request) {
		if (renovations.findById(id).isEmpty() || heirs.findById(heirId).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		RenovationPayment current = renovations.findPayments()
			.getOrDefault(id, Map.of())
			.getOrDefault(heirId, RenovationPayment.NONE);
		renovations.setPayment(id, heirId, request.applyTo(current));
		return ResponseEntity.ok(response(id));
	}

	/** Delete the renovation with its expenses and payments */
	@DeleteMapping("/renovations/{id}")
	ResponseEntity<Void> delete(@PathVariable UUID id) {
		return renovations.deleteById(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
	}

	private RenovationResponse response(UUID id) {
		return response(renovations.findById(id).orElseThrow(), heirs.findAll(),
				renovations.findPayments().getOrDefault(id, Map.of()), renovations.findExpenses(id));
	}

	/**
	 * The expenses, then a line for each heir alive in the renovation's year, who pays their part,
	 * did workmanship or can do it, and anyone else ticked off, in the order of the Heirs tab. A part
	 * is settled when it is paid or the heir did workmanship.
	 */
	private static RenovationResponse response(Renovation renovation, List<Heir> all,
			Map<UUID, RenovationPayment> payments, List<RenovationExpense> expenses) {
		BigDecimal cost = expenses.stream()
			.map(RenovationExpense::amountEur)
			.reduce(BigDecimal.valueOf(0, 2), BigDecimal::add);
		Map<UUID, BigDecimal> dues = Contributors.shares(cost, Contributors.portions(all, renovation.year()));
		List<RenovationResponse.Line> lines = new ArrayList<>();
		BigDecimal paidEur = BigDecimal.valueOf(0, 2);
		BigDecimal missingEur = BigDecimal.valueOf(0, 2);
		for (Heir heir : all) {
			BigDecimal due = dues.get(heir.id());
			RenovationPayment payment = payments.getOrDefault(heir.id(), RenovationPayment.NONE);
			if (due == null && !payment.any() && !alive(heir, renovation.year())) {
				continue;
			}
			lines.add(new RenovationResponse.Line(heir.id(), heir.name(), heir.deceased(), due, payment.paid(),
					payment.workmanship()));
			if (due != null && payment.any()) {
				paidEur = paidEur.add(due);
			}
			else if (due != null) {
				missingEur = missingEur.add(due);
			}
		}
		return new RenovationResponse(renovation.id(), renovation.year(), renovation.title(), renovation.description(),
				cost, renovation.comment(), expenses, lines, paidEur, missingEur);
	}

	/** Whether the heir is born by the year and has not died before it, as far as is known */
	private static boolean alive(Heir heir, int year) {
		boolean born = heir.birthDate() == null || heir.birthDate().getYear() <= year;
		boolean living = (heir.deathDate() != null) ? heir.deathDate().getYear() >= year : !heir.deceased();
		return born && living;
	}

}
