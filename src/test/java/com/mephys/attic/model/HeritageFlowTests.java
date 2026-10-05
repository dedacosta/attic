package com.mephys.attic.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeritageFlowTests {

	@Test
	void livingHeirKeepsTheirShareAndTheirChildrenReceiveNothing() {
		Heir maria = heir("Maria", "1/2", false, null);
		Heir ana = heir("Ana", null, false, maria);

		Map<UUID, HeritageShare> shares = HeritageFlow.calculate(List.of(maria, ana));

		assertThat(shares).containsOnlyKeys(maria.id());
		assertThat(shares.get(maria.id())).hasToString("1/2");
	}

	@Test
	void deceasedHeirPassesTheirShareToTheirChildrenInEqualParts() {
		Heir maria = heir("Maria", "1/2", true, null);
		Heir ana = heir("Ana", null, false, maria);
		Heir rui = heir("Rui", null, false, maria);

		Map<UUID, HeritageShare> shares = HeritageFlow.calculate(List.of(maria, ana, rui));

		assertThat(shares.get(ana.id())).hasToString("1/4");
		assertThat(shares.get(rui.id())).hasToString("1/4");
	}

	@Test
	void shareFlowsDownThroughDeceasedGenerations() {
		Heir maria = heir("Maria", "2/3", true, null);
		Heir ana = heir("Ana", null, true, maria);
		Heir rui = heir("Rui", null, false, maria);
		Heir celina = heir("Celina", null, false, ana);
		Heir joao = heir("João", null, false, ana);

		Map<UUID, HeritageShare> shares = HeritageFlow.calculate(List.of(maria, ana, rui, celina, joao));

		assertThat(shares.get(rui.id())).hasToString("1/3");
		assertThat(shares.get(celina.id())).hasToString("1/6");
		assertThat(shares.get(joao.id())).hasToString("1/6");
	}

	@Test
	void deceasedHeirWithoutChildrenKeepsTheirShare() {
		Heir maria = heir("Maria", "1/3", true, null);

		assertThat(HeritageFlow.calculate(List.of(maria)).get(maria.id())).hasToString("1/3");
	}

	@Test
	void childWhoseParentIsMissingIsTreatedAsTopLevel() {
		Heir maria = heir("Maria", "1/3", true, null);
		Heir ana = heir("Ana", null, false, maria);

		assertThat(HeritageFlow.calculate(List.of(ana)).get(ana.id())).hasToString("1/1");
	}

	@Test
	void withoutEnteredSharesTheHeritageIsDividedEqually() {
		Heir maria = heir("Maria", null, false, null);
		Heir joao = heir("João", null, false, null);
		Heir rui = heir("Rui", null, false, null);

		Map<UUID, HeritageShare> shares = HeritageFlow.calculate(List.of(maria, joao, rui));

		assertThat(shares.values()).allSatisfy((share) -> assertThat(share).hasToString("1/3"));
	}

	@Test
	void whatEnteredSharesLeaveIsDividedAmongTheOthers() {
		Heir maria = heir("Maria", "1/2", true, null);
		Heir joao = heir("João", null, false, null);
		Heir rui = heir("Rui", null, false, null);
		Heir ana = heir("Ana", null, false, maria);

		Map<UUID, HeritageShare> shares = HeritageFlow.calculate(List.of(maria, joao, rui, ana));

		assertThat(shares.get(joao.id())).hasToString("1/4");
		assertThat(shares.get(rui.id())).hasToString("1/4");
		assertThat(shares.get(ana.id())).hasToString("1/2");
	}

	@Test
	void nothingIsLeftWhenEnteredSharesTakeEverything() {
		Heir maria = heir("Maria", "2/3", false, null);
		Heir joao = heir("João", "1/2", false, null);
		Heir rui = heir("Rui", null, false, null);

		assertThat(HeritageFlow.calculate(List.of(maria, joao, rui))).doesNotContainKey(rui.id());
	}

	private static Heir heir(String name, @Nullable String share, boolean deceased, @Nullable Heir parent) {
		return new Heir(null, name, null, deceased, null, null, null, null,
				(share != null) ? HeritageShare.parse(share) : null, null, (parent != null) ? parent.id() : null, null,
				null);
	}

}
