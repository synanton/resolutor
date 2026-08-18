package org.synanton.resolutor.adapter.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.synanton.resolutor.application.backpressure.BackpressureManager;
import org.synanton.resolutor.application.port.out.MetricsPort;
import org.synanton.resolutor.application.port.out.TaskRepositoryPort;
import org.synanton.resolutor.domain.plan.PlanMetrics;
import org.synanton.resolutor.domain.task.TaskState;

/**
 * Micrometer binding for {@link MetricsPort}. Registers the {@code resolutor.*} meters listed in
 * {@code docs/implementation-plan.md} §11.
 */
public final class MicrometerMetricsPort implements MetricsPort {

  private final MeterRegistry registry;
  private final BackpressureManager backpressure;
  private final TaskRepositoryPort taskRepo;
  private final AtomicReference<PlanMetrics> latestPlan =
      new AtomicReference<>(new PlanMetrics(0, 0, 0, 0.0, 0.0, 0));
  private final ConcurrentHashMap<String, Boolean> backpressureClasses = new ConcurrentHashMap<>();
  private final AtomicLong lastActualMillis = new AtomicLong(0L);

  private final ObservationRegistry observations;

  public MicrometerMetricsPort(
      MeterRegistry registry,
      BackpressureManager backpressure,
      TaskRepositoryPort taskRepo,
      ObservationRegistry observations) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.backpressure = Objects.requireNonNull(backpressure, "backpressure");
    this.taskRepo = Objects.requireNonNull(taskRepo, "taskRepo");
    this.observations = Objects.requireNonNull(observations, "observations");
    registerStaticGauges();
  }

  @Override
  public void incrementTasksIngested(int count) {
    Counter.builder("resolutor.tasks.ingested").register(registry).increment(count);
  }

  @Override
  public void recordPlanBuilt(PlanMetrics metrics, Duration planningDuration) {
    latestPlan.set(metrics);
    Timer.builder("resolutor.plan.build.duration")
        .register(registry)
        .record(planningDuration.toNanos(), TimeUnit.NANOSECONDS);
    backpressure.trackedClasses().forEach(this::ensureBackpressureGauges);
  }

  @Override
  public void recordBackpressureDenied(String resourceClass) {
    Counter.builder("resolutor.backpressure.denied")
        .tag("resource_class", resourceClass)
        .register(registry)
        .increment();
    ensureBackpressureGauges(resourceClass);
  }

  @Override
  public void recordGroupDispatched(String componentId, Duration duration, boolean succeeded) {
    Timer.builder("resolutor.dispatch.group.duration")
        .tag("component_id", componentId)
        .tag("outcome", succeeded ? "success" : "failure")
        .register(registry)
        .record(duration.toNanos(), TimeUnit.NANOSECONDS);
  }

  @Override
  public void recordResourceGraphCall(String outcome) {
    Counter.builder("resolutor.resource_graph.calls")
        .tag("outcome", outcome)
        .register(registry)
        .increment();
  }

  @Override
  public <T> T observe(String name, Supplier<T> work) {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(work, "work");
    return Observation.createNotStarted("resolutor." + name, observations).observe(work);
  }

  @Override
  public void recordTaskDuration(String resourceClass, Duration duration) {
    Timer.builder("resolutor.task.duration")
        .tag("resource_class", resourceClass)
        .register(registry)
        .record(duration.toNanos(), TimeUnit.NANOSECONDS);
  }

  @Override
  public void recordPlanExecuted(long estimatedDurationMillis, Duration actual) {
    lastActualMillis.set(actual.toMillis());
    Timer.builder("resolutor.plan.actual.duration")
        .register(registry)
        .record(actual.toNanos(), TimeUnit.NANOSECONDS);
  }

  private void registerStaticGauges() {
    for (TaskState state : TaskState.values()) {
      TaskState captured = state;
      Gauge.builder(
              "resolutor.tasks.state",
              taskRepo,
              repo -> repo.countByState().getOrDefault(captured, 0L))
          .tag("state", captured.name())
          .register(registry);
    }
    Gauge.builder("resolutor.plan.tasks.total", latestPlan, MicrometerMetricsPort::latestTotalTasks)
        .register(registry);
    Gauge.builder("resolutor.plan.components", latestPlan, MicrometerMetricsPort::latestComponents)
        .register(registry);
    Gauge.builder(
            "resolutor.plan.parallelism", latestPlan, MicrometerMetricsPort::latestParallelism)
        .register(registry);
    Gauge.builder(
            "resolutor.plan.serialization_ratio",
            latestPlan,
            MicrometerMetricsPort::latestSerialization)
        .register(registry);
    Gauge.builder(
            "resolutor.plan.chromatic_number", latestPlan, MicrometerMetricsPort::latestChromatic)
        .register(registry);
    Gauge.builder("resolutor.plan.wave_count", latestPlan, MicrometerMetricsPort::latestWaveCount)
        .register(registry);
    Gauge.builder(
            "resolutor.plan.intra_component_parallelism",
            latestPlan,
            MicrometerMetricsPort::latestIntra)
        .register(registry);
    Gauge.builder(
            "resolutor.plan.estimated_duration_millis",
            latestPlan,
            MicrometerMetricsPort::latestEstimated)
        .register(registry);
    Gauge.builder("resolutor.plan.actual_duration_millis", lastActualMillis, AtomicLong::get)
        .register(registry);
  }

  private static double latestTotalTasks(AtomicReference<PlanMetrics> ref) {
    return currentPlan(ref).totalTasks();
  }

  private static double latestComponents(AtomicReference<PlanMetrics> ref) {
    return currentPlan(ref).connectedComponents();
  }

  private static double latestParallelism(AtomicReference<PlanMetrics> ref) {
    return currentPlan(ref).parallelismFactor();
  }

  private static double latestSerialization(AtomicReference<PlanMetrics> ref) {
    return currentPlan(ref).serializationRatio();
  }

  private static double latestChromatic(AtomicReference<PlanMetrics> ref) {
    return currentPlan(ref).chromaticNumber();
  }

  private static double latestWaveCount(AtomicReference<PlanMetrics> ref) {
    return currentPlan(ref).waveCount();
  }

  private static double latestIntra(AtomicReference<PlanMetrics> ref) {
    return currentPlan(ref).intraComponentParallelism();
  }

  private static double latestEstimated(AtomicReference<PlanMetrics> ref) {
    return currentPlan(ref).estimatedDurationMillis();
  }

  private static PlanMetrics currentPlan(AtomicReference<PlanMetrics> ref) {
    PlanMetrics metrics = ref.get();
    return metrics == null ? new PlanMetrics(0, 0, 0, 0.0, 0.0, 0) : metrics;
  }

  private void ensureBackpressureGauges(String resourceClass) {
    backpressureClasses.computeIfAbsent(
        resourceClass,
        cls -> {
          String capturedClass = cls;
          Gauge.builder(
                  "resolutor.backpressure.inflight",
                  backpressure,
                  bp -> (double) bp.inflightEstimate(capturedClass))
              .tag("resource_class", capturedClass)
              .register(registry);
          Gauge.builder(
                  "resolutor.backpressure.rate",
                  backpressure,
                  bp -> (double) bp.rateEstimate(capturedClass))
              .tag("resource_class", capturedClass)
              .register(registry);
          return Boolean.TRUE;
        });
  }
}
