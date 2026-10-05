package com.mephys.attic.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

/**
 * How the heritage flows down the family. The whole heritage is 1. An heir without a parent
 * receives the share entered for them; what the entered shares leave over is divided equally
 * among the heirs without a parent whose share is not entered. An heir who has died passes what
 * they receive on to their children, in equal parts, and so on down the generations. The children
 * of a living heir receive nothing.
 * <p>
 * Only heirs who {@linkplain #counts count} take part: one who has died without descendants who
 * inherit receives nothing, not even a share entered for them, and is left out when dividing.
 */
public final class HeritageFlow {

	private HeritageFlow() {
	}

	/**
	 * The share each heir receives; heirs who receive nothing are left out.
	 */
	public static Map<UUID, HeritageShare> calculate(List<Heir> heirs) {
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
		List<Heir> countingRoots = roots.stream().filter((root) -> counts(root, children, counting)).toList();
		HeritageShare unentered = remainder(countingRoots);
		Map<UUID, HeritageShare> shares = new HashMap<>();
		for (Heir root : countingRoots) {
			flow(root, (root.heritageShare() != null) ? root.heritageShare() : unentered, children, counting, shares);
		}
		return shares;
	}

	/**
	 * Whether the heir takes part in the heritage: they are alive, or they have died and one of
	 * their children takes part.
	 */
	private static boolean counts(Heir heir, Map<UUID, List<Heir>> children, Map<UUID, Boolean> counting) {
		Boolean known = counting.get(heir.id());
		if (known != null) {
			return known;
		}
		boolean result = !heir.deceased() || children.getOrDefault(heir.id(), List.of())
			.stream()
			.anyMatch((child) -> counts(child, children, counting));
		counting.put(heir.id(), result);
		return result;
	}

	/**
	 * The share of each heir without an entered share: an equal part of what the entered shares
	 * leave over, or {@code null} if they leave nothing.
	 */
	private static @Nullable HeritageShare remainder(List<Heir> roots) {
		long numerator = 0;
		long denominator = 1;
		int unentered = 0;
		for (Heir root : roots) {
			HeritageShare share = root.heritageShare();
			if (share == null) {
				unentered++;
				continue;
			}
			numerator = numerator * share.denominator() + share.numerator() * denominator;
			denominator *= share.denominator();
			long divisor = gcd(numerator, denominator);
			numerator /= divisor;
			denominator /= divisor;
		}
		long left = denominator - numerator;
		if (unentered == 0 || left <= 0) {
			return null;
		}
		long divisor = gcd(left, denominator);
		return new HeritageShare(Math.toIntExact(left / divisor), Math.toIntExact(denominator / divisor))
			.split(unentered);
	}

	private static long gcd(long a, long b) {
		return (b == 0) ? Math.max(a, 1) : gcd(b, a % b);
	}

	private static void flow(Heir heir, @Nullable HeritageShare received, Map<UUID, List<Heir>> children,
			Map<UUID, Boolean> counting, Map<UUID, HeritageShare> shares) {
		if (received == null) {
			return;
		}
		shares.put(heir.id(), received);
		if (heir.deceased()) {
			// Only the children who take part divide the share; a deceased heir who counts has one
			List<Heir> heirs = children.getOrDefault(heir.id(), List.of())
				.stream()
				.filter((child) -> counts(child, children, counting))
				.toList();
			HeritageShare part = received.split(heirs.size());
			for (Heir child : heirs) {
				flow(child, part, children, counting, shares);
			}
		}
	}

}
