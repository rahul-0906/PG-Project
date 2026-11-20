package com.pgcrm.config;

import com.pgcrm.multitenancy.TenantAwareTaskDecorator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Asynchronous execution configuration wiring {@link TenantAwareTaskDecorator}
 * into Spring's task execution pool.
 *
 * <p>Ensures that any method annotated with {@code @Async} inherits the calling
 * thread's {@code TenantContext} and SLF4J MDC, and clears them cleanly in a
 * {@code finally} block upon task completion.</p>
 */
@Configuration
@EnableAsync
@RequiredArgsConstructor
public class AsyncConfig implements AsyncConfigurer {

    private final TenantAwareTaskDecorator taskDecorator;

    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("async-tenant-");
        executor.setTaskDecorator(taskDecorator);
        executor.initialize();
        return executor;
    }
}
