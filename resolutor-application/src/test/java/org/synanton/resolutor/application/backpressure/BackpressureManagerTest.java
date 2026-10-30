package org.synanton.resolutor.application.backpressure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BackpressureManagerTest {

  private static final String CLASS = "report";

  @Test
  void disabledAlwaysAdmits() {
    BackpressureManager mgr = new BackpressureManager(BackpressureConfig.disabled());
    assertThat(mgr.admit(CLASS)).isTrue();
    assertThat(mgr.isEnabled()).isFalse();
  }

  @Test
  void admitsWhenBelowLimits() {
    BackpressureManager mgr = new BackpressureManager(BackpressureConfig.defaults());
    assertThat(mgr.admit(CLASS)).isTrue();
  }

  @Test
  void onEmittedIncreasesInflight() {
    BackpressureConfig config = new BackpressureConfig(true, 1L, Long.MAX_VALUE, Map.of());
    BackpressureManager mgr = new BackpressureManager(config);

    assertThat(mgr.admit(CLASS)).isTrue();
    mgr.onEmitted(CLASS);
    // Inflight is now 1 which equals maxInflight(1) - should deny.
    assertThat(mgr.admit(CLASS)).isFalse();
  }

  @Test
  void onCompletedDecrementsInflight() {
    BackpressureConfig config = new BackpressureConfig(true, 1L, Long.MAX_VALUE, Map.of());
    BackpressureManager mgr = new BackpressureManager(config);

    mgr.onEmitted(CLASS);
    assertThat(mgr.admit(CLASS)).isFalse();

    mgr.onCompleted(CLASS);
    assertThat(mgr.admit(CLASS)).isTrue();
  }

  @Test
  void onCompletedClampsAtZero() {
    BackpressureManager mgr = new BackpressureManager(BackpressureConfig.defaults());
    // Call onCompleted before any onEmitted - should not go negative.
    mgr.onCompleted(CLASS);
    mgr.onCompleted(CLASS);
    assertThat(mgr.admit(CLASS)).isTrue();
  }

  @Test
  void rateLimitDeniesAfterThreshold() {
    BackpressureConfig config = new BackpressureConfig(true, Long.MAX_VALUE, 2L, Map.of());
    BackpressureManager mgr = new BackpressureManager(config);

    mgr.onEmitted(CLASS);
    mgr.onEmitted(CLASS);
    // Rate is now 2 which equals maxRatePerHour(2) - should deny.
    assertThat(mgr.admit(CLASS)).isFalse();
  }

  @Test
  void reconstructFromDbSeedsInflight() {
    BackpressureConfig config = new BackpressureConfig(true, 5L, Long.MAX_VALUE, Map.of());
    BackpressureManager mgr = new BackpressureManager(config);

    mgr.reconstructFromDb(Map.of(CLASS, 5L));
    // Inflight == maxInflight → should deny.
    assertThat(mgr.admit(CLASS)).isFalse();
  }

  @Test
  void reconstructFromDbClearsPreviousState() {
    BackpressureConfig config = new BackpressureConfig(true, 1L, Long.MAX_VALUE, Map.of());
    BackpressureManager mgr = new BackpressureManager(config);
    mgr.onEmitted(CLASS);
    assertThat(mgr.admit(CLASS)).isFalse();

    // Reconstruct with zero in-flight.
    mgr.reconstructFromDb(Map.of(CLASS, 0L));
    assertThat(mgr.admit(CLASS)).isTrue();
  }

  @Test
  void perClassOverrideIsRespected() {
    BackpressureConfig config =
        new BackpressureConfig(
            true, 1_000L, 1_000L, Map.of("special", new BackpressureConfig.ClassConfig(1L, 1L)));
    BackpressureManager mgr = new BackpressureManager(config);

    mgr.onEmitted("special");
    assertThat(mgr.admit("special")).isFalse();
    assertThat(mgr.admit(CLASS)).isTrue();
  }

  @Test
  void admitReservesCapacityAcrossBatch() {
    BackpressureConfig config = new BackpressureConfig(true, 2L, Long.MAX_VALUE, Map.of());
    BackpressureManager mgr = new BackpressureManager(config);

    // A batch of tasks in the same class cannot oversubscribe the in-flight limit.
    assertThat(mgr.admit(CLASS)).isTrue();
    assertThat(mgr.admit(CLASS)).isTrue();
    assertThat(mgr.admit(CLASS)).isFalse();
    assertThat(mgr.reservedEstimate(CLASS)).isEqualTo(2);
  }

  @Test
  void onEmittedConvertsReservationToInflight() {
    BackpressureConfig config = new BackpressureConfig(true, 1L, Long.MAX_VALUE, Map.of());
    BackpressureManager mgr = new BackpressureManager(config);

    assertThat(mgr.admit(CLASS)).isTrue();
    assertThat(mgr.reservedEstimate(CLASS)).isEqualTo(1);

    mgr.onEmitted(CLASS);
    // Reservation is consumed; the slot is now real in-flight usage.
    assertThat(mgr.reservedEstimate(CLASS)).isZero();
    assertThat(mgr.admit(CLASS)).isFalse();

    mgr.onCompleted(CLASS);
    assertThat(mgr.admit(CLASS)).isTrue();
  }

  @Test
  void releaseAllReservationsReturnsSlots() {
    BackpressureConfig config = new BackpressureConfig(true, 1L, Long.MAX_VALUE, Map.of());
    BackpressureManager mgr = new BackpressureManager(config);

    assertThat(mgr.admit(CLASS)).isTrue();
    assertThat(mgr.admit(CLASS)).isFalse();

    mgr.releaseAllReservations();
    assertThat(mgr.admit(CLASS)).isTrue();
  }

  @Test
  void releaseReturnsSingleReservation() {
    BackpressureConfig config = new BackpressureConfig(true, 2L, Long.MAX_VALUE, Map.of());
    BackpressureManager mgr = new BackpressureManager(config);

    assertThat(mgr.admit(CLASS)).isTrue();
    assertThat(mgr.admit(CLASS)).isTrue();
    assertThat(mgr.admit(CLASS)).isFalse();

    mgr.release(CLASS);
    assertThat(mgr.reservedEstimate(CLASS)).isEqualTo(1);
    assertThat(mgr.admit(CLASS)).isTrue();
  }

  @Test
  void concurrentAdmissionNeverOversubscribes() throws Exception {
    BackpressureConfig config = new BackpressureConfig(true, 10L, Long.MAX_VALUE, Map.of());
    BackpressureManager mgr = new BackpressureManager(config);

    int threads = 8;
    int attemptsPerThread = 100;
    AtomicInteger admitted = new AtomicInteger();
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
      for (int t = 0; t < threads; t++) {
        futures.add(
            pool.submit(
                () -> {
                  for (int i = 0; i < attemptsPerThread; i++) {
                    if (mgr.admit(CLASS)) {
                      admitted.incrementAndGet();
                    }
                  }
                }));
      }
      pool.shutdown();
      assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
      for (java.util.concurrent.Future<?> future : futures) {
        future.get();
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(admitted.get()).isEqualTo(10);
    assertThat(mgr.reservedEstimate(CLASS)).isEqualTo(10);
  }
}
