package com.mephys.attic.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * Creates small JPEG thumbnails of pictures. Only formats that the JDK can decode
 * (JPEG, PNG, GIF, BMP) are supported. The EXIF orientation that phone cameras write is
 * applied, so portrait photos are not shown sideways.
 */
public final class Thumbnails {

	public static final int MAX_SIZE = 256;

	private static final long MAX_SOURCE_PIXELS = 100_000_000L;

	private static final float JPEG_QUALITY = 0.8f;

	private Thumbnails() {
	}

	/**
	 * Create a JPEG thumbnail that fits within {@link #MAX_SIZE} pixels.
	 * @return the thumbnail, or empty if the image cannot be decoded
	 */
	public static Optional<byte[]> create(byte[] image) {
		try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(image))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			if (!readers.hasNext()) {
				return Optional.empty();
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(in, true, true);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if ((long) width * height > MAX_SOURCE_PIXELS) {
					return Optional.empty();
				}
				// Skip pixels while decoding so large photos never load at full size
				ImageReadParam param = reader.getDefaultReadParam();
				int step = Math.max(1, Math.max(width, height) / (MAX_SIZE * 2));
				param.setSourceSubsampling(step, step, 0, 0);
				return Optional.of(toJpeg(orient(scale(reader.read(0, param)), exifOrientation(image))));
			}
			finally {
				reader.dispose();
			}
		}
		catch (IOException | RuntimeException ex) {
			// Corrupt or unsupported image: store the picture without a thumbnail
			return Optional.empty();
		}
	}

	private static BufferedImage scale(BufferedImage source) {
		double factor = Math.min(1.0, (double) MAX_SIZE / Math.max(source.getWidth(), source.getHeight()));
		int width = Math.max(1, (int) Math.round(source.getWidth() * factor));
		int height = Math.max(1, (int) Math.round(source.getHeight() * factor));
		BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = target.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			// JPEG has no transparency, so transparent areas become white
			graphics.setColor(Color.WHITE);
			graphics.fillRect(0, 0, width, height);
			graphics.drawImage(source, 0, 0, width, height, null);
		}
		finally {
			graphics.dispose();
		}
		return target;
	}

	/**
	 * Turn the image upright according to its EXIF orientation (1 to 8).
	 */
	private static BufferedImage orient(BufferedImage source, int orientation) {
		int w = source.getWidth();
		int h = source.getHeight();
		// AffineTransform(m00, m10, m01, m11, m02, m12): x' = m00 x + m01 y + m02, y' = m10 x + m11 y + m12
		AffineTransform transform = switch (orientation) {
			case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0); // mirror horizontally
			case 3 -> new AffineTransform(-1, 0, 0, -1, w, h); // rotate 180°
			case 4 -> new AffineTransform(1, 0, 0, -1, 0, h); // mirror vertically
			case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0); // transpose
			case 6 -> new AffineTransform(0, 1, -1, 0, h, 0); // rotate 90° clockwise
			case 7 -> new AffineTransform(0, -1, -1, 0, h, w); // transverse
			case 8 -> new AffineTransform(0, -1, 1, 0, 0, w); // rotate 90° counter-clockwise
			default -> null;
		};
		if (transform == null) {
			return source;
		}
		boolean swapsSides = orientation >= 5;
		BufferedImage target = new BufferedImage(swapsSides ? h : w, swapsSides ? w : h, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = target.createGraphics();
		try {
			graphics.drawImage(source, transform, null);
		}
		finally {
			graphics.dispose();
		}
		return target;
	}

	/**
	 * Read the orientation tag from a JPEG's EXIF block.
	 * @return 1 to 8, or 1 (upright) if there is none
	 */
	public static int exifOrientation(byte[] jpeg) {
		try {
			if (u8(jpeg, 0) != 0xFF || u8(jpeg, 1) != 0xD8) {
				return 1;
			}
			int pos = 2;
			while (pos + 4 <= jpeg.length && u8(jpeg, pos) == 0xFF) {
				int marker = u8(jpeg, pos + 1);
				if (marker == 0xDA || marker == 0xD9) {
					// Image data starts: no EXIF block before it
					return 1;
				}
				int length = u16(jpeg, pos + 2, true);
				if (marker == 0xE1 && length >= 8 && isExifHeader(jpeg, pos + 4)) {
					return tiffOrientation(jpeg, pos + 10, Math.min(jpeg.length, pos + 2 + length));
				}
				pos += 2 + length;
			}
		}
		catch (IndexOutOfBoundsException ex) {
			// Truncated or malformed EXIF block
		}
		return 1;
	}

	private static boolean isExifHeader(byte[] data, int pos) {
		return data[pos] == 'E' && data[pos + 1] == 'x' && data[pos + 2] == 'i' && data[pos + 3] == 'f'
				&& data[pos + 4] == 0 && data[pos + 5] == 0;
	}

	private static int tiffOrientation(byte[] data, int tiff, int end) {
		boolean bigEndian = data[tiff] == 'M';
		int ifd = tiff + u32(data, tiff + 4, bigEndian);
		int entries = u16(data, ifd, bigEndian);
		for (int i = 0; i < entries; i++) {
			int entry = ifd + 2 + i * 12;
			if (entry + 12 > end) {
				break;
			}
			if (u16(data, entry, bigEndian) == 0x0112) {
				int orientation = u16(data, entry + 8, bigEndian);
				return (orientation >= 1 && orientation <= 8) ? orientation : 1;
			}
		}
		return 1;
	}

	private static int u8(byte[] data, int pos) {
		return data[pos] & 0xFF;
	}

	private static int u16(byte[] data, int pos, boolean bigEndian) {
		return bigEndian ? (u8(data, pos) << 8) | u8(data, pos + 1) : u8(data, pos) | (u8(data, pos + 1) << 8);
	}

	private static int u32(byte[] data, int pos, boolean bigEndian) {
		return bigEndian ? (u16(data, pos, true) << 16) | u16(data, pos + 2, true)
				: u16(data, pos, false) | (u16(data, pos + 2, false) << 16);
	}

	private static byte[] toJpeg(BufferedImage image) throws IOException {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
			writer.setOutput(out);
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(JPEG_QUALITY);
			writer.write(null, new IIOImage(image, null, null), param);
		}
		finally {
			writer.dispose();
		}
		return bytes.toByteArray();
	}

}
