package com.mephys.attic.picture;

import org.springframework.util.unit.DataSize;

public class PictureTooLargeException extends RuntimeException {

	PictureTooLargeException(DataSize maxSize) {
		super("The picture is larger than " + maxSize.toMegabytes() + " MB");
	}

}
