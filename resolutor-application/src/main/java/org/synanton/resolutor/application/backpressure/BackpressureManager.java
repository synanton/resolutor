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
 * <p>Admission is scoped to a single planning operation. A {@link ReservationScope} accumulates the
 * in-flight and rate slots reserved while one plan is being compiled, so a whole batch cannot
 * oversubscribe a class and concurrent operations (for example a scheduled cycle and a dry-run
 * {@code simulate}) cannot release each other's reservations. The scope is discarded when the
 * operation ends; real in-flight usage is still tracked by {@link #onEmitted} / {@link
 * #onCompleted}.
 *
 * <p><b>Limitation (asynchronous dispatch).</b> Reservations guard the synchronous in-process
 * dispatcher, which consumes them by incrementing in-flight usage during the same planning cycle.
 * The Kafka dispatcher only publishes a plan and returns before consumers start the tasks, so
 * reservations are not retained until durable scheduling/execution. Correct capacity accounting for
 * asynchronous delivery depends on the broader Kafka delivery/acknowledgement lifecycle and is
 * deferred.
 */
public final class BackpressureManager {

  private final BackpressureConfig config;
  private final ConcurrentHashMap<String, AtomicLong> inflightByClass = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, RingBuffer> rateByClass = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Object> locksByClass = new ConcurrentHashMap<>();

  public BackpressureManager(BackpressureConfig config) {
    this.config = Objects.requireNonNull(config, "config");
  }

  public boolean isEnabled() {
    return config.enabled();
  }

  /** Begin a new, isolated reservation scope for a single planning operation. */
  public ReservationScope newScope() {
    return new ReservationScope();
  }

  /**
   * Atomically reserve an in-flight slot (and a rate slot) for {@code resourceClass} in {@code
   * scope}, returning {@code false} when the class is already at its in-flight or emission-rate
   * limit. The in-flight check compares the class's actual in-flight count plus the reservations
   * already held by {@code scope}; the rate check does the same for the emission-rate window. The
   * whole check-and-reserve runs under a per-class lock so concurrent admissions cannot exceed the
   * limit.
   */
  public boolean admit(ReservationScope scope, String resourceClass) {
    Objects.requireNonNull(scope, "scope");
    if (!config.enabled()) {
      return true;
    }
    synchronized (lockFor(resourceClass)) {
      long inflight = inflightFor(resourceClass).get();
      if (inflight + scope.reserved(resourceClass) >= config.maxInflight(resourceClass)) {
        return false;
      }
      long rate = rateFor(resourceClass).sum() + scope.rateReserved(resourceClass);
      if (rate >= config.maxRatePerHour(resourceClass)) {
        return false;
      }
      scope.reserve(resourceClass);
      return true;
    }
  }

  /**
   * Return a single planning-time reservation for {@code resourceClass} held by {@code scope},
   * without dispatching any work. Used to roll back a component that failed admission partway.
   */
  public void release(ReservationScope scope, String resourceClass) {
    Objects.requireNonNull(scope, "scope");
    scope.release(resourceClass);
  }

  /** Record that a task for {@code resourceClass} has actually been dispatched. */
  public void onEmitted(String resourceClass) {
    synchronized (lockFor(resourceClass)) {
      inflightFor(resourceClass).incrementAndGet();
      rateFor(resourceClass).increment(System.currentTimeMillis());
    }
  }

  /** Decrement in-flight counter when a task completes (or fails / times out). */
  public void onCompleted(String resourceClass) {
    synchronized (lockFor(resourceClass)) {
      AtomicLong counter = inflightByClass.get(resourceClass);
      if (counter != null) {
        counter.updateAndGet(v -> Math.max(0, v - 1));
      }
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

  /** Resource classes that have been observed by admit/emit/reconstruct. */
  public Set<String> trackedClasses() {
    return Set.copyOf(inflightByClass.keySet());
  }

  // ── private helpers ───────────────────────────────────────────────────────

  private Object lockFor(String cls) {
    return locksByClass.computeIfAbsent(cls, k -> new Object());
  }

  private AtomicLong inflightFor(String cls) {
    return inflightByClass.computeIfAbsent(cls, k -> new AtomicLong(0));
  }

  private RingBuffer rateFor(String cls) {
    return rateByClass.computeIfAbsent(cls, k -> new RingBuffer(System.currentTimeMillis()));
  }

  /**
   * Planning-time reservations held by a single planning operation. Counters are scoped to the
   * operation: releasing or discarding one scope never affects another scope's reservations.
   */
  public static final class ReservationScope {

    private final ConcurrentHashMap<String, AtomicLong> reservedByClass = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> rateReservedByClass =
        new ConcurrentHashMap<>();

    long reserved(String resourceClass) {
      AtomicLong counter = reservedByClass.get(resourceClass);
      return counter == null ? 0L : counter.get();
    }

    long rateReserved(String resourceClass) {
      AtomicLong counter = rateReservedByClass.get(resourceClass);
      return counter == null ? 0L : counter.get();
    }

    void reserve(String resourceClass) {
      reservedByClass.computeIfAbsent(resourceClass, k -> new AtomicLong()).incrementAndGet();
      rateReservedByClass.computeIfAbsent(resourceClass, k -> new AtomicLong()).incrementAndGet();
    }

    void release(String resourceClass) {
      AtomicLong reserved = reservedByClass.get(resourceClass);
      if (reserved != null) {
        reserved.updateAndGet(v -> Math.max(0, v - 1));
      }
      AtomicLong rateReserved = rateReservedByClass.get(resourceClass);
      if (rateReserved != null) {
        rateReserved.updateAndGet(v -> Math.max(0, v - 1));
      }
    }
  }
}
