package org.vaadin.example.vaadin_quixotic.views;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.vaadin.example.vaadin_quixotic.ai.VisionService;
import org.vaadin.example.vaadin_quixotic.ui.ModelStatusBanner;

import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.theme.lumo.LumoUtility;

import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

@Route("vision")
@PageTitle("Vision · Quixotic AI")
public class VisionView extends ExampleView {

	private static final Logger log = LoggerFactory.getLogger(VisionView.class);

	private static final int MAX_IMAGE_DIM = 768;

	private final transient VisionService visionService;
	private byte[] imageBytes;
	private MimeType imageMimeType;
	private transient Disposable currentStream;

	public VisionView(VisionService visionService) {
		super("Vision", "Upload an image and ask the model to describe or reason about it.");
		this.visionService = visionService;

		if (!visionService.isConfigured()) {
			add(missingModelNotice("app.vision.model"));
			return;
		}

		Image preview = new Image();
		preview.setMaxHeight("240px");
		preview.setVisible(false);

		Upload upload = new Upload(UploadHandler.inMemory((metadata, data) -> {
			this.imageBytes = data;
			this.imageMimeType = parseMime(metadata.contentType());
			String dataUri = "data:" + this.imageMimeType + ";base64,"
					+ Base64.getEncoder().encodeToString(data);
			getUI().ifPresent(ui -> ui.access(() -> {
				preview.setSrc(dataUri);
				preview.setVisible(true);
			}));
		}));
		upload.setAcceptedFileTypes("image/png", "image/jpeg", "image/webp", "image/gif");
		upload.setMaxFiles(1);

		TextField prompt = new TextField("Prompt");
		prompt.setValue("Describe this image in detail.");
		prompt.setWidthFull();

		ProgressBar progress = new ProgressBar();
		progress.setIndeterminate(true);
		progress.setVisible(false);

		Paragraph answer = new Paragraph();
		answer.addClassNames(LumoUtility.Whitespace.PRE_WRAP);

		Button analyze = new Button("Analyze");
		analyze.setEnabled(false);
		analyze.addClickListener(e -> {
			if (imageBytes == null) {
				answer.setText("Please upload an image first.");
				return;
			}
			analyze(analyze, progress, answer, prompt.getValue());
		});

		visionService.startLoading();
		ModelStatusBanner banner = new ModelStatusBanner(visionService).onReadyRun(() -> analyze.setEnabled(true));

		VerticalLayout panel = new VerticalLayout(upload, preview, prompt, analyze, banner, progress, answer);
		panel.setPadding(false);
		addAndExpand(panel);
	}

	private void analyze(Button analyze, ProgressBar progress, Paragraph answer, String question) {
		if (currentStream != null) {
			currentStream.dispose();
		}
		UI ui = UI.getCurrent();
		byte[] bytes = downscale(imageBytes, MAX_IMAGE_DIM);
		MimeType mime = bytes == imageBytes ? imageMimeType : MimeTypeUtils.IMAGE_JPEG;
		analyze.setEnabled(false);
		answer.setText("");
		progress.setVisible(true);
		StringBuilder sb = new StringBuilder();

		long startNanos = System.nanoTime();
		AtomicLong firstTokenNanos = new AtomicLong();
		AtomicReference<ChatResponse> lastResponse = new AtomicReference<>();

		currentStream = visionService.stream(bytes, mime, question)
				.subscribeOn(Schedulers.boundedElastic())
				.subscribe(
						chunk -> {
							lastResponse.set(chunk);
							String delta = textOf(chunk);
							if (!delta.isEmpty()) {
								firstTokenNanos.compareAndSet(0, System.nanoTime());
								sb.append(delta);
								ui.access(() -> {
									progress.setVisible(false);
									answer.setText(sb.toString());
								});
							}
						},
						error -> ui.access(() -> {
							progress.setVisible(false);
							answer.setText("Error: " + error.getMessage());
							analyze.setEnabled(true);
						}),
						() -> ui.access(() -> {
							progress.setVisible(false);
							logInference(log, "Vision", startNanos, firstTokenNanos.get(), lastResponse.get());
							analyze.setEnabled(true);
						}));
	}

	@Override
	protected void onDetach(DetachEvent detachEvent) {
		if (currentStream != null) {
			currentStream.dispose();
		}
		super.onDetach(detachEvent);
	}

	private static byte[] downscale(byte[] data, int maxDim) {
		try {
			BufferedImage src = ImageIO.read(new ByteArrayInputStream(data));
			if (src == null) {
				return data;
			}
			int longest = Math.max(src.getWidth(), src.getHeight());
			if (longest <= maxDim) {
				return data;
			}
			double scale = (double) maxDim / longest;
			int nw = Math.max(1, (int) Math.round(src.getWidth() * scale));
			int nh = Math.max(1, (int) Math.round(src.getHeight() * scale));
			BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = dst.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(src, 0, 0, nw, nh, null);
			g.dispose();
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(dst, "jpg", out);
			return out.toByteArray();
		} catch (Exception e) {
			return data;
		}
	}

	private static String textOf(ChatResponse chunk) {
		if (chunk == null || chunk.getResult() == null || chunk.getResult().getOutput() == null) {
			return "";
		}
		String text = chunk.getResult().getOutput().getText();
		return text == null ? "" : text;
	}

	private static MimeType parseMime(String contentType) {
		try {
			return MimeTypeUtils.parseMimeType(contentType);
		} catch (Exception e) {
			return MimeTypeUtils.IMAGE_PNG;
		}
	}
}
