/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.config;

import java.util.concurrent.Executor;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
@Slf4j
public class SchedulingConfig {

  @Value("${monitor.threading.max-pool-size:50}")
  private int maxPoolSize;

  @Value("${monitor.threading.queue-capacity:100}")
  private int queueCapacity;

  @Value("${monitor.threading.scheduler-pool-size:5}")
  private int schedulerPoolSize;

  @Value("${monitor.threading.alert-queue-capacity:1000}")
  private int alertQueueCapacity;

  /**
   * Scheduled jobs run on every replica. The lock in the shedlock table makes sure only one replica
   * runs a job at a time. The database clock is used, so the replica clocks do not matter.
   */
  @Bean
  public LockProvider lockProvider(DataSource dataSource) {
    return new JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(new JdbcTemplate(dataSource))
            .usingDbTime()
            .build());
  }

  /**
   * Runs the HTTP checks. A ThreadPoolExecutor only starts more than the core threads when the
   * queue is full, so core size = max size: up to maxPoolSize checks run at the same time. Idle
   * threads stop after 60 s.
   */
  @Bean(name = "taskExecutor")
  public Executor taskExecutor() {
    log.info(
        "Creating task executor with pool size: {}, queue capacity: {}",
        maxPoolSize,
        queueCapacity);

    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(maxPoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setAllowCoreThreadTimeOut(true);
    executor.setKeepAliveSeconds(60);
    executor.setQueueCapacity(queueCapacity);
    executor.setThreadNamePrefix("monitor-exec-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(60);
    executor.setRejectedExecutionHandler(
        new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();

    return executor;
  }

  /**
   * Sends the alerts after the check is committed. One thread: the alerts of a monitor are sent in
   * the order of its checks, so the DOWN alert is recorded before the next check looks for it, and
   * alerts hold at most one database connection.
   */
  @Bean(name = "alertExecutor")
  public Executor alertExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(alertQueueCapacity);
    executor.setThreadNamePrefix("monitor-alert-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
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
