package org.vaadin.example.vaadin_quixotic.views;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;

import org.slf4j.Logger;
import org.springframework.ai.chat.model.ChatResponse;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.theme.lumo.LumoUtility;

abstract class ExampleView extends VerticalLayout {

	ExampleView(String title, String description) {
		setSizeFull();
		setPadding(true);
		setSpacing(true);

		H2 heading = new H2(title);
		heading.addClassNames(LumoUtility.Margin.Bottom.NONE);
		Paragraph desc = new Paragraph(description);
		desc.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.TextColor.SECONDARY);
		add(heading, desc);
	}

	static Component missingModelNotice(String propertyKey) {
		Span icon = new Span(VaadinIcon.INFO_CIRCLE.create());
		Span text = new Span("This capability is not configured. Set " + propertyKey
				+ " in src/main/resources/application.properties (a local model downloads on first run), then restart.");
		HorizontalLayout notice = new HorizontalLayout(icon, text);
		notice.setAlignItems(Alignment.CENTER);
		notice.setWidthFull();
		notice.addClassNames(LumoUtility.Padding.MEDIUM, LumoUtility.BorderRadius.MEDIUM,
				LumoUtility.Background.CONTRAST_5, LumoUtility.TextColor.SECONDARY);
		return notice;
	}

	static <R> void runAsync(Executor executor, ProgressBar bar, Supplier<R> work,
			Consumer<R> onResult, Consumer<String> onError) {
		UI ui = UI.getCurrent();
		bar.setVisible(true);
		executor.execute(() -> {
			try {
				R result = work.get();
				ui.access(() -> {
					bar.setVisible(false);
					onResult.accept(result);
				});
			} catch (Exception ex) {
				ui.access(() -> {
					bar.setVisible(false);
					onError.accept(ex.getMessage());
				});
			}
		});
	}

	static void logElapsed(Logger log, String label, long startNanos) {
		log.info("{} inference: {} ms", label, (System.nanoTime() - startNanos) / 1_000_000);
	}

	static void logInference(Logger log, String label, long startNanos, long firstTokenNanos, ChatResponse last) {
		long endNanos = System.nanoTime();
		StringBuilder sb = new StringBuilder(label).append(" inference: ")
				.append((endNanos - startNanos) / 1_000_000).append(" ms");
		if (firstTokenNanos > 0) {
			sb.append(", ttft ").append((firstTokenNanos - startNanos) / 1_000_000).append(" ms");
		}
		Integer in = null;
		Integer out = null;
		if (last != null && last.getMetadata() != null && last.getMetadata().getUsage() != null) {
			var usage = last.getMetadata().getUsage();
			in = usage.getPromptTokens();
			out = usage.getCompletionTokens();
		}
		if (out != null && out > 0) {
			sb.append(", ").append(out).append(" out tok");
			double genSeconds = (endNanos - (firstTokenNanos > 0 ? firstTokenNanos : startNanos)) / 1e9;
			if (genSeconds > 0) {
				sb.append(String.format(" @ %.1f tok/s", out / genSeconds));
			}
		}
		if (in != null && in > 0) {
			sb.append(", ").append(in).append(" in tok");
		}
		log.info(sb.toString());
	}

	static String describeAudio(byte[] data) {
		String length = formatAudioLength(data);
		return (length != null ? length + " audio, " : "") + data.length + " bytes";
	}

	private static String formatAudioLength(byte[] data) {
		double seconds = audioLengthSeconds(data);
		if (Double.isNaN(seconds) || seconds <= 0) {
			return null;
		}
		if (seconds < 60) {
			return String.format("%.1fs", seconds);
		}
		long minutes = (long) (seconds / 60);
		return String.format("%dm %02.0fs", minutes, seconds - minutes * 60);
	}

	private static double audioLengthSeconds(byte[] data) {
		try {
			AudioFileFormat format = AudioSystem.getAudioFileFormat(new ByteArrayInputStream(data));
			long frames = format.getFrameLength();
			float frameRate = format.getFormat().getFrameRate();
			if (frames != AudioSystem.NOT_SPECIFIED && frameRate > 0) {
				return frames / frameRate;
			}
		} catch (Exception ignored) {
		}
		return wavDurationSeconds(data);
	}

	private static double wavDurationSeconds(byte[] d) {
		try {
			if (d.length < 44 || d[0] != 'R' || d[1] != 'I' || d[2] != 'F' || d[3] != 'F'
					|| d[8] != 'W' || d[9] != 'A' || d[10] != 'V' || d[11] != 'E') {
				return Double.NaN;
			}
			ByteBuffer bb = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
			int pos = 12;
			int byteRate = 0;
			while (pos + 8 <= d.length) {
				int size = bb.getInt(pos + 4);
				if (size < 0) {
					break;
				}
				if (d[pos] == 'f' && d[pos + 1] == 'm' && d[pos + 2] == 't' && d[pos + 3] == ' ') {
					byteRate = bb.getInt(pos + 16); // fmt: audioFormat(2) channels(2) sampleRate(4) byteRate(4)
				} else if (d[pos] == 'd' && d[pos + 1] == 'a' && d[pos + 2] == 't' && d[pos + 3] == 'a'
						&& byteRate > 0) {
					return (double) size / byteRate;
				}
				pos += 8 + size + (size & 1); // chunks are word-aligned
			}
		} catch (Exception ignored) {
		}
		return Double.NaN;
	}
}
