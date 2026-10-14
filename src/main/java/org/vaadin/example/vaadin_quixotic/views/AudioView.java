package org.vaadin.example.vaadin_quixotic.views;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.vaadin.example.vaadin_quixotic.ai.AudioModelService;
import org.vaadin.example.vaadin_quixotic.ui.ModelStatusBanner;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.UploadHandler;

@Route("audio")
@PageTitle("Audio · Quixotic AI")
public class AudioView extends ExampleView {

	private static final Logger log = LoggerFactory.getLogger(AudioView.class);

	public AudioView(AudioModelService audioService, AsyncTaskExecutor inferenceExecutor) {
		super("Audio", "Upload an audio file and transcribe it to text with a multimodal model.");

		if (!audioService.isConfigured()) {
			add(missingModelNotice("app.audio.model"));
			return;
		}

		TextArea transcript = new TextArea("Transcript");
		transcript.setWidthFull();
		transcript.setMinHeight("160px");
		transcript.setReadOnly(true);

		ProgressBar progress = new ProgressBar();
		progress.setIndeterminate(true);
		progress.setVisible(false);

		Upload upload = new Upload(UploadHandler.inMemory((metadata, data) -> {
			UI ui = getUI().orElse(null);
			if (ui != null) {
				ui.access(() -> progress.setVisible(true));
			}
			MimeType mime = parseMime(metadata.contentType());
			inferenceExecutor.execute(() -> {
				long startNanos = System.nanoTime();
				String result;
				try {
					result = audioService.transcribe(data, mime);
					logElapsed(log, "Transcription (Gemma, " + describeAudio(data) + ")", startNanos);
				} catch (Exception ex) {
					result = "Error: " + ex.getMessage();
				}
				String finalResult = result;
				if (ui != null) {
					ui.access(() -> {
						progress.setVisible(false);
						transcript.setValue(finalResult);
					});
				}
			});
		}));
		upload.setAcceptedFileTypes("audio/wav", "audio/x-wav", "audio/mpeg", "audio/mp3", "audio/flac", "audio/ogg");
		upload.setMaxFiles(1);
		upload.setEnabled(false);

		audioService.startLoading();
		ModelStatusBanner banner = new ModelStatusBanner(audioService).onReadyRun(() -> upload.setEnabled(true));

		add(banner, upload, progress, transcript);
	}

	private static MimeType parseMime(String contentType) {
		try {
			return MimeTypeUtils.parseMimeType(contentType);
		} catch (Exception e) {
			return MimeTypeUtils.parseMimeType("audio/wav");
		}
	}
}
