package com.mephys.attic.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An heir's share of the heritage as a fraction between 0 and 1, kept as entered (2/6 stays
 * 2/6, as it may be written that way in legal papers).
 */
public record HeritageShare(int numerator, int denominator) {

	private static final Pattern FRACTION = Pattern.compile("\\s*(\\d{1,9})\\s*(?:/\\s*(\\d{1,9})\\s*)?");

	public HeritageShare {
		if (denominator <= 0 || numerator < 0 || numerator > denominator) {
			throw new IllegalArgumentException("heritageShare must be a fraction between 0 and 1, like 1/3");
		}
	}

	/**
	 * Parse "1/3", or "1" / "0" for a whole or no share.
	 */
	public static HeritageShare parse(String text) {
		Matcher matcher = FRACTION.matcher(text);
		if (!matcher.matches()) {
			throw new IllegalArgumentException("heritageShare must be a fraction between 0 and 1, like 1/3");
		}
		int numerator = Integer.parseInt(matcher.group(1));
		int denominator = (matcher.group(2) != null) ? Integer.parseInt(matcher.group(2)) : 1;
		return new HeritageShare(numerator, denominator);
	}

	@Override
	public String toString() {
		return numerator + "/" + denominator;
	}

}
