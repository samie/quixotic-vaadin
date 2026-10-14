package org.vaadin.example.vaadin_quixotic.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import com.qxotic.jinfer.hub.HuggingFaceSource;
import com.qxotic.jinfer.hub.ModelRef;
import com.qxotic.jinfer.hub.ModelSource;
import com.qxotic.jinfer.hub.ModelStore;
import com.qxotic.jinfer.hub.RemoteFile;

final class ModelDownloadProgress {

	private ModelDownloadProgress() {
	}

	static long totalBytes(String modelRef) {
		try {
			ModelRef ref = ModelRef.parse(modelRef);
			ModelSource source = new HuggingFaceSource();
			if (!source.supports(ref)) {
				return 0;
			}
			long sum = 0;
			for (Object file : source.list(ref, null)) {
				if (file instanceof RemoteFile rf) {
					sum += rf.sizeBytes();
				}
			}
			return sum;
		} catch (Throwable ignored) {
			return 0;
		}
	}

	static Path downloadDir(ModelStore store, String modelRef) {
		try {
			Path p = store.resolve(modelRef);
			if (p == null) {
				return null;
			}
			return Files.isDirectory(p) ? p : p.getParent();
		} catch (Throwable ignored) {
			return null;
		}
	}

	static long bytesIn(Path dir) {
		if (dir == null) {
			return 0;
		}
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(Files::isRegularFile).mapToLong(ModelDownloadProgress::sizeOf).sum();
		} catch (Exception ignored) {
			return 0;
		}
	}

	private static long sizeOf(Path p) {
		try {
			return Files.size(p);
		} catch (IOException e) {
			return 0;
		}
	}

	static String describe(long downloaded, long total, double bytesPerSecond) {
		int pct = total > 0 ? (int) Math.min(100, Math.round(100.0 * downloaded / total)) : 0;
		StringBuilder sb = new StringBuilder()
				.append(formatBytes(downloaded)).append(" / ").append(formatBytes(total))
				.append(" · ").append(pct).append('%');
		if (bytesPerSecond > 0) {
			sb.append(" · ").append(formatRate(bytesPerSecond));
			long remaining = Math.max(0, total - downloaded);
			sb.append(" · ").append(formatEta(Math.round(remaining / bytesPerSecond))).append(" left");
		}
		return sb.toString();
	}

	static String formatBytes(long bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		}
		String[] units = {"KB", "MB", "GB", "TB"};
		double value = bytes;
		int i = -1;
		do {
			value /= 1024.0;
			i++;
		} while (value >= 1024 && i < units.length - 1);
		return String.format(value >= 100 ? "%.0f %s" : "%.1f %s", value, units[i]);
	}

	static String formatRate(double bytesPerSecond) {
		return formatBytes(Math.round(bytesPerSecond)) + "/s";
	}

	static String formatEta(long seconds) {
		if (seconds < 60) {
			return "~" + seconds + "s";
		}
		long m = seconds / 60;
		long s = seconds % 60;
		return "~" + m + "m " + s + "s";
	}
}
