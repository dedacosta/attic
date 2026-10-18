package com.mephys.attic.dto;

import com.mephys.attic.model.RenovationPayment;

import org.jspecify.annotations.Nullable;

/** Whether an heir has paid their part, and whether they did workmanship; what is left out stays */
public record RenovationPaymentRequest(@Nullable Boolean paid, @Nullable Boolean workmanship) {

	public RenovationPayment applyTo(RenovationPayment current) {
		return new RenovationPayment((paid != null) ? paid : current.paid(),
				(workmanship != null) ? workmanship : current.workmanship());
	}

}
