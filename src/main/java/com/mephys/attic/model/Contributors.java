package com.mephys.attic.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Who pays the annual contribution in a year, and which portion of a yearly amount each owes.
 * <p>
 * Every heir without a parent holds one yearly amount. While they are {@linkplain Heir#activeIn
 * alive} that year, they pay it. Once they have died, it is divided equally among their children,
 * the way the heritage passes on, from the year after the death; a child who has died too passes
 * their part on in turn. An heir who died without descendants alive that year leaves nothing to
 * pay. So a child pays half the yearly amount if they have one brother or sister, and nothing while
 * their parent is alive.
 */
public final class Contributors {

	private static final HeritageShare WHOLE = new HeritageShare(1, 1);

	private Contributors() {
	}

	/**
	 * The portion of the yearly amount each heir owes in the year; those who do not pay are left
	 * out. An heir whose parent is not among {@code heirs} counts as one without a parent.
	 */
	public static Map<UUID, HeritageShare> portions(List<Heir> heirs, int year) {
		Set<UUID> ids = heirs.stream().map(Heir::id).collect(Collectors.toSet());
		Map<UUID, List<Heir>> children = new HashMap<>();
		List<Heir> roots = new ArrayList<>();
		for (Heir heir : heirs) {
			if (heir.parentId() != null && ids.contains(heir.parentId())) {
				children.computeIfAbsent(heir.parentId(), (id) -> new ArrayList<>()).add(heir);
			}
			else {
				roots.add(heir);
			}
		}
		Map<UUID, Boolean> counting = new HashMap<>();
		Map<UUID, HeritageShare> portions = new HashMap<>();
		for (Heir root : roots) {
			if (counts(root, year, children, counting)) {
				flow(root, WHOLE, year, children, counting, portions);
			}
		}
		return portions;
	}

	/**
	 * An amount, such as the cost of a renovation, divided among those who pay in proportion to their
	 * {@linkplain #portions portion}, rounded to cents.
	 */
	public static Map<UUID, BigDecimal> shares(BigDecimal amount, Map<UUID, HeritageShare> portions) {
		BigDecimal whole = portions.values()
			.stream()
			.map(Contributors::decimal)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		Map<UUID, BigDecimal> shares = new HashMap<>();
		if (whole.signum() == 0) {
			return shares;
		}
		portions.forEach((heirId, portion) -> shares.put(heirId,
				amount.multiply(decimal(portion)).divide(whole, 2, RoundingMode.HALF_UP)));
		return shares;
	}

	private static BigDecimal decimal(HeritageShare share) {
		return BigDecimal.valueOf(share.numerator())
			.divide(BigDecimal.valueOf(share.denominator()), MathContext.DECIMAL64);
	}

	/** Whether the heir, or a descendant through whom their portion passes, is alive in the year */
	private static boolean counts(Heir heir, int year, Map<UUID, List<Heir>> children, Map<UUID, Boolean> counting) {
		Boolean known = counting.get(heir.id());
		if (known != null) {
			return known;
		}
		// Guards against a cycle, which the heir endpoints do not allow anyway
		counting.put(heir.id(), false);
		boolean result = heir.activeIn(year) || children.getOrDefault(heir.id(), List.of())
			.stream()
			.anyMatch((child) -> counts(child, year, children, counting));
		counting.put(heir.id(), result);
		return result;
	}

	private static void flow(Heir heir, HeritageShare portion, int year, Map<UUID, List<Heir>> children,
			Map<UUID, Boolean> counting, Map<UUID, HeritageShare> portions) {
		if (heir.activeIn(year)) {
			portions.put(heir.id(), portion);
			return;
		}
		List<Heir> heirs = children.getOrDefault(heir.id(), List.of())
			.stream()
			.filter((child) -> counts(child, year, children, counting))
			.toList();
		HeritageShare part = portion.split(heirs.size());
		for (Heir child : heirs) {
			flow(child, part, year, children, counting, portions);
		}
	}

}
