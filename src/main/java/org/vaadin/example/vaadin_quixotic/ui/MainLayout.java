package org.vaadin.example.vaadin_quixotic.ui;

import org.vaadin.example.vaadin_quixotic.views.AudioView;
import org.vaadin.example.vaadin_quixotic.views.ChatView;
import org.vaadin.example.vaadin_quixotic.views.EmbedView;
import org.vaadin.example.vaadin_quixotic.views.LiveTranscribeView;
import org.vaadin.example.vaadin_quixotic.views.ParakeetView;
import org.vaadin.example.vaadin_quixotic.views.TextToSpeechView;
import org.vaadin.example.vaadin_quixotic.views.VisionView;

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;

@Layout
@AnonymousAllowed
public class MainLayout extends AppLayout {

	public MainLayout() {
		setPrimarySection(Section.DRAWER);
		addToNavbar(new DrawerToggle(), buildTitle());
		addToDrawer(buildLogo(), buildNav());
	}

	private Div buildLogo() {
		Span badge = new Span("AI");
		badge.addClassName("app-logo-badge");

		Div logo = new Div(badge);
		logo.addClassName("app-logo");
		return logo;
	}

	private HorizontalLayout buildTitle() {
		Html name = new Html("""
				<span>Java-only inferences samples with \
				<a href="https://qxotic.ai" target="_blank" rel="noopener">qxotic.ai</a> and \
				<a href="https://vaadin.com" target="_blank" rel="noopener">vaadin.com</a></span>""");
		name.addClassNames(LumoUtility.FontWeight.SEMIBOLD, LumoUtility.FontSize.LARGE);

		HorizontalLayout header = new HorizontalLayout(name);
		header.setAlignItems(FlexComponent.Alignment.BASELINE);
		header.addClassNames(LumoUtility.Padding.Horizontal.MEDIUM);
		header.setSpacing(true);
		return header;
	}

	private SideNav buildNav() {
		SideNav nav = new SideNav();
		nav.addClassNames(LumoUtility.Padding.SMALL);
		nav.addItem(new SideNavItem("Chat", ChatView.class, VaadinIcon.COMMENTS.create()));
		nav.addItem(new SideNavItem("Vision", VisionView.class, VaadinIcon.PICTURE.create()));
		nav.addItem(new SideNavItem("Embed", EmbedView.class, VaadinIcon.CLUSTER.create()));
		nav.addItem(new SideNavItem("Audio", AudioView.class, VaadinIcon.FILE_SOUND.create()));
		nav.addItem(new SideNavItem("Text to Speech", TextToSpeechView.class, VaadinIcon.VOLUME_UP.create()));
		nav.addItem(new SideNavItem("Speech to Text", ParakeetView.class, VaadinIcon.MICROPHONE.create()));
		nav.addItem(new SideNavItem("Live Transcribe", LiveTranscribeView.class, VaadinIcon.DOT_CIRCLE.create()));
		return nav;
	}
}
