package com.mephys.attic.service;

import com.mephys.attic.support.TestImages;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThumbnailsTests {

	@Test
	void scalesLargeImageToFitAndKeepsAspectRatio() throws IOException {
		BufferedImage thumbnail = decode(Thumbnails.create(TestImages.jpeg(2000, 1000)).orElseThrow());

		assertThat(thumbnail.getWidth()).isEqualTo(256);
		assertThat(thumbnail.getHeight()).isEqualTo(128);
	}

	@Test
	void doesNotEnlargeSmallImage() throws IOException {
		BufferedImage thumbnail = decode(Thumbnails.create(TestImages.png(40, 30)).orElseThrow());

		assertThat(thumbnail.getWidth()).isEqualTo(40);
		assertThat(thumbnail.getHeight()).isEqualTo(30);
	}

	@Test
	void producesJpegFromTransparentPng() {
		byte[] thumbnail = Thumbnails.create(TestImages.png(500, 500)).orElseThrow();

		assertThat(thumbnail).startsWith((byte) 0xFF, (byte) 0xD8);
	}

	@Test
	void rotatesPhoneLandscapePixelsToPortrait() throws IOException {
		// Stored 400x200 with the left half red; orientation 6 means "turn 90° clockwise"
		byte[] photo = TestImages.withExifOrientation(TestImages.jpeg(400, 200), 6);

		BufferedImage thumbnail = decode(Thumbnails.create(photo).orElseThrow());

		assertThat(thumbnail.getWidth()).isEqualTo(128);
		assertThat(thumbnail.getHeight()).isEqualTo(256);
		assertThat(isRed(thumbnail.getRGB(64, 20))).as("top is red").isTrue();
		assertThat(isRed(thumbnail.getRGB(64, 236))).as("bottom is not red").isFalse();
	}

	@Test
	void rotatesCounterClockwiseForOrientation8() throws IOException {
		BufferedImage thumbnail = decode(
				Thumbnails.create(TestImages.withExifOrientation(TestImages.jpeg(400, 200), 8)).orElseThrow());

		assertThat(thumbnail.getWidth()).isEqualTo(128);
		assertThat(isRed(thumbnail.getRGB(64, 236))).as("bottom is red").isTrue();
		assertThat(isRed(thumbnail.getRGB(64, 20))).as("top is not red").isFalse();
	}

	@Test
	void keepsImageWithoutExifUnrotated() throws IOException {
		BufferedImage thumbnail = decode(Thumbnails.create(TestImages.jpeg(400, 200)).orElseThrow());

		assertThat(thumbnail.getWidth()).isEqualTo(256);
		assertThat(isRed(thumbnail.getRGB(20, 64))).as("left is red").isTrue();
	}

	@Test
	void returnsEmptyForUndecodableData() {
		assertThat(Thumbnails.create(new byte[] { 1, 2, 3 })).isEmpty();
	}

	private static boolean isRed(int rgb) {
		int red = (rgb >> 16) & 0xFF;
		int green = (rgb >> 8) & 0xFF;
		return red > 200 && green < 60;
	}

	private static BufferedImage decode(byte[] jpeg) throws IOException {
		return ImageIO.read(new ByteArrayInputStream(jpeg));
	}

}
