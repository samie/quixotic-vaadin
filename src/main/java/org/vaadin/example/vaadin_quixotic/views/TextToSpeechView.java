package org.vaadin.example.vaadin_quixotic.views;

import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.AsyncTaskExecutor;
import org.vaadin.example.vaadin_quixotic.ai.SpeechModelService;
import org.vaadin.example.vaadin_quixotic.ui.ModelStatusBanner;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("text-to-speech")
@PageTitle("Text to Speech · Quixotic AI")
public class TextToSpeechView extends ExampleView {

	private static final Logger log = LoggerFactory.getLogger(TextToSpeechView.class);

	public TextToSpeechView(SpeechModelService speechService, AsyncTaskExecutor inferenceExecutor) {
		super("Text to Speech", "Type some text and synthesize it to speech locally.");

		if (!speechService.isConfigured()) {
			add(missingModelNotice("app.speech.model"));
			return;
		}

		TextArea text = new TextArea("Text");
		text.setValue("Local inference, in Java. No server, no Python.");
		text.setWidthFull();
		text.setMinHeight("120px");

		ProgressBar progress = new ProgressBar();
		progress.setIndeterminate(true);
		progress.setVisible(false);

		Element audio = new Element("audio");
		audio.setAttribute("controls", true);
		audio.setVisible(false);

		Anchor download = new Anchor();
		download.getElement().setAttribute("download", "speech.wav");
		download.setText("Download WAV");
		download.setVisible(false);

		Button synthesize = new Button("Synthesize", e -> {
			if (text.getValue().isBlank()) {
				return;
			}
			String input = text.getValue();
			audio.setVisible(false);
			download.setVisible(false);
			long startNanos = System.nanoTime();
			runAsync(inferenceExecutor, progress,
					() -> speechService.getModel().call(input),
					wav -> {
						logElapsed(log, "Text-to-speech (" + input.length() + " chars)", startNanos);
						String dataUri = "data:audio/wav;base64," + Base64.getEncoder().encodeToString(wav);
						audio.setAttribute("src", dataUri);
						audio.setVisible(true);
						// Anchor.setHref rejects data: URIs; this WAV is trusted, so set it unsafely.
						download.setUnsafeHref(dataUri);
						download.setVisible(true);
					},
					err -> add(new com.vaadin.flow.component.html.Paragraph("Error: " + err)));
		});
		synthesize.setEnabled(false);

		speechService.startLoading();
		ModelStatusBanner banner = new ModelStatusBanner(speechService).onReadyRun(() -> synthesize.setEnabled(true));

		add(text, synthesize, banner, progress);
		getElement().appendChild(audio);
		add(download);
	}
}
