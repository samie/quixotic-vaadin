package org.vaadin.example.vaadin_quixotic.views;

import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.parttio.vaadinjsloader.JSLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vaadin.example.vaadin_quixotic.ai.LiveTranscriptionService;
import org.vaadin.example.vaadin_quixotic.ai.LiveTranscriptionService.LiveSession;
import org.vaadin.example.vaadin_quixotic.ui.ModelStatusBanner;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("live")
@PageTitle("Live Transcribe · Quixotic AI")
public class LiveTranscribeView extends ExampleView {

	private static final Logger log = LoggerFactory.getLogger(LiveTranscribeView.class);

	private final transient LiveTranscriptionService service;
	private final TextArea transcript = new TextArea("Transcript");
	private final Button record = new Button("Start", VaadinIcon.MICROPHONE.create());
	private final Button stop = new Button("Stop", VaadinIcon.STOP.create());

	private transient volatile LiveSession session;
	private transient ExecutorService worker;
	private volatile boolean recording;
	private volatile UI ui;

	private String lastPushed;
	private final AtomicInteger queued = new AtomicInteger();

	public LiveTranscribeView(LiveTranscriptionService service) {
		super("Live Transcribe", "Speak into your microphone and watch the transcript appear in real time.");
		this.service = service;

		if (!service.isConfigured()) {
			add(missingModelNotice("app.transcription.model"));
			return;
		}

		record.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		record.setEnabled(false);
		stop.addThemeVariants(ButtonVariant.LUMO_ERROR);
		stop.setEnabled(false);

		record.addClickListener(e -> startRecording());
		stop.addClickListener(e -> stopRecording());

		transcript.setWidthFull();
		transcript.setMinHeight("200px");
		transcript.setReadOnly(true);

		service.startLoading();
		ModelStatusBanner banner = new ModelStatusBanner(service).onReadyRun(() -> record.setEnabled(true));

		add(banner, new HorizontalLayout(record, stop), transcript);
	}

	@Override
	protected void onAttach(AttachEvent attachEvent) {
		super.onAttach(attachEvent);
		this.ui = attachEvent.getUI();
		if (service.isConfigured()) {
			JSLoader.loadJavaResource(attachEvent.getUI(), LiveTranscribeView.class, "recorder", "recorder.js");
		}
	}

	@Override
	protected void onDetach(DetachEvent detachEvent) {
		cleanup();
		super.onDetach(detachEvent);
	}

	private void startRecording() {
		transcript.clear();
		lastPushed = null;
		queued.set(0);
		session = service.openSession();
		worker = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "live-transcribe-feed");
			t.setDaemon(true);
			return t;
		});
		recording = true;
		getElement().executeJs("window.recorder.start($0)", getElement());
		record.setEnabled(false);
		stop.setEnabled(true);
	}

	private void stopRecording() {
		stop.setEnabled(false);
		getElement().executeJs("window.recorder.stop($0)", getElement());
	}

	@ClientCallable
	private void onAudioChunk(String base64Pcm) {
		if (!recording || worker == null) {
			return;
		}
		LiveSession current = session;
		queued.incrementAndGet();
		worker.execute(() -> {
			queued.decrementAndGet();
			try {
				pushText(current.feed(Base64.getDecoder().decode(base64Pcm)));
			} catch (Exception ex) {
				log.warn("Live feed failed", ex);
			}
		});
	}

	@ClientCallable
	private void onSpeechPause() {
		if (!recording || worker == null) {
			return;
		}
		LiveSession current = session;
		queued.incrementAndGet();
		worker.execute(() -> {
			if (queued.decrementAndGet() != 0) {
				return;
			}
			try {
				pushText(current.pause());
			} catch (Exception ex) {
				log.warn("Live partial failed", ex);
			}
		});
	}

	@ClientCallable
	private void onAudioEnd() {
		recording = false;
		ExecutorService w = worker;
		LiveSession current = session;
		if (w == null || current == null) {
			return;
		}
		w.execute(() -> {
			try {
				pushText(current.finish());
			} catch (Exception ex) {
				log.warn("Live finalize failed", ex);
			} finally {
				current.close();
			}
		});
		w.shutdown();
		worker = null;
		session = null;
		UI u = ui;
		if (u != null) {
			u.access(() -> record.setEnabled(true));
		}
	}

	private void pushText(String text) {
		if (text.equals(lastPushed)) {
			return;
		}
		lastPushed = text;
		UI u = ui;
		if (u != null) {
			u.access(() -> transcript.setValue(text));
		}
	}

	private void cleanup() {
		recording = false;
		ExecutorService w = worker;
		if (w != null) {
			w.shutdownNow();
			worker = null;
		}
		LiveSession s = session;
		if (s != null) {
			try {
				s.close();
			} catch (Exception ignored) {
			}
			session = null;
		}
	}
}
