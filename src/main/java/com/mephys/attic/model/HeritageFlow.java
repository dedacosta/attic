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
 * among the heirs without a parent whose share is not entered. An heir who has died passes what they receive on to their children, in equal parts, and
 * so on down the generations; one who has died without children keeps it. The children of a
 * living heir receive nothing.
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
		HeritageShare unentered = remainder(roots);
		Map<UUID, HeritageShare> shares = new HashMap<>();
		for (Heir root : roots) {
			flow(root, (root.heritageShare() != null) ? root.heritageShare() : unentered, children, shares);
		}
		return shares;
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
			Map<UUID, HeritageShare> shares) {
		if (received == null) {
			return;
		}
		shares.put(heir.id(), received);
		List<Heir> heirChildren = children.getOrDefault(heir.id(), List.of());
		if (heir.deceased() && !heirChildren.isEmpty()) {
			HeritageShare part = received.split(heirChildren.size());
			for (Heir child : heirChildren) {
				flow(child, part, children, shares);
			}
		}
	}

}
