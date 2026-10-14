package org.vaadin.example.vaadin_quixotic.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Guards the lazy-load phase contract: the loader must deliver every phase to listeners in
 * order — including the short-lived {@code WARMING}, which a past bug could skip before READY.
 * A fake in-memory model keeps the test fast and offline.
 */
class AbstractModelServiceTest {

	private static final class FakeService extends AbstractModelService<AutoCloseable> {
		FakeService() {
			super("fake/model.gguf");
		}

		@Override
		protected AutoCloseable buildModel() {
			return () -> {
			};
		}
	}

	@Test
	@Timeout(15)
	void listenerReceivesWarmingPhaseInOrderThroughReady() throws Exception {
		FakeService service = new FakeService();
		List<AbstractModelService.Phase> seen = new CopyOnWriteArrayList<>();
		service.addStatusListener(status -> seen.add(status.phase()));

		service.startLoading();

		long deadline = System.currentTimeMillis() + 12_000;
		while (service.phase() != AbstractModelService.Phase.READY
				&& service.phase() != AbstractModelService.Phase.FAILED
				&& System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}

		assertEquals(AbstractModelService.Phase.READY, service.phase(),
				"loader should reach READY with a fake model");

		assertTrue(seen.contains(AbstractModelService.Phase.WARMING),
				"WARMING must be delivered to phase listeners, got: " + seen);
		assertEquals(AbstractModelService.Phase.READY, seen.get(seen.size() - 1),
				"READY must be the final delivered phase");
		assertTrue(seen.indexOf(AbstractModelService.Phase.LOADING)
				< seen.indexOf(AbstractModelService.Phase.WARMING),
				"phases must arrive in loader order (LOADING before WARMING): " + seen);
		assertTrue(seen.indexOf(AbstractModelService.Phase.WARMING)
				< seen.lastIndexOf(AbstractModelService.Phase.READY),
				"phases must arrive in loader order (WARMING before READY): " + seen);
	}
}
