package org.vaadin.example.vaadin_quixotic.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModelDownloadProgressTest {

	@Test
	void formatBytesScalesAndRounds() {
		assertEquals("0 B", ModelDownloadProgress.formatBytes(0));
		assertEquals("512 B", ModelDownloadProgress.formatBytes(512));
		assertEquals("1.0 KB", ModelDownloadProgress.formatBytes(1024));
		assertEquals("1.5 KB", ModelDownloadProgress.formatBytes(1536));
		assertEquals("1.0 MB", ModelDownloadProgress.formatBytes(1024L * 1024));
		assertEquals("150 MB", ModelDownloadProgress.formatBytes(150L * 1024 * 1024));
	}

	@Test
	void formatRateAppendsPerSecond() {
		assertTrue(ModelDownloadProgress.formatRate(1024 * 1024).endsWith("/s"));
		assertEquals("1.0 MB/s", ModelDownloadProgress.formatRate(1024 * 1024));
	}

	@Test
	void formatEtaUsesSecondsThenMinutes() {
		assertEquals("~40s", ModelDownloadProgress.formatEta(40));
		assertEquals("~1m 35s", ModelDownloadProgress.formatEta(95));
	}

	@Test
	void describeShowsPercentTotalAndEta() {
		String s = ModelDownloadProgress.describe(1_200_000_000L, 2_400_000_000L, 18_000_000);
		assertTrue(s.contains("50%"), s);
		assertTrue(s.contains(" / "), s);
		assertTrue(s.contains("/s"), s);
		assertTrue(s.contains("left"), s);
	}

	@Test
	void describeWithoutRateOmitsEta() {
		String s = ModelDownloadProgress.describe(500, 1000, 0);
		assertTrue(s.contains("50%"), s);
		assertTrue(!s.contains("left"), s);
	}
}
