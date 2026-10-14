package org.vaadin.example.vaadin_quixotic.views;

import java.util.Base64;

import org.parttio.vaadinjsloader.JSLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.task.AsyncTaskExecutor;
import org.vaadin.example.vaadin_quixotic.ai.TranscriptionModelService;
import org.vaadin.example.vaadin_quixotic.ui.ModelStatusBanner;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("parakeet")
@PageTitle("Speech to Text · Quixotic AI")
public class ParakeetView extends ExampleView {

	private static final Logger log = LoggerFactory.getLogger(ParakeetView.class);

	private final transient TranscriptionModelService transcriptionService;
	private final transient AsyncTaskExecutor inferenceExecutor;
	private final TextArea transcript = new TextArea("Transcript");
	private final ProgressBar progress = new ProgressBar();

	public ParakeetView(TranscriptionModelService transcriptionService, AsyncTaskExecutor inferenceExecutor) {
		super("Speech to Text", "Record a clip from your microphone and transcribe it with the Parakeet model.");
		this.transcriptionService = transcriptionService;
		this.inferenceExecutor = inferenceExecutor;

		if (!transcriptionService.isConfigured()) {
			add(missingModelNotice("app.transcription.model"));
			return;
		}

		Button record = new Button("Record", VaadinIcon.MICROPHONE.create());
		record.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		record.setEnabled(false);
		Button stop = new Button("Stop", VaadinIcon.STOP.create());
		stop.addThemeVariants(ButtonVariant.LUMO_ERROR);
		stop.setEnabled(false);

		record.addClickListener(e -> {
			getElement().executeJs("window.recorder.start($0, {stream:false})", getElement());
			record.setEnabled(false);
			stop.setEnabled(true);
		});
		stop.addClickListener(e -> {
			getElement().executeJs("window.recorder.stop($0)", getElement());
			stop.setEnabled(false);
			record.setEnabled(true);
			progress.setVisible(true);
		});

		progress.setIndeterminate(true);
		progress.setVisible(false);
		transcript.setWidthFull();
		transcript.setMinHeight("160px");
		transcript.setReadOnly(true);

		transcriptionService.startLoading();
		ModelStatusBanner banner = new ModelStatusBanner(transcriptionService).onReadyRun(() -> record.setEnabled(true));

		add(banner, new HorizontalLayout(record, stop), progress, transcript);
	}

	@Override
	protected void onAttach(AttachEvent attachEvent) {
		super.onAttach(attachEvent);
		if (transcriptionService.isConfigured()) {
			JSLoader.loadJavaResource(attachEvent.getUI(), ParakeetView.class, "recorder", "recorder.js");
		}
	}

	@ClientCallable
	private void onAudioCaptured(String base64Wav) {
		UI ui = UI.getCurrent();
		byte[] wav = Base64.getDecoder().decode(base64Wav);
		inferenceExecutor.execute(() -> {
			long startNanos = System.nanoTime();
			String result;
			try {
				ByteArrayResource resource = new ByteArrayResource(wav) {
					@Override
					public String getFilename() {
						return "microphone.wav";
					}
				};
				result = transcriptionService.getModel().transcribe(resource);
				logElapsed(log, "Transcription (mic, " + describeAudio(wav) + ")", startNanos);
			} catch (Exception ex) {
				result = "Error: " + ex.getMessage();
			}
			String finalResult = result;
			ui.access(() -> {
				progress.setVisible(false);
				transcript.setValue(finalResult);
			});
		});
	}
}
