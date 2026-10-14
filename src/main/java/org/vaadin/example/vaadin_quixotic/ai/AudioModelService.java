package org.vaadin.example.vaadin_quixotic.ai;

import java.util.List;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;

import com.qxotic.jinfer.spring.ai.JinferChatModel;
import com.qxotic.jinfer.spring.ai.JinferChatOptions;

@Service
public class AudioModelService extends AbstractModelService<JinferChatModel> {

	private static final String TRANSCRIBE_PROMPT = "Transcribe this recording.";

	private final String mmprojRef;
	private final int maxTokens;

	public AudioModelService(
			@Value("${app.audio.model:}") String modelRef,
			@Value("${app.audio.mmproj:}") String mmprojRef,
			@Value("${app.audio.max-tokens:512}") int maxTokens) {
		super(modelRef);
		this.mmprojRef = mmprojRef;
		this.maxTokens = maxTokens;
	}

	@Override
	protected JinferChatModel buildModel() {
		var builder = JinferChatModel.builder()
				.model(modelRef())
				.contextCapacity(4096)
				.options(JinferChatOptions.builder().maxTokens(maxTokens).thinking(false).build());
		if (StringUtils.hasText(mmprojRef)) {
			builder.companion("media", mmprojRef);
		}
		return builder.build();
	}

	public String transcribe(byte[] audio, MimeType mimeType) {
		Media media = Media.builder().mimeType(mimeType).data(audio).build();
		UserMessage message = UserMessage.builder().text(TRANSCRIBE_PROMPT).media(List.of(media)).build();
		ChatResponse response = getModel().call(new Prompt(List.of(message)));
		if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
			return "";
		}
		String text = response.getResult().getOutput().getText();
		return text == null ? "" : text;
	}
}
