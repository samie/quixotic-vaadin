package org.vaadin.example.vaadin_quixotic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.theme.aura.Aura;

@SpringBootApplication
@Push
// With an AppShellConfigurator present, Aura no longer auto-loads, so name its stylesheet explicitly.
@StyleSheet(Aura.STYLESHEET)
@StyleSheet("context://styles.css")
@PWA(
		name = "Java-only inference samples",
		shortName = "AI Samples",
		description = "Local, pure-JVM AI inference samples (chat, vision, embeddings, speech) "
				+ "built with Vaadin and Spring AI, powered by Quixotic.",
		themeColor = "#4f46e5",
		backgroundColor = "#ffffff",
		display = "standalone",
		offline = false)
public class VaadinQuixoticApplication implements AppShellConfigurator {

	public static void main(String[] args) {
		SpringApplication.run(VaadinQuixoticApplication.class, args);
	}

}
