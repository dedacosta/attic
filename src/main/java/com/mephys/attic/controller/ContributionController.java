package com.mephys.attic.controller;

import com.mephys.attic.dto.CommentRequest;
import com.mephys.attic.dto.ContributionAmount;
import com.mephys.attic.dto.ContributionYearResponse;
import com.mephys.attic.dto.NewContributionYear;
import com.mephys.attic.model.Contributors;
import com.mephys.attic.model.Heir;
import com.mephys.attic.model.HeritageShare;
import com.mephys.attic.repository.ContributionRepository;
import com.mephys.attic.repository.HeirRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
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
 * The annual contribution. Each year lists every heir, whether they {@linkplain Contributors pay}
 * that year, and the amount entered for each. Everybody signed in sees all years; administrators
 * add and delete years and enter the amounts.
 */
@RestController
class ContributionController {

	private final ContributionRepository contributions;

	private final HeirRepository heirs;

	ContributionController(ContributionRepository contributions, HeirRepository heirs) {
		this.contributions = contributions;
		this.heirs = heirs;
	}

	@GetMapping("/contributions")
	List<ContributionYearResponse> list() {
		List<Heir> all = heirs.findAll();
		Map<Integer, Map<UUID, BigDecimal>> amounts = contributions.findAmounts();
		return contributions.findYears()
			.stream()
			.map((year) -> response(year, all, amounts.getOrDefault(year.year(), Map.of())))
			.toList();
	}

	@PostMapping("/contributions")
	@Transactional
	ResponseEntity<ContributionYearResponse> createYear(@RequestBody NewContributionYear request) {
		Integer year = request.year();
		if (year == null || year < 1900 || year > 2999) {
			throw new IllegalArgumentException("year must be between 1900 and 2999");
		}
		if (!contributions.createYear(year)) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
		return ResponseEntity.created(URI.create("/api/contributions/" + year)).body(response(year));
	}

	/** Delete the year with all its amounts */
	@DeleteMapping("/contributions/{year}")
	ResponseEntity<Void> deleteYear(@PathVariable int year) {
		return contributions.deleteYear(year) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
	}

	/** Enter, change or, with a {@code null} amount, remove what the heir contributed in the year */
	@PutMapping("/contributions/{year}/{heirId}")
	@Transactional
	ResponseEntity<ContributionYearResponse> setAmount(@PathVariable int year, @PathVariable UUID heirId,
			@RequestBody ContributionAmount request) {
		if (!contributions.yearExists(year) || heirs.findById(heirId).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		if (request.amountEur() == null) {
			contributions.deleteAmount(year, heirId);
		}
		else {
			BigDecimal amount = request.amountEur().setScale(2, RoundingMode.HALF_UP);
			if (amount.signum() < 0) {
				throw new IllegalArgumentException("amountEur must not be negative");
			}
			contributions.saveAmount(year, heirId, amount);
		}
		return ResponseEntity.ok(response(year));
	}

	/** Change the comment on the year; a blank one is removed */
	@PutMapping("/contributions/{year}/comment")
	ResponseEntity<ContributionYearResponse> setComment(@PathVariable int year,
			@RequestBody CommentRequest request) {
		if (!contributions.yearExists(year)) {
			return ResponseEntity.notFound().build();
		}
		contributions.updateComment(year, request.cleaned());
		return ResponseEntity.ok(response(year));
	}

	private ContributionYearResponse response(int year) {
		return response(contributions.findYear(year).orElseThrow(), heirs.findAll(),
				contributions.findAmounts().getOrDefault(year, Map.of()));
	}

	/** Every heir, in the order of the Heirs tab */
	private static ContributionYearResponse response(ContributionRepository.StoredYear stored, List<Heir> all,
			Map<UUID, BigDecimal> amounts) {
		int year = stored.year();
		Map<UUID, HeritageShare> portions = Contributors.portions(all, year);
		List<ContributionYearResponse.Line> lines = all.stream().map((heir) -> {
			HeritageShare portion = portions.get(heir.id());
			return new ContributionYearResponse.Line(heir.id(), heir.name(), heir.deceased(), portion != null,
					(portion != null) ? (double) portion.numerator() / portion.denominator() : null,
					amounts.get(heir.id()));
		}).toList();
		BigDecimal total = amounts.values().stream().reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
		return new ContributionYearResponse(year, lines, total, stored.comment());
	}

}
