package org.vaadin.example.vaadin_quixotic.ai;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.qxotic.jinfer.RuntimeFlags;
import com.qxotic.jinfer.RuntimeState;
import com.qxotic.jinfer.Segments;
import com.qxotic.jinfer.TranscriptionModel;
import com.qxotic.jinfer.TranscriptionStream;
import com.qxotic.jinfer.chat.Models;
import com.qxotic.jinfer.hub.ModelStore;

import org.vaadin.example.vaadin_quixotic.ai.LiveTranscriptionService.LiveTranscriber;

@Service
public class LiveTranscriptionService extends AbstractModelService<LiveTranscriber> {

	private static final Logger log = LoggerFactory.getLogger(LiveTranscriptionService.class);

	private static final String CHUNK_SECONDS_PROPERTY = "jinfer.parakeet.chunkSeconds";

	private static final int SAMPLE_RATE = 16_000;
	private static final int WARMUP_SAMPLES = SAMPLE_RATE / 4;

	public record LiveTranscriber(TranscriptionModel model, Arena arena) implements AutoCloseable {
		@Override
		public void close() {
			arena.close();
		}
	}

	public static final class LiveSession implements AutoCloseable {

		private final TranscriptionStream stream;
		private final RuntimeState state;
		private final StringBuilder finalized = new StringBuilder();
		private String partial = "";

		LiveSession(TranscriptionStream stream, RuntimeState state) {
			this.stream = stream;
			this.state = state;
		}

		public String feed(byte[] pcm) {
			float[] samples = decodePcm16(pcm);
			String piece = stream.feed(samples, 0, samples.length).text();
			if (!piece.isEmpty()) {
				finalized.append(piece);
				partial = "";
			}
			return finalized + partial;
		}

		public String pause() {
			partial = stream.partial().text();
			return finalized + partial;
		}

		public String finish() {
			String rest = stream.finish().text();
			if (!rest.isEmpty()) {
				finalized.append(rest);
			}
			partial = "";
			return finalized.toString();
		}

		@Override
		public void close() {
			try {
				stream.close();
			} finally {
				state.close();
			}
		}

		private static float[] decodePcm16(byte[] pcm) {
			int n = pcm.length / 2;
			float[] samples = new float[n];
			ByteBuffer bb = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
			for (int i = 0; i < n; i++) {
				samples[i] = bb.getShort(i * 2) / 32768f;
			}
			return samples;
		}
	}

	public LiveTranscriptionService(
			@Value("${app.transcription.model:}") String modelRef,
			@Value("${app.transcription.chunk-seconds:}") String chunkSeconds) {
		super(modelRef);
		if (StringUtils.hasText(chunkSeconds)) {
			System.setProperty(CHUNK_SECONDS_PROPERTY, chunkSeconds.trim());
		}
	}

	@Override
	protected LiveTranscriber buildModel() throws IOException {
		Arena arena = Arena.ofShared();
		Path gguf = ModelStore.standard().resolve(modelRef());
		LiveTranscriber live = new LiveTranscriber(Models.loadTranscription(gguf, arena), arena);
		log.info("Live transcription runtime: jinfer.threads={}, vectorApi={}, fastVectorJit={}, chunkSeconds={}",
				RuntimeFlags.THREADS, Segments.USE_VECTOR_API, Segments.FAST_VECTOR_JIT,
				System.getProperty(CHUNK_SECONDS_PROPERTY, "default(2)"));
		return live;
	}

	@Override
	protected void warmUp(LiveTranscriber live) {
		try (LiveSession session = newSession(live)) {
			session.feed(new byte[WARMUP_SAMPLES * 2]);
			session.finish();
		}
	}

	public LiveSession openSession() {
		return newSession(getModel());
	}

	private static LiveSession newSession(LiveTranscriber live) {
		RuntimeState state = live.model().newState();
		return new LiveSession(live.model().stream(state), state);
	}
}
