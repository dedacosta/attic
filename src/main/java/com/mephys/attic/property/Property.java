package com.mephys.attic.property;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A property of the heritage: a {@link House} or a {@link Land}. {@code valueEur} is
 * {@code null} when not estimated; {@code facts} are its details in order, without blank lines.
 */
abstract sealed class Property permits House, Land {

	private final UUID id;

	private final String name;

	private final @Nullable String address;

	private final @Nullable BigDecimal valueEur;

	private final @Nullable String comments;

	private final List<PropertyFact> facts;

	protected Property(@Nullable UUID id, String name, @Nullable String address, @Nullable BigDecimal valueEur,
			@Nullable String comments, @Nullable List<PropertyFact> facts) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("name must not be blank");
		}
		if (valueEur != null) {
			valueEur = valueEur.setScale(2, RoundingMode.HALF_UP);
			if (valueEur.signum() < 0) {
				throw new IllegalArgumentException("valueEur must not be negative");
			}
		}
		this.id = (id != null) ? id : UUID.randomUUID();
		this.name = name.strip();
		this.address = blankToNull(address);
		this.valueEur = valueEur;
		this.comments = blankToNull(comments);
		this.facts = (facts != null) ? facts.stream().filter((fact) -> !fact.isBlank()).toList() : List.of();
	}

	/**
	 * The house or the land with these values, as {@code kind} says.
	 */
	static Property of(@Nullable UUID id, PropertyKind kind, String name, @Nullable String address,
			@Nullable BigDecimal valueEur, @Nullable String comments, @Nullable List<PropertyFact> facts) {
		if (kind == null) {
			throw new IllegalArgumentException("kind must not be null");
		}
		return switch (kind) {
			case HOUSE -> new House(id, name, address, valueEur, comments, facts);
			case LAND -> new Land(id, name, address, valueEur, comments, facts);
		};
	}

	/** Which of the subclasses this is, as stored and sent */
	abstract PropertyKind kind();

	UUID id() {
		return id;
	}

	String name() {
		return name;
	}

	@Nullable String address() {
		return address;
	}

	@Nullable BigDecimal valueEur() {
		return valueEur;
	}

	@Nullable String comments() {
		return comments;
	}

	List<PropertyFact> facts() {
		return facts;
	}

	private static @Nullable String blankToNull(@Nullable String text) {
		return (text != null && !text.isBlank()) ? text.strip() : null;
	}

}
