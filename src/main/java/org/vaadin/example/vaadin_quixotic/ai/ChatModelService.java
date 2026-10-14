package org.vaadin.example.vaadin_quixotic.ai;

import java.util.List;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.qxotic.jinfer.spring.ai.JinferChatModel;
import com.qxotic.jinfer.spring.ai.JinferChatOptions;

@Service
public class ChatModelService extends AbstractModelService<JinferChatModel> {

	private final int contextCapacity;
	private final int maxTokens;
	private final double temperature;

	public ChatModelService(
			@Value("${app.chat.model:}") String modelRef,
			@Value("${app.chat.context-capacity:4096}") int contextCapacity,
			@Value("${app.chat.max-tokens:512}") int maxTokens,
			@Value("${app.chat.temperature:0.7}") double temperature) {
		super(modelRef);
		this.contextCapacity = contextCapacity;
		this.maxTokens = maxTokens;
		this.temperature = temperature;
	}

	@Override
	protected JinferChatModel buildModel() {
		return JinferChatModel.builder()
				.model(modelRef())
				.contextCapacity(contextCapacity)
				.options(JinferChatOptions.builder()
						.temperature(temperature)
						.maxTokens(maxTokens)
						.build())
				.build();
	}

	@Override
	protected void warmUp(JinferChatModel model) {
		model.call(new Prompt(List.of(new UserMessage("Hi")),
				JinferChatOptions.builder().maxTokens(1).build()));
	}
}
