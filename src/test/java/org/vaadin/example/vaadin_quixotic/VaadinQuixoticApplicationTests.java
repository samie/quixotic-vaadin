package org.vaadin.example.vaadin_quixotic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.vaadin.example.vaadin_quixotic.ai.AbstractModelService;
import org.vaadin.example.vaadin_quixotic.ai.AudioModelService;
import org.vaadin.example.vaadin_quixotic.ai.ChatModelService;
import org.vaadin.example.vaadin_quixotic.ai.EmbeddingModelService;
import org.vaadin.example.vaadin_quixotic.ai.LiveTranscriptionService;
import org.vaadin.example.vaadin_quixotic.ai.SpeechModelService;
import org.vaadin.example.vaadin_quixotic.ai.TranscriptionModelService;
import org.vaadin.example.vaadin_quixotic.ai.VisionService;
import org.vaadin.example.vaadin_quixotic.views.ChatView;
import org.vaadin.example.vaadin_quixotic.views.LiveTranscribeView;

/**
 * Smoke tests. Jinfer's model autoconfigurations are excluded and models load lazily, so the
 * context comes up fast and offline with no real GGUF models downloaded or loaded.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude="
				+ "com.qxotic.jinfer.spring.ai.autoconfigure.JinferChatAutoConfiguration,"
				+ "com.qxotic.jinfer.spring.ai.autoconfigure.JinferEmbeddingAutoConfiguration,"
				+ "com.qxotic.jinfer.spring.ai.autoconfigure.JinferSpeechAutoConfiguration,"
				+ "com.qxotic.jinfer.spring.ai.autoconfigure.JinferTranscriptionAutoConfiguration,"
				+ "com.qxotic.jinfer.spring.ai.autoconfigure.JinferRerankAutoConfiguration"
})
class VaadinQuixoticApplicationTests {

	@Test
	void contextLoads() {
	}

	/**
	 * Guards against the Vaadin-AI / Spring-AI version clash: constructing ChatView must not fail
	 * with a LinkageError (the original bug was a NoSuchMethodError in SpringAILLMProvider).
	 */
	@Test
	void chatViewConstructsWithoutLinkageError() {
		ChatModelService service = new ChatModelService("LiquidAI/LFM2.5-350M-GGUF/x.gguf", 4096, 512, 0.7);
		assertDoesNotThrow(() -> new ChatView(service));
	}

	/** An unconfigured view must construct without touching the model, showing the notice only. */
	@Test
	void liveTranscribeViewConstructsWhenUnconfigured() {
		LiveTranscriptionService service = new LiveTranscriptionService("", "");
		assertFalse(service.isConfigured());
		assertDoesNotThrow(() -> new LiveTranscribeView(service));
	}

	/**
	 * Every capability service must construct in IDLE with no model loaded — loading starts only
	 * when a view calls startLoading() — which keeps boot instant. Also covers the blank-ref branch.
	 */
	@Test
	void capabilityServicesConstructIdleWithoutLoading() {
		AbstractModelService<?>[] configured = {
				new ChatModelService("LiquidAI/LFM2.5-350M-GGUF:Q8_0", 4096, 512, 0.7),
				new EmbeddingModelService("LiquidAI/LFM2.5-Embedding-350M-GGUF:Q8_0", 2048),
				new SpeechModelService("simonfxr/kokoro.cpp-GGUF:Q8_0",
						"simonfxr/kokoro.cpp-GGUF/voices/kokoro-voice-af_heart.gguf"),
				new VisionService("LiquidAI/LFM2.5-VL-3B-GGUF:Q8_0",
						"LiquidAI/LFM2.5-VL-3B-GGUF/mmproj-LFM2.5-VL-3B-Q8_0.gguf", 512),
				new AudioModelService("unsloth/gemma-4-E2B-it-GGUF:Q4_K_M",
						"unsloth/gemma-4-E2B-it-GGUF/mmproj-BF16.gguf", 512),
				new TranscriptionModelService("mudler/parakeet-cpp-gguf/tdt-0.6b-v3-q5_k.gguf"),
				new LiveTranscriptionService("mudler/parakeet-cpp-gguf/tdt-0.6b-v3-q5_k.gguf", ""),
		};
		assertEquals("LiquidAI/LFM2.5-350M-GGUF:Q8_0", configured[0].modelRef());
		for (AbstractModelService<?> s : configured) {
			assertTrue(s.isConfigured());
			assertEquals(AbstractModelService.Phase.IDLE, s.phase());
			assertNull(s.getModel());
		}
		assertFalse(new ChatModelService("", 4096, 512, 0.7).isConfigured());
		assertFalse(new AudioModelService("", "", 512).isConfigured());
		assertFalse(new TranscriptionModelService("").isConfigured());
		assertFalse(new LiveTranscriptionService("", "").isConfigured());
	}
}
