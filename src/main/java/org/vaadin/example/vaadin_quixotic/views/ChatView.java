package org.vaadin.example.vaadin_quixotic.views;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vaadin.example.vaadin_quixotic.ai.CancelableLLMProvider;
import org.vaadin.example.vaadin_quixotic.ai.ChatModelService;
import org.vaadin.example.vaadin_quixotic.ui.ModelStatusBanner;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.ai.orchestrator.AIOrchestrator;
import com.vaadin.flow.component.ai.orchestrator.ResponseListener;
import com.vaadin.flow.component.ai.provider.SpringAILLMProvider;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * Chat with a local, in-JVM LLM via Vaadin's {@link AIOrchestrator} (a 25.3 preview feature,
 * enabled with the {@code com.vaadin.experimental.aiComponents} flag). Uses a custom input
 * field and a Send/Stop toggle, cancelling in-flight generation through
 * {@link CancelableLLMProvider} — a Stop capability the preview does not offer.
 */
@Route("")
@PageTitle("Chat · Quixotic AI")
public class ChatView extends ExampleView {

	private static final Logger log = LoggerFactory.getLogger(ChatView.class);

	private static final String AGENT_NAME = "Agent";

	private static final String SYSTEM_PROMPT =
			"You are " + AGENT_NAME + ", a concise and helpful assistant running fully locally on the JVM.";

	private final transient ChatModelService chatModelService;

	private volatile long turnStartNanos;

	private transient AIOrchestrator orchestrator;
	private transient CancelableLLMProvider provider;

	private final TextField messageField = new TextField();
	private final Button actionButton = new Button("Send", VaadinIcon.PAPERPLANE.create(), e -> onAction());
	private boolean generating;

	public ChatView(ChatModelService chatModelService) {
		super("Chat", "Chat with a large language model running entirely on the JVM. "
				+ "Responses stream in token by token.");

		this.chatModelService = chatModelService;
		if (!chatModelService.isConfigured()) {
			add(missingModelNotice("app.chat.model"));
			return;
		}

		MessageList messageList = new MessageList();
		messageList.setSizeFull();
		messageList.setMarkdown(true);

		messageField.setPlaceholder("Message " + AGENT_NAME + "…");
		messageField.setWidthFull();
		messageField.setEnabled(false);

		actionButton.setWidth("7em");
		actionButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		actionButton.setEnabled(false);
		actionButton.addClickShortcut(Key.ENTER).listenOn(messageField);

		HorizontalLayout inputRow = new HorizontalLayout(messageField, actionButton);
		inputRow.setWidthFull();
		inputRow.setAlignItems(Alignment.BASELINE);
		inputRow.expand(messageField);

		chatModelService.startLoading();
		ModelStatusBanner banner = new ModelStatusBanner(chatModelService)
				.onReadyRun(() -> onModelReady(messageList));

		addAndExpand(messageList);
		add(banner, inputRow);
	}

	private void onModelReady(MessageList messageList) {
		SpringAILLMProvider springProvider = new SpringAILLMProvider(chatModelService.getModel());
		springProvider.setBackgroundExecution(true);
		this.provider = new CancelableLLMProvider(springProvider);

		UI ui = messageList.getUI().orElseGet(UI::getCurrent);

		this.orchestrator = AIOrchestrator.builder(provider, SYSTEM_PROMPT)
				.withMessageList(messageList)
				.withUserName("You")
				.withAssistantName(AGENT_NAME)
				// Runs on the UI thread.
				.withRequestListener(event -> {
					turnStartNanos = System.nanoTime();
					setGenerating(true);
				})
				// Runs on a background thread — marshal UI updates via ui.access.
				.withResponseListener(event -> {
					logTurn(event);
					ui.access(() -> {
						setGenerating(false);
						messageField.focus();
					});
				})
				.build();

		messageField.setEnabled(true);
		actionButton.setEnabled(true);
		messageField.focus();
	}

	private void onAction() {
		if (generating) {
			if (provider != null) {
				provider.cancel();
			}
		} else {
			send();
		}
	}

	private void send() {
		String text = messageField.getValue();
		if (orchestrator == null || generating || text == null || text.isBlank()) {
			return;
		}
		messageField.clear();
		orchestrator.prompt(text);
	}

	private void setGenerating(boolean value) {
		generating = value;
		messageField.setEnabled(!value);
		if (value) {
			actionButton.removeThemeVariants(ButtonVariant.LUMO_PRIMARY);
			actionButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
			actionButton.setIcon(VaadinIcon.STOP.create());
			actionButton.setText("Stop");
		} else {
			actionButton.removeThemeVariants(ButtonVariant.LUMO_ERROR);
			actionButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
			actionButton.setIcon(VaadinIcon.PAPERPLANE.create());
			actionButton.setText("Send");
		}
	}

	private void logTurn(ResponseListener.ResponseEvent event) {
		long elapsedMs = (System.nanoTime() - turnStartNanos) / 1_000_000;
		StringBuilder sb = new StringBuilder("Chat inference: ").append(elapsedMs).append(" ms");
		Integer out = null;
		Integer in = null;
		var metadata = event.getMetadata().orElse(null);
		if (metadata != null && metadata.tokenUsage() != null) {
			out = metadata.tokenUsage().outputTokens();
			in = metadata.tokenUsage().inputTokens();
		}
		if (out != null && out > 0) {
			sb.append(", ").append(out).append(" out tok");
			double seconds = elapsedMs / 1000.0;
			if (seconds > 0) {
				sb.append(String.format(" @ %.1f tok/s", out / seconds));
			}
		}
		if (in != null && in > 0) {
			sb.append(", ").append(in).append(" in tok");
		}
		event.getError().ifPresent(err -> sb.append(" [error: ").append(err.getMessage()).append("]"));
		log.info(sb.toString());
	}
}
