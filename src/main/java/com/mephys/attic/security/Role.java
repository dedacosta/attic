package com.mephys.attic.security;

import java.util.List;

enum Role {

	/** Everything an administrator can, plus deleting accounts, changing roles and managing administrators */
	SUPER_ADMIN,

	/** Edits everything and creates user accounts */
	ADMIN,

	/** Can look at everything but change nothing, apart from their own password */
	USER;

	/**
	 * The Spring Security authorities of this role. A super-administrator also has the
	 * administrator authority, so that everything open to administrators is open to them.
	 */
	List<String> authorities() {
		return switch (this) {
			case SUPER_ADMIN -> List.of("ROLE_SUPER_ADMIN", "ROLE_ADMIN");
			case ADMIN -> List.of("ROLE_ADMIN");
			case USER -> List.of("ROLE_USER");
		};
	}

}
