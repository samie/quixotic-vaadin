package org.vaadin.example.vaadin_quixotic.ai;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.qxotic.jinfer.hub.ModelStore;

import jakarta.annotation.PreDestroy;

public abstract class AbstractModelService<T extends AutoCloseable> {

	private static final Logger log = LoggerFactory.getLogger(AbstractModelService.class);

	private static final long DOWNLOAD_POLL_MS = 500;

	public enum Phase {
		IDLE("Preparing…"),
		CHECKING("Checking for a local copy…"),
		DOWNLOADING("Downloading model (first run — this can take a while)…"),
		LOADING("Loading model into memory…"),
		WARMING("Warming up…"),
		READY("Ready"),
		FAILED("Failed to load model");

		private final String message;

		Phase(String message) {
			this.message = message;
		}

		public String message() {
			return message;
		}
	}

	public record Status(Phase phase, double fraction, String detail) {
		public boolean isDeterminate() {
			return fraction >= 0;
		}
	}

	private final String modelRef;
	private final AtomicBoolean started = new AtomicBoolean(false);
	private final List<Consumer<Status>> listeners = new CopyOnWriteArrayList<>();
	private volatile Phase phase = Phase.IDLE;
	private volatile double fraction = -1;
	private volatile String detail;
	private volatile String failure;
	private volatile T model;

	protected AbstractModelService(String modelRef) {
		this.modelRef = modelRef == null ? "" : modelRef.trim();
	}

	public final String modelRef() {
		return modelRef;
	}

	protected abstract T buildModel() throws Exception;

	protected void warmUp(T model) throws Exception {
	}

	public final boolean isConfigured() {
		return StringUtils.hasText(modelRef);
	}

	public final Phase phase() {
		return phase;
	}

	public final Status status() {
		return new Status(phase, fraction, detail);
	}

	public final void addStatusListener(Consumer<Status> listener) {
		listeners.add(listener);
	}

	public final void removeStatusListener(Consumer<Status> listener) {
		listeners.remove(listener);
	}

	private void setPhase(Phase newPhase) {
		this.phase = newPhase;
		this.fraction = -1;
		this.detail = newPhase == Phase.FAILED ? failure : null;
		fire();
	}

	private void setProgress(double fraction, String detail) {
		this.fraction = fraction;
		this.detail = detail;
		fire();
	}

	private void fire() {
		Status snapshot = new Status(phase, fraction, detail);
		for (Consumer<Status> listener : listeners) {
			try {
				listener.accept(snapshot);
			} catch (RuntimeException ignored) {
			}
		}
	}

	public final String statusMessage() {
		return phase == Phase.FAILED && failure != null ? phase.message() + ": " + failure : phase.message();
	}

	public final T getModel() {
		return model;
	}

	public final void startLoading() {
		if (!isConfigured() || !started.compareAndSet(false, true)) {
			return;
		}
		Thread t = new Thread(this::load, "model-loader-" + getClass().getSimpleName());
		t.setDaemon(true);
		t.start();
	}

	private void load() {
		try {
			setPhase(Phase.CHECKING);
			if (!isCached()) {
				setPhase(Phase.DOWNLOADING);
				downloadWithProgress();
			}
			setPhase(Phase.LOADING);
			long loadStart = System.nanoTime();
			T built = buildModel();
			this.model = built;
			log.info("Loaded model '{}' in {} ms", modelRef, millis(loadStart));
			setPhase(Phase.WARMING);
			warmUpWithElapsed(built);
			setPhase(Phase.READY);
		} catch (Throwable ex) {
			this.failure = ex.getMessage();
			setPhase(Phase.FAILED);
			log.warn("Failed to load model '{}'", modelRef, ex);
		}
	}

	private void downloadWithProgress() {
		try {
			ModelStore store = ModelStore.standard();
			Path dir = ModelDownloadProgress.downloadDir(store, modelRef);

			AtomicLong total = new AtomicLong(0);
			Thread sizer = new Thread(() -> total.set(ModelDownloadProgress.totalBytes(modelRef)),
					"model-size-" + getClass().getSimpleName());
			sizer.setDaemon(true);
			sizer.start();

			AtomicReference<Throwable> error = new AtomicReference<>();
			Thread puller = new Thread(() -> {
				try {
					store.pullAll(List.of(modelRef), false);
				} catch (Throwable t) {
					error.set(t);
				}
			}, "model-download-" + getClass().getSimpleName());
			puller.setDaemon(true);
			puller.start();

			long lastBytes = ModelDownloadProgress.bytesIn(dir);
			long lastNanos = System.nanoTime();
			while (puller.isAlive()) {
				Thread.sleep(DOWNLOAD_POLL_MS);
				if (dir == null) {
					continue;
				}
				long bytes = ModelDownloadProgress.bytesIn(dir);
				long now = System.nanoTime();
				double seconds = (now - lastNanos) / 1e9;
				double rate = seconds > 0 ? Math.max(0, bytes - lastBytes) / seconds : 0;
				lastBytes = bytes;
				lastNanos = now;

				long totalBytes = total.get();
				if (totalBytes > 0) {
					setProgress(Math.min(1.0, (double) bytes / totalBytes),
							ModelDownloadProgress.describe(bytes, totalBytes, rate));
				} else if (bytes > 0) {
					setProgress(-1, ModelDownloadProgress.formatBytes(bytes)
							+ (rate > 0 ? " · " + ModelDownloadProgress.formatRate(rate) : ""));
				}
			}
			puller.join();
			if (error.get() != null) {
				throw error.get();
			}
		} catch (Throwable ignored) {
		}
	}

	private void warmUpWithElapsed(T built) {
		long start = System.nanoTime();
		Thread ticker = new Thread(() -> {
			try {
				while (!Thread.currentThread().isInterrupted()) {
					Thread.sleep(1000);
					setProgress(-1, "first request is slower · " + seconds(start) + "s");
				}
			} catch (InterruptedException ignored) {
			}
		}, "model-warmup-tick-" + getClass().getSimpleName());
		ticker.setDaemon(true);
		ticker.start();
		try {
			warmUp(built);
			log.info("Warmed up model '{}' in {} ms", modelRef, millis(start));
		} catch (Throwable ignored) {
		} finally {
			ticker.interrupt();
		}
	}

	private boolean isCached() {
		try {
			return ModelStore.standard().find(modelRef).isPresent();
		} catch (Throwable ignored) {
			return false;
		}
	}

	@PreDestroy
	void close() {
		T m = model;
		if (m != null) {
			try {
				m.close();
			} catch (Exception ignored) {
			}
		}
	}

	private static long millis(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000;
	}

	private static long seconds(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000_000;
	}
}
