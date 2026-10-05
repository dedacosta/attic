package com.mephys.attic.support;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

public final class TestImages {

	private TestImages() {
	}

	public static byte[] png(int width, int height) {
		return encode(width, height, BufferedImage.TYPE_INT_ARGB, "png");
	}

	public static byte[] jpeg(int width, int height) {
		return encode(width, height, BufferedImage.TYPE_INT_RGB, "jpeg");
	}

	/**
	 * Insert an EXIF block with the given orientation, as phone cameras do: the pixels stay
	 * as they are and the orientation says how to turn them for display.
	 */
	public static byte[] withExifOrientation(byte[] jpeg, int orientation) {
		ByteArrayOutputStream exif = new ByteArrayOutputStream();
		exif.writeBytes(new byte[] { (byte) 0xFF, (byte) 0xE1, 0, 34 });
		exif.writeBytes("Exif\0\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
		exif.writeBytes(new byte[] { 'M', 'M', 0, 42, 0, 0, 0, 8 });
		exif.writeBytes(new byte[] { 0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0 });
		exif.writeBytes(new byte[] { 0, 0, 0, 0 });
		ByteArrayOutputStream result = new ByteArrayOutputStream();
		result.write(jpeg, 0, 2);
		result.writeBytes(exif.toByteArray());
		result.write(jpeg, 2, jpeg.length - 2);
		return result.toByteArray();
	}

	private static byte[] encode(int width, int height, int type, String format) {
		BufferedImage image = new BufferedImage(width, height, type);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(Color.RED);
		graphics.fillRect(0, 0, width / 2, height);
		graphics.dispose();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try {
			ImageIO.write(image, format, bytes);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return bytes.toByteArray();
	}

}
