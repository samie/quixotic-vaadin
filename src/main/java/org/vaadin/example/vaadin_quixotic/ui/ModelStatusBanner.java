package org.vaadin.example.vaadin_quixotic.ui;

import java.util.function.Consumer;

import org.vaadin.example.vaadin_quixotic.ai.AbstractModelService;
import org.vaadin.example.vaadin_quixotic.ai.AbstractModelService.Status;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.theme.lumo.LumoUtility;

public class ModelStatusBanner extends HorizontalLayout {

	private final transient AbstractModelService<?> service;
	private final ProgressBar bar = new ProgressBar();
	private final Span label = new Span();
	private final Span modelRef = new Span();
	private final Span ready = new Span(" · Ready");
	private Runnable onReady = () -> {
	};
	private boolean readyFired;
	private transient Consumer<Status> statusListener;

	public ModelStatusBanner(AbstractModelService<?> service) {
		this.service = service;
		bar.setIndeterminate(true);
		bar.setWidth("12em");
		label.addClassNames(LumoUtility.TextColor.SECONDARY);
		modelRef.addClassName("model-ref");
		modelRef.setVisible(false);
		ready.addClassNames(LumoUtility.TextColor.SECONDARY);
		ready.setVisible(false);
		setAlignItems(Alignment.CENTER);
		setWidthFull();
		addClassNames(LumoUtility.Padding.Vertical.SMALL);
		add(bar, label, modelRef, ready);
		setVisible(false);
	}

	public ModelStatusBanner onReadyRun(Runnable onReady) {
		this.onReady = onReady;
		return this;
	}

	@Override
	protected void onAttach(AttachEvent attachEvent) {
		super.onAttach(attachEvent);
		if (!service.isConfigured()) {
			return;
		}
		UI ui = attachEvent.getUI();
		statusListener = status -> ui.access(() -> render(status));
		service.addStatusListener(statusListener);
		render(service.status());
	}

	@Override
	protected void onDetach(DetachEvent detachEvent) {
		stopListening();
		super.onDetach(detachEvent);
	}

	private void render(Status status) {
		switch (status.phase()) {
			case READY -> {
				setVisible(true);
				bar.setVisible(false);
				label.setText("Model:");
				modelRef.setText(service.modelRef());
				modelRef.setVisible(true);
				ready.setVisible(true);
				stopListening();
				if (!readyFired) {
					readyFired = true;
					onReady.run();
				}
			}
			case FAILED -> {
				setVisible(true);
				bar.setVisible(false);
				modelRef.setVisible(false);
				ready.setVisible(false);
				label.getStyle().set("color", "var(--lumo-error-text-color, #c0392b)");
				label.setText(service.statusMessage());
				stopListening();
			}
			default -> {
				setVisible(true);
				bar.setVisible(true);
				modelRef.setVisible(false);
				ready.setVisible(false);
				if (status.isDeterminate()) {
					bar.setIndeterminate(false);
					bar.setValue(Math.clamp(status.fraction(), 0.0, 1.0));
				} else {
					bar.setIndeterminate(true);
				}
				String detail = status.detail();
				label.setText(detail == null || detail.isBlank()
						? status.phase().message()
						: status.phase().message() + " — " + detail);
			}
		}
	}

	private void stopListening() {
		if (statusListener != null) {
			service.removeStatusListener(statusListener);
			statusListener = null;
		}
	}
}
