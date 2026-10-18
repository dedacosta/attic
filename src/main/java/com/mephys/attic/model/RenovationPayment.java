package com.mephys.attic.model;

/**
 * What an heir gave to a renovation: they paid their part, did workmanship, or both.
 */
public record RenovationPayment(boolean paid, boolean workmanship) {

	public static final RenovationPayment NONE = new RenovationPayment(false, false);

	public boolean any() {
		return paid || workmanship;
	}

}
