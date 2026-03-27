package com.lantu.connect.common.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;

/**
 * Async 配置
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Configuration
public class AsyncConfig {

    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("lantu-async-");
        executor.setTaskDecorator(runnable -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                if (context != null) MDC.setContextMap(context);
                try { runnable.run(); }
                finally { MDC.clear(); }
            };
        });
        executor.initialize();
        return executor;
    }
}
