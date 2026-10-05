package com.mephys.attic.dto;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

/** The amount an heir contributed in a year, in euros; {@code null} removes it */
public record ContributionAmount(@Nullable BigDecimal amountEur) {
}
