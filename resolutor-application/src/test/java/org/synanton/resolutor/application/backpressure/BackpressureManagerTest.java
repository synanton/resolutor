package org.synanton.resolutor.application.backpressure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BackpressureManagerTest {

  private static final String CLASS = "report";

  @Test
  void disabledAlwaysAdmits() {
    BackpressureManager mgr = new BackpressureManager(BackpressureConfig.disabled());
    assertThat(mgr.admit(mgr.newScope(), CLASS)).isTrue();
    assertThat(mgr.isEnabled()).isFalse();
  }

  @Test
  void admitsWhenBelowLimits() {
    BackpressureManager mgr = new BackpressureManager(BackpressureConfig.defaults());
    assertThat(mgr.admit(mgr.newScope(), CLASS)).isTrue();
  }

  @Test
  void onEmittedIncreasesInflight() {
    BackpressureConfig config = new BackpressureConfig(true, 1L, Long.MAX_VALUE, Map.of());
    BackpressureManager mgr = new BackpressureManager(config);

    mgr.onEmitted(CLASS);
    // Inflight is now 1 which equals maxInflight(1) - should deny.
    assertThat(mgr.admit(mgr.newScope(), CLASS)).isFalse();
  }

  @Test
  void onCompletedDecrementsInflight() {
    BackpressureConfig config = new BackpressureConfig(true, 1L, Long.MAX_VALUE, Map.of());
    BackpressureManager mgr = new BackpressureManager(config);

    mgr.onEmitted(CLASS);
    assertThat(mgr.admit(mgr.newScope(), CLASS)).isFalse();

    mgr.onCompleted(CLASS);
    assertThat(mgr.admit(mgr.newScope(), CLASS)).isTrue();
  }

  @Test
  void onCompletedClampsAtZero() {
    BackpressureManager mgr = new BackpressureManager(BackpressureConfig.defaults());
    // Call onCompleted before any onEmitted - should not go negative.
    mgr.onCompleted(CLASS);
    mgr.onCompleted(CLASS);
    assertThat(mgr.admit(mgr.newScope(), CLASS)).isTrue();
  }

  @Test
  void rateLimitDeniesAfterThreshold() {
    BackpressureConfig config = new BackpressureConfig(true, Long.MAX_VALUE, 2L, Map.of());
    BackpressureManager mgr = new BackpressureManager(config);

    mgr.onEmitted(CLASS);
    mgr.onEmitted(CLASS);
    // Rate is now 2 which equals maxRatePerHour(2) - should deny.
    assertThat(mgr.admit(mgr.newScope(), CLASS)).isFalse();
  }

  @Test
  void reconstructFromDbSeedsInflight() {
    BackpressureConfig config = new BackpressureConfig(true, 5L, Long.MAX_VALUE, Map.of());
    BackpressureManager mgr = new BackpressureManager(config);

    mgr.reconstructFromDb(Map.of(CLASS, 5L));
    // Inflight == maxInflight → should deny.
    assertThat(mgr.admit(mgr.newScope(), CLASS)).isFalse();
  }

  @Test
  void reconstructFromDbClearsPreviousState() {
    BackpressureConfig config = new BackpressureConfig(true, 1L, Long.MAX_VALUE, Map.of());
    BackpressureManager mgr = new BackpressureManager(config);
    mgr.onEmitted(CLASS);
    assertThat(mgr.admit(mgr.newScope(), CLASS)).isFalse();

    // Reconstruct with zero in-flight.
    mgr.reconstructFromDb(Map.of(CLASS, 0L));
    assertThat(mgr.admit(mgr.newScope(), CLASS)).isTrue();
  }

  @Test
  void perClassOverrideIsRespected() {
    BackpressureConfig config =
        new BackpressureConfig(
            true, 1_000L, 1_000L, Map.of("special", new BackpressureConfig.ClassConfig(1L, 1L)));
    BackpressureManager mgr = new BackpressureManager(config);

    mgr.onEmitted("special");
    assertThat(mgr.admit(mgr.newScope(), "special")).isFalse();
    assertThat(mgr.admit(mgr.newScope(), CLASS)).isTrue();
  }

  @Test
  void admitReservesCapacityWithinScope() {
    BackpressureConfig config = new BackpressureConfig(true, 2L, Long.MAX_VALUE, Map.of());
    BackpressureManager mgr = new BackpressureManager(config);
    BackpressureManager.ReservationScope scope = mgr.newScope();

    // A batch of tasks in the same class cannot oversubscribe the in-flight limit.
    assertThat(mgr.admit(scope, CLASS)).isTrue();
    assertThat(mgr.admit(scope, CLASS)).isTrue();
    assertThat(mgr.admit(scope, CLASS)).isFalse();
    assertThat(scope.reserved(CLASS)).isEqualTo(2);
  }

  @Test
  void scopesAreIsolated() {
    BackpressureConfig config = new BackpressureConfig(true, 1L, Long.MAX_VALUE, Map.of());
    BackpressureManager mgr = new BackpressureManager(config);

    // A full scope does not leak reservations into a concurrent operation's scope.
    BackpressureManager.ReservationScope first = mgr.newScope();
    assertThat(mgr.admit(first, CLASS)).isTrue();
    assertThat(mgr.admit(first, CLASS)).isFalse();

    BackpressureManager.ReservationScope second = mgr.newScope();
    assertThat(mgr.admit(second, CLASS)).isTrue();
    assertThat(first.reserved(CLASS)).isEqualTo(1);
    assertThat(second.reserved(CLASS)).isEqualTo(1);
  }

  @Test
  void concurrentAdmissionNeverOversubscribesInflight() throws Exception {
    BackpressureConfig config = new BackpressureConfig(true, 10L, Long.MAX_VALUE, Map.of());
    BackpressureManager mgr = new BackpressureManager(config);
    BackpressureManager.ReservationScope scope = mgr.newScope();

    int admitted = runConcurrently(() -> mgr.admit(scope, CLASS));

    assertThat(admitted).isEqualTo(10);
    assertThat(scope.reserved(CLASS)).isEqualTo(10);
  }

  @Test
  void concurrentAdmissionNeverOversubscribesRate() throws Exception {
    BackpressureConfig config = new BackpressureConfig(true, Long.MAX_VALUE, 5L, Map.of());
    BackpressureManager mgr = new BackpressureManager(config);
    BackpressureManager.ReservationScope scope = mgr.newScope();

    int admitted = runConcurrently(() -> mgr.admit(scope, CLASS));

    assertThat(admitted).isEqualTo(5);
    assertThat(scope.rateReserved(CLASS)).isEqualTo(5);
  }

  private static int runConcurrently(Admit action) throws Exception {
    int threads = 8;
    int attemptsPerThread = 100;
    AtomicInteger admitted = new AtomicInteger();
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        futures.add(
            pool.submit(
                () -> {
                  for (int i = 0; i < attemptsPerThread; i++) {
                    if (action.admit()) {
                      admitted.incrementAndGet();
                    }
                  }
                }));
      }
      pool.shutdown();
      assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
      for (Future<?> future : futures) {
        future.get();
      }
    } finally {
      pool.shutdownNow();
    }
    return admitted.get();
  }

  @FunctionalInterface
  private interface Admit {
    boolean admit();
  }
}
