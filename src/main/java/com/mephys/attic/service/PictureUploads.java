package com.mephys.attic.service;

import com.mephys.attic.model.Picture;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/**
 * Turns picture uploads into {@link Picture}s and pictures into responses, the same way for
 * every endpoint that has a picture. Documents may have a PDF instead of a picture.
 */
@Component
public class PictureUploads {

	/** Every PDF starts with this */
	private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

	private final DataSize maxSize;

	public PictureUploads(@Value("${attic.picture.max-size:10MB}") DataSize maxSize) {
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

	/**
	 * Like {@link #read}, but also accepts a PDF, such as a scanned document.
	 * @throws IllegalArgumentException also if a PDF does not look like one
	 */
	public Picture readDocumentFile(String contentType, byte[] data) {
		if (!MediaType.APPLICATION_PDF.equalsTypeAndSubtype(MediaType.parseMediaType(contentType))) {
			return read(contentType, data);
		}
		if (data.length > maxSize.toBytes()) {
			throw new PictureTooLargeException(maxSize);
		}
		if (!startsWith(data, PDF_SIGNATURE)) {
			throw new IllegalArgumentException("file is not a PDF");
		}
		return new Picture(Picture.PDF, data);
	}

	private static boolean startsWith(byte[] data, byte[] prefix) {
		return data.length >= prefix.length && Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
	}

	/**
	 * The picture, shown in the browser; a PDF opens in its viewer and is saved as {@code <name>.pdf}.
	 */
	public static ResponseEntity<byte[]> pictureResponse(Optional<Picture> picture, String name) {
		return picture.map((p) -> ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(p.contentType()))
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.inline()
						.filename(name + "." + p.fileExtension(), StandardCharsets.UTF_8)
						.build()
						.toString())
			.body(p.data())).orElseGet(() -> ResponseEntity.notFound().build());
	}

	public static ResponseEntity<byte[]> thumbnailResponse(Optional<byte[]> thumbnail) {
		return thumbnail.map((t) -> ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(t))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

}
