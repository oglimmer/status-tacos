/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.config;

import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
@Slf4j
public class SchedulingConfig {

  @Value("${monitor.threading.core-pool-size:10}")
  private int corePoolSize;

  @Value("${monitor.threading.max-pool-size:50}")
  private int maxPoolSize;

  @Value("${monitor.threading.queue-capacity:100}")
  private int queueCapacity;

  @Value("${monitor.threading.scheduler-pool-size:5}")
  private int schedulerPoolSize;

  @Bean(name = "taskExecutor")
  public Executor taskExecutor() {
    log.info(
        "Creating task executor with core pool size: {}, max pool size: {}, queue capacity: {}",
        corePoolSize,
        maxPoolSize,
        queueCapacity);

    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(queueCapacity);
    executor.setThreadNamePrefix("monitor-exec-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(60);
    executor.setRejectedExecutionHandler(
        new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();

    return executor;
  }

  @Bean(name = "taskScheduler")
  public TaskScheduler taskScheduler() {
    log.info("Creating task scheduler with pool size: {}", schedulerPoolSize);

    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(schedulerPoolSize);
    scheduler.setThreadNamePrefix("monitor-sched-");
    scheduler.setWaitForTasksToCompleteOnShutdown(true);
    scheduler.setAwaitTerminationSeconds(60);
    scheduler.setRejectedExecutionHandler(
        new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
    scheduler.initialize();

    return scheduler;
  }
}
