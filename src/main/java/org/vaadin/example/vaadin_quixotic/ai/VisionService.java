package org.vaadin.example.vaadin_quixotic.ai;

import java.util.Base64;
import java.util.List;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

import com.qxotic.jinfer.spring.ai.JinferChatModel;
import com.qxotic.jinfer.spring.ai.JinferChatOptions;

import reactor.core.publisher.Flux;

@Service
public class VisionService extends AbstractModelService<JinferChatModel> {

	private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
			"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M8AAAMBAQDJ/1eRAAAAAElFTkSuQmCC");

	private final String mmprojRef;
	private final int maxTokens;

	public VisionService(
			@Value("${app.vision.model:}") String modelRef,
			@Value("${app.vision.mmproj:}") String mmprojRef,
			@Value("${app.vision.max-tokens:512}") int maxTokens) {
		super(modelRef);
		this.mmprojRef = mmprojRef;
		this.maxTokens = maxTokens;
	}

	@Override
	protected JinferChatModel buildModel() {
		var builder = JinferChatModel.builder()
				.model(modelRef())
				.contextCapacity(4096)
				.options(JinferChatOptions.builder().maxTokens(maxTokens).build());
		if (StringUtils.hasText(mmprojRef)) {
			builder.companion("media", mmprojRef);
		}
		return builder.build();
	}

	public Flux<ChatResponse> stream(byte[] image, MimeType mimeType, String prompt) {
		return Flux.defer(() -> getModel().stream(new Prompt(imageMessage(image, mimeType, prompt))));
	}

	@Override
	protected void warmUp(JinferChatModel model) {
		model.call(new Prompt(
				List.of(imageMessage(ONE_PIXEL_PNG, MimeTypeUtils.IMAGE_PNG, "Describe.")),
				JinferChatOptions.builder().maxTokens(1).build()));
	}

	private static UserMessage imageMessage(byte[] image, MimeType mimeType, String prompt) {
		Media media = Media.builder().mimeType(mimeType).data(image).build();
		return UserMessage.builder().text(prompt).media(List.of(media)).build();
	}
}
