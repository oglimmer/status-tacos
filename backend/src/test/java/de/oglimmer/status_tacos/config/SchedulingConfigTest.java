/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

class SchedulingConfigTest {

  @Test
  void aFullPoolQueuesTheCheck_andNeverRunsItOnTheThreadThatSubmitsIt() throws Exception {
    SchedulingConfig config = new SchedulingConfig();
    ReflectionTestUtils.setField(config, "maxPoolSize", 2);
    ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.taskExecutor();
    try {
      int checks = 20;
      CountDownLatch release = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(checks);
      Set<String> threads = ConcurrentHashMap.newKeySet();
      for (int i = 0; i < checks; i++) {
        executor.execute(
            () -> {
              threads.add(Thread.currentThread().getName());
              try {
                release.await(5, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              done.countDown();
            });
      }

      // Two checks run, the others wait in the queue. None ran on this thread.
      assertThat(threads).doesNotContain(Thread.currentThread().getName());
      assertThat(executor.getThreadPoolExecutor().getQueue()).hasSize(checks - 2);

      release.countDown();
      assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(threads).allSatisfy(name -> assertThat(name).startsWith("monitor-exec-"));
    } finally {
      executor.shutdown();
    }
  }
}
