package com.example.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 操作日志异步写库执行器。
 *
 * <p>审计/操作日志不参与业务主流程，异步落库可减少主请求路径 2 次 DB 往返。
 * 队列可缓冲瞬时突发；极端拥堵时回退调用线程同步写（CallerRunsPolicy），
 * 保证日志不丢失，代价是退化为同步延迟。
 */
@Configuration
public class OperLogExecutorConfig {

    @Bean(name = "operLogExecutor")
    public TaskExecutor operLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10_000);
        executor.setThreadNamePrefix("oper-log-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }
}
