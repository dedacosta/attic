package com.mephys.attic.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContributorsTests {

	@Test
	void childrenShareTheirParentsPortionFromTheYearAfterTheDeath() {
		Heir maria = heir("Maria", null, "2025-06-30", null);
		Heir ana = heir("Ana", null, null, maria);
		Heir rui = heir("Rui", null, null, maria);
		Heir joao = heir("João", null, null, null);
		List<Heir> all = List.of(maria, ana, rui, joao);

		Map<UUID, HeritageShare> in2025 = Contributors.portions(all, 2025);
		assertThat(in2025).containsOnlyKeys(maria.id(), joao.id());
		assertThat(in2025.get(maria.id())).hasToString("1/1");

		Map<UUID, HeritageShare> in2026 = Contributors.portions(all, 2026);
		assertThat(in2026).containsOnlyKeys(ana.id(), rui.id(), joao.id());
		assertThat(in2026.get(ana.id())).hasToString("1/2");
		assertThat(in2026.get(rui.id())).hasToString("1/2");
		assertThat(in2026.get(joao.id())).hasToString("1/1");
	}

	@Test
	void grandchildrenShareTheirDeceasedParentsPart() {
		Heir maria = heir("Maria", null, "2020-05-01", null);
		Heir ana = heir("Ana", null, "2023-02-01", maria);
		Heir rui = heir("Rui", null, null, maria);
		Heir celina = heir("Celina", "2010-01-01", null, ana);
		Heir pedro = heir("Pedro", "2012-01-01", null, ana);
		List<Heir> all = List.of(maria, ana, rui, celina, pedro);

		assertThat(Contributors.portions(all, 2022)).containsOnlyKeys(ana.id(), rui.id());
		Map<UUID, HeritageShare> in2024 = Contributors.portions(all, 2024);
		assertThat(in2024.get(rui.id())).hasToString("1/2");
		assertThat(in2024.get(celina.id())).hasToString("1/4");
		assertThat(in2024.get(pedro.id())).hasToString("1/4");
	}

	@Test
	void childNotBornYetDoesNotShare() {
		Heir maria = heir("Maria", null, "2020-05-01", null);
		Heir ana = heir("Ana", null, null, maria);
		Heir rui = heir("Rui", "2022-03-01", null, maria);
		List<Heir> all = List.of(maria, ana, rui);

		assertThat(Contributors.portions(all, 2021).get(ana.id())).hasToString("1/1");
		assertThat(Contributors.portions(all, 2022).get(ana.id())).hasToString("1/2");
	}

	@Test
	void deceasedWithoutDescendantsLeavesNothingToPay() {
		Heir maria = heir("Maria", null, "2020-05-01", null);
		Heir ana = heir("Ana", null, "2019-01-01", maria);
		Heir joao = heir("João", null, null, null);

		assertThat(Contributors.portions(List.of(maria, ana, joao), 2021)).containsOnlyKeys(joao.id());
	}

	@Test
	void nobodyPaysBeforeBirthOrAfterDeath() {
		Heir maria = heir("Maria", "1990-01-01", "2020-05-01", null);
		List<Heir> all = List.of(maria);

		assertThat(Contributors.portions(all, 1989)).isEmpty();
		assertThat(Contributors.portions(all, 1990)).containsKey(maria.id());
		assertThat(Contributors.portions(all, 2020)).containsKey(maria.id());
		assertThat(Contributors.portions(all, 2021)).isEmpty();
	}

	@Test
	void childOfAnUnknownParentPaysOnTheirOwn() {
		Heir maria = heir("Maria", null, null, null);
		Heir ana = heir("Ana", null, null, maria);

		assertThat(Contributors.portions(List.of(ana), 2026).get(ana.id())).hasToString("1/1");
	}

	@Test
	void amountIsSharedInProportionToThePortions() {
		Heir maria = heir("Maria", null, "2020-05-01", null);
		Heir ana = heir("Ana", null, null, maria);
		Heir rui = heir("Rui", null, null, maria);
		Heir joao = heir("João", null, null, null);
		Map<UUID, BigDecimal> shares = Contributors.shares(new BigDecimal("100.00"),
				Contributors.portions(List.of(maria, ana, rui, joao), 2021));

		assertThat(shares.get(ana.id())).isEqualByComparingTo("25.00");
		assertThat(shares.get(rui.id())).isEqualByComparingTo("25.00");
		assertThat(shares.get(joao.id())).isEqualByComparingTo("50.00");
		assertThat(Contributors.shares(BigDecimal.TEN, Map.of())).isEmpty();
	}

	private static Heir heir(String name, @Nullable String birth, @Nullable String death, @Nullable Heir parent) {
		return new Heir(null, name, (birth != null) ? LocalDate.parse(birth) : null, death != null,
				(death != null) ? LocalDate.parse(death) : null, null, null, null, null, null,
				(parent != null) ? parent.id() : null, null, null);
	}

}
