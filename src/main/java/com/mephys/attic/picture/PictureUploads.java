package com.mephys.attic.picture;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/**
 * Turns picture uploads into {@link Picture}s and pictures into responses, the same way for
 * every endpoint that has a picture.
 */
@Component
public class PictureUploads {

	private final DataSize maxSize;

	PictureUploads(@Value("${attic.picture.max-size:10MB}") DataSize maxSize) {
		this.maxSize = maxSize;
	}

	/**
	 * @throws PictureTooLargeException if the upload exceeds {@code attic.picture.max-size}
	 * @throws IllegalArgumentException if the content type is not a supported image type
	 */
	public Picture read(String contentType, byte[] data) {
		if (data.length > maxSize.toBytes()) {
			throw new PictureTooLargeException(maxSize);
		}
		// Drop parameters such as "; charset=..."
		return new Picture("image/" + MediaType.parseMediaType(contentType).getSubtype(), data);
	}

	public static ResponseEntity<byte[]> pictureResponse(Optional<Picture> picture) {
		return picture
			.map((p) -> ResponseEntity.ok().contentType(MediaType.parseMediaType(p.contentType())).body(p.data()))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	public static ResponseEntity<byte[]> thumbnailResponse(Optional<byte[]> thumbnail) {
		return thumbnail.map((t) -> ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(t))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

}
