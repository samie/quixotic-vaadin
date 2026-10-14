package org.vaadin.example.vaadin_quixotic.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class InferenceExecutorConfig {

	@Bean
	AsyncTaskExecutor inferenceExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		int cores = Runtime.getRuntime().availableProcessors();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(Math.max(2, cores / 2));
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("inference-");
		executor.setDaemon(true);
		executor.setWaitForTasksToCompleteOnShutdown(false);
		return executor;
	}
}
