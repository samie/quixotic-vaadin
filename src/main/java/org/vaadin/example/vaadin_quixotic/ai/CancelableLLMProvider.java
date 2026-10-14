package org.vaadin.example.vaadin_quixotic.ai;

import com.vaadin.flow.component.ai.provider.LLMProvider;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Wraps an {@link LLMProvider} to add turn cancellation, which Vaadin's {@code AIOrchestrator}
 * does not offer in the 25.3 preview. Each {@link #stream} ends the moment {@link #cancel()}
 * fires, which cancels upstream so jinfer stops generating; the partial reply already streamed
 * is kept and the orchestrator finalizes the turn normally.
 */
public final class CancelableLLMProvider implements LLMProvider {

	private final LLMProvider delegate;
	private volatile Sinks.One<Object> cancelSignal;

	public CancelableLLMProvider(LLMProvider delegate) {
		this.delegate = delegate;
	}

	@Override
	public Flux<String> stream(LLMProvider.LLMRequest request) {
		Sinks.One<Object> signal = Sinks.one();
		cancelSignal = signal;
		return delegate.stream(request).takeUntilOther(signal.asMono());
	}

	public void cancel() {
		Sinks.One<Object> signal = cancelSignal;
		if (signal != null) {
			signal.tryEmitValue(Boolean.TRUE);
		}
	}
}
