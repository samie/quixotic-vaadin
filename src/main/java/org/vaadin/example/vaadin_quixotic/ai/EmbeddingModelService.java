package org.vaadin.example.vaadin_quixotic.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.qxotic.jinfer.spring.ai.JinferEmbeddingModel;

@Service
public class EmbeddingModelService extends AbstractModelService<JinferEmbeddingModel> {

	private final int contextCapacity;

	public EmbeddingModelService(
			@Value("${app.embedding.model:}") String modelRef,
			@Value("${app.embedding.context-capacity:2048}") int contextCapacity) {
		super(modelRef);
		this.contextCapacity = contextCapacity;
	}

	@Override
	protected JinferEmbeddingModel buildModel() {
		return JinferEmbeddingModel.builder()
				.model(modelRef())
				.contextCapacity(contextCapacity)
				.build();
	}

	@Override
	protected void warmUp(JinferEmbeddingModel model) {
		model.embed("warm up");
	}
}
