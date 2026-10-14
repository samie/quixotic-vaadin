package org.vaadin.example.vaadin_quixotic.views;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.core.task.AsyncTaskExecutor;
import org.vaadin.example.vaadin_quixotic.ai.EmbeddingModelService;
import org.vaadin.example.vaadin_quixotic.ui.ModelStatusBanner;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("embed")
@PageTitle("Embed · Quixotic AI")
public class EmbedView extends ExampleView {

	private static final Logger log = LoggerFactory.getLogger(EmbedView.class);

	public record Scored(String text, double similarity) {
	}

	public EmbedView(EmbeddingModelService embeddingService, AsyncTaskExecutor inferenceExecutor) {
		super("Embed", "Rank candidate texts by semantic similarity to a query using local embeddings.");

		if (!embeddingService.isConfigured()) {
			add(missingModelNotice("app.embedding.model"));
			return;
		}

		TextField query = new TextField("Query");
		query.setValue("How do I run AI locally on the JVM?");
		query.setWidthFull();

		TextArea candidates = new TextArea("Candidates (one per line)");
		candidates.setValue(String.join("\n",
				"Quixotic runs AI inference in pure Java, no Python required.",
				"The best pizza in Naples is made in a wood-fired oven.",
				"Spring AI exposes a unified API over many model providers.",
				"Cats sleep for most of the day.",
				"GraalVM can compile Java to fast native binaries."));
		candidates.setWidthFull();
		candidates.setMinHeight("160px");

		Grid<Scored> grid = new Grid<>(Scored.class, false);
		grid.addColumn(s -> String.format("%.4f", s.similarity())).setHeader("Cosine similarity").setFlexGrow(0).setWidth("170px");
		grid.addColumn(Scored::text).setHeader("Text");
		grid.setVisible(false);

		ProgressBar progress = new ProgressBar();
		progress.setIndeterminate(true);
		progress.setVisible(false);

		Button embed = new Button("Embed & rank", e -> {
			List<String> texts = candidates.getValue().lines()
					.map(String::trim).filter(s -> !s.isEmpty()).toList();
			if (query.getValue().isBlank() || texts.isEmpty()) {
				return;
			}
			String q = query.getValue();
			grid.setVisible(false);
			EmbeddingModel model = embeddingService.getModel();
			long startNanos = System.nanoTime();
			runAsync(inferenceExecutor, progress,
					() -> rank(model, q, texts),
					ranked -> {
						logElapsed(log, "Embed (" + (texts.size() + 1) + " texts)", startNanos);
						grid.setItems(ranked);
						grid.setVisible(true);
					},
					err -> {
						grid.setItems(new Scored("Error: " + err, 0));
						grid.setVisible(true);
					});
		});
		embed.setEnabled(false);

		embeddingService.startLoading();
		ModelStatusBanner banner = new ModelStatusBanner(embeddingService).onReadyRun(() -> embed.setEnabled(true));

		add(query, candidates, embed, banner, progress, grid);
	}

	private static List<Scored> rank(EmbeddingModel model, String query, List<String> texts) {
		List<Scored> ranked = new ArrayList<>();
		float[] qv = model.embed(query);
		for (String t : texts) {
			ranked.add(new Scored(t, cosineSimilarity(qv, model.embed(t))));
		}
		ranked.sort(Comparator.comparingDouble(Scored::similarity).reversed());
		return ranked;
	}

	static double cosineSimilarity(float[] a, float[] b) {
		int n = Math.min(a.length, b.length);
		double dot = 0, na = 0, nb = 0;
		for (int i = 0; i < n; i++) {
			dot += a[i] * b[i];
			na += a[i] * a[i];
			nb += b[i] * b[i];
		}
		if (na == 0 || nb == 0) {
			return 0;
		}
		return dot / (Math.sqrt(na) * Math.sqrt(nb));
	}
}
