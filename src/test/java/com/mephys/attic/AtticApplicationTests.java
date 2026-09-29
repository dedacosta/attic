package com.mephys.attic;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "attic.database.file=target/test-data/attic.db")
class AtticApplicationTests {

	@Test
	void contextLoads() {
	}

}
