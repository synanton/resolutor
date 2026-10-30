package org.synanton.resolutor.application.backpressure;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Probabilistic per-class backpressure without Redis.
 *
 * <p>In-flight counts use exact {@link AtomicLong}s keyed by resource class. Because the number of
 * distinct resource classes is bounded (O(10s)), AtomicLong gives exact decrements - the {@link
 * CountMinSketch} is retained as a utility for future high-cardinality resource-ID tracking.
 *
 * <p>Emission rate uses a {@link RingBuffer} per class (60-bucket, 1-hour sliding window).
 *
 * <p>On leadership acquisition, call {@link #reconstructFromDb(Map)} to seed in-flight counts from
 * the database before the first planning cycle. The ring buffers are reset on restart (documented
 * acceptable loss per docs/design.md Appendix A).
 *
 * <p>Admission is a two-phase reservation: {@link #admit(String)} atomically reserves an in-flight
 * slot (and a rate slot) at planning time so a whole batch cannot oversubscribe a class, and {@link
 * #onEmitted(String)} converts a reservation into real in-flight usage when dispatch actually
 * starts the task. Call {@link #releaseAllReservations()} once a planning cycle (or a dry-run
 * simulation) ends to return any slots that were never turned into dispatched work.
 */
public final class BackpressureManager {

  private final BackpressureConfig config;
  private final ConcurrentHashMap<String, AtomicLong> inflightByClass = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, RingBuffer> rateByClass = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, AtomicLong> reservedByClass = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, AtomicLong> rateReservedByClass =
      new ConcurrentHashMap<>();

  public BackpressureManager(BackpressureConfig config) {
    this.config = Objects.requireNonNull(config, "config");
  }

  public boolean isEnabled() {
    return config.enabled();
  }

  /**
   * Atomically reserve an in-flight slot (and a rate slot) for {@code resourceClass}, returning
   * {@code false} when the class is already at its in-flight or emission-rate limit. The
   * reservation is converted into real in-flight usage by {@link #onEmitted(String)} or returned
   * via {@link #releaseAllReservations()}.
   */
  public boolean admit(String resourceClass) {
    if (!config.enabled()) {
      return true;
    }
    long maxInflight = config.maxInflight(resourceClass);
    long maxRate = config.maxRatePerHour(resourceClass);
    AtomicLong reserved = reservedFor(resourceClass);
    AtomicLong inflight = inflightFor(resourceClass);
    while (true) {
      long current = reserved.get();
      if (inflight.get() + current >= maxInflight) {
        return false;
      }
      if (rateFor(resourceClass).sum() + rateReservedFor(resourceClass).get() >= maxRate) {
        return false;
      }
      if (reserved.compareAndSet(current, current + 1)) {
        rateReservedFor(resourceClass).incrementAndGet();
        return true;
      }
    }
  }

  /**
   * Convert a planning-time reservation into real in-flight usage. When no reservation exists for
   * the class (e.g. tasks dispatched directly, outside planning), it simply counts the emission.
   */
  public void onEmitted(String resourceClass) {
    inflightFor(resourceClass).incrementAndGet();
    rateFor(resourceClass).increment(System.currentTimeMillis());
    consumeReservation(resourceClass);
  }

  /** Return a single planning-time reservation for {@code resourceClass} without emitting work. */
  public void release(String resourceClass) {
    consumeReservation(resourceClass);
  }

  /**
   * Drop every outstanding planning-time reservation. Called at the end of a planning cycle (or
   * dry-run simulation) so tasks that were planned but never dispatched do not leak capacity.
   */
  public void releaseAllReservations() {
    reservedByClass.clear();
    rateReservedByClass.clear();
  }

  /** Decrement in-flight counter when a task completes (or fails / times out). */
  public void onCompleted(String resourceClass) {
    AtomicLong counter = inflightByClass.get(resourceClass);
    if (counter != null) {
      counter.updateAndGet(v -> Math.max(0, v - 1));
    }
  }

  /** Called by {@link BackpressureManager} when a component is denied. */
  public void recordDenied(String resourceClass) {
    // No-op in the base implementation; MetricsPort receives the event from ExecutionPlanner.
  }

  /**
   * Seed in-flight counts from the database. Called once after leadership acquisition to recover
   * from a restart. Ring buffers are intentionally not reconstructed (see design Appendix A).
   */
  public void reconstructFromDb(Map<String, Long> inflightByResourceClass) {
    inflightByClass.clear();
    rateByClass.clear();
    reservedByClass.clear();
    rateReservedByClass.clear();
    long nowMs = System.currentTimeMillis();
    inflightByResourceClass.forEach(
        (cls, count) -> {
          inflightByClass.put(cls, new AtomicLong(count));
          rateByClass.put(cls, new RingBuffer(nowMs));
        });
  }

  /** In-flight and rate usage in {@code [0, +∞)}, 1.0 meaning at the configured limit. */
  public double pressure(String resourceClass) {
    if (!config.enabled()) {
      return 0.0;
    }
    double inflightShare =
        inflightEstimate(resourceClass) / (double) Math.max(1L, config.maxInflight(resourceClass));
    double rateShare =
        rateEstimate(resourceClass) / (double) Math.max(1L, config.maxRatePerHour(resourceClass));
    return Math.max(inflightShare, rateShare);
  }

  /** Current in-flight estimate for {@code resourceClass} (0 if unseen). */
  public long inflightEstimate(String resourceClass) {
    AtomicLong counter = inflightByClass.get(resourceClass);
    return counter == null ? 0L : counter.get();
  }

  /** Current hourly emission-rate estimate for {@code resourceClass}. */
  public long rateEstimate(String resourceClass) {
    RingBuffer buffer = rateByClass.get(resourceClass);
    return buffer == null ? 0L : buffer.sum();
  }

  /** Number of planning-time reservations currently held for {@code resourceClass}. */
  public long reservedEstimate(String resourceClass) {
    AtomicLong counter = reservedByClass.get(resourceClass);
    return counter == null ? 0L : counter.get();
  }

  /** Resource classes that have been observed by admit/emit/reconstruct. */
  public Set<String> trackedClasses() {
    return Set.copyOf(inflightByClass.keySet());
  }

  // ── private helpers ───────────────────────────────────────────────────────

  private AtomicLong inflightFor(String cls) {
    return inflightByClass.computeIfAbsent(cls, k -> new AtomicLong(0));
  }

  private RingBuffer rateFor(String cls) {
    return rateByClass.computeIfAbsent(cls, k -> new RingBuffer(System.currentTimeMillis()));
  }

  private AtomicLong reservedFor(String cls) {
    return reservedByClass.computeIfAbsent(cls, k -> new AtomicLong(0));
  }

  private AtomicLong rateReservedFor(String cls) {
    return rateReservedByClass.computeIfAbsent(cls, k -> new AtomicLong(0));
  }

  private void consumeReservation(String cls) {
    AtomicLong reserved = reservedByClass.get(cls);
    if (reserved != null) {
      reserved.updateAndGet(v -> Math.max(0, v - 1));
    }
    AtomicLong rateReserved = rateReservedByClass.get(cls);
    if (rateReserved != null) {
      rateReserved.updateAndGet(v -> Math.max(0, v - 1));
    }
  }
}
