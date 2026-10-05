package com.mephys.attic.service;

import org.springframework.util.unit.DataSize;

public class PictureTooLargeException extends RuntimeException {

	public PictureTooLargeException(DataSize maxSize) {
		super("The picture is larger than " + maxSize.toMegabytes() + " MB");
	}

}
