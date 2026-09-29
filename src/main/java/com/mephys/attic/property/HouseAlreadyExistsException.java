package com.mephys.attic.property;

/**
 * There is only one family house; a second one is refused.
 */
public class HouseAlreadyExistsException extends RuntimeException {

	HouseAlreadyExistsException() {
		super("the house already exists");
	}

}
