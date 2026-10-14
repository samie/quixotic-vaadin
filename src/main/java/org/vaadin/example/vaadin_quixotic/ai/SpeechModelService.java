package org.vaadin.example.vaadin_quixotic.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.qxotic.jinfer.spring.ai.JinferSpeechModel;

@Service
public class SpeechModelService extends AbstractModelService<JinferSpeechModel> {

	private final String lexiconRef;

	public SpeechModelService(
			@Value("${app.speech.model:}") String modelRef,
			@Value("${app.speech.lexicon:}") String lexiconRef) {
		super(modelRef);
		this.lexiconRef = lexiconRef;
	}

	@Override
	protected JinferSpeechModel buildModel() {
		var builder = JinferSpeechModel.builder().model(modelRef());
		if (StringUtils.hasText(lexiconRef)) {
			builder.companion("lexicon", lexiconRef);
		}
		return builder.build();
	}
}
