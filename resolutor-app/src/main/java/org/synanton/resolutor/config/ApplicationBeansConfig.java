package org.synanton.resolutor.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.synanton.resolutor.adapter.metrics.MicrometerMetricsPort;
import org.synanton.resolutor.application.backpressure.BackpressureConfig;
import org.synanton.resolutor.application.backpressure.BackpressureManager;
import org.synanton.resolutor.application.dispatch.DispatchConfig;
import org.synanton.resolutor.application.dispatch.InProcessDispatcher;
import org.synanton.resolutor.application.ingestion.PlanQueryService;
import org.synanton.resolutor.application.ingestion.TaskIngestionService;
import org.synanton.resolutor.application.lifecycle.LeadershipManager;
import org.synanton.resolutor.application.lifecycle.TaskTimeoutReaper;
import org.synanton.resolutor.application.planner.ExecutionPlanner;
import org.synanton.resolutor.application.planner.PlannerConfig;
import org.synanton.resolutor.application.planner.TaskDurationEstimator;
import org.synanton.resolutor.application.port.in.PlanQueryPort;
import org.synanton.resolutor.application.port.in.ProgressPort;
import org.synanton.resolutor.application.port.in.TaskIngestionPort;
import org.synanton.resolutor.application.port.out.DispatcherPort;
import org.synanton.resolutor.application.port.out.LeadershipPort;
import org.synanton.resolutor.application.port.out.MetricsPort;
import org.synanton.resolutor.application.port.out.PlanPublisherPort;
import org.synanton.resolutor.application.port.out.ProgressRepositoryPort;
import org.synanton.resolutor.application.port.out.ResourceGraphPort;
import org.synanton.resolutor.application.port.out.TaskRepositoryPort;
import org.synanton.resolutor.application.port.out.TaskWorker;
import org.synanton.resolutor.application.progress.ProgressTracker;
import org.synanton.resolutor.domain.policy.DeadlinePolicy;
import org.synanton.resolutor.domain.policy.FifoPolicy;
import org.synanton.resolutor.domain.policy.OrderingPolicy;
import org.synanton.resolutor.domain.policy.PriorityPolicy;
import org.synanton.resolutor.domain.task.Task;

/** Composition root - wires the application services and their dependencies. */
@Configuration
@EnableConfigurationProperties(ResolutorProperties.class)
public class ApplicationBeansConfig {

  @Bean
  PlannerConfig plannerConfig(ResolutorProperties props) {
    ResolutorProperties.Planner p = props.plannerOrDefault();
    return new PlannerConfig(
        p.plannerVersionOrDefault(),
        p.batchSizeOrDefault(),
        resolveOrderingPolicy(p.orderPolicyOrDefault()),
        p.maxBucketSizeOrDefault(),
        p.colouringOrDefault(),
        p.localityOrDefault(),
        p.costOrDefault(),
        p.backpressureReorderOrDefault(),
        p.defaultTaskDurationOrDefault(),
        p.taskDurationsOrEmpty());
  }

  @Bean
  DispatchConfig dispatchConfig(ResolutorProperties props) {
    ResolutorProperties.Dispatch d = props.dispatchOrDefault();
    return new DispatchConfig(d.maxConcurrentGroupsOrDefault(), d.taskTimeoutOrDefault());
  }

  @Bean
  BackpressureConfig backpressureConfig(ResolutorProperties props) {
    ResolutorProperties.Backpressure bp = props.backpressureOrDefault();
    if (!bp.isEnabled()) {
      return BackpressureConfig.disabled();
    }
    ResolutorProperties.Limits defaults = bp.defaultLimitsOrDefault();
    Map<String, BackpressureConfig.ClassConfig> overrides = new HashMap<>();
    bp.classesOrEmpty()
        .forEach(
            (cls, limits) ->
                overrides.put(
                    cls,
                    new BackpressureConfig.ClassConfig(
                        limits.maxInflight(), limits.maxRatePerHour())));
    return new BackpressureConfig(
        true, defaults.maxInflight(), defaults.maxRatePerHour(), overrides);
  }

  @Bean
  BackpressureManager backpressureManager(BackpressureConfig config) {
    return new BackpressureManager(config);
  }

  @Bean
  TaskDurationEstimator taskDurationEstimator(PlannerConfig plannerConfig) {
    return TaskDurationEstimator.of(plannerConfig);
  }

  @Bean
  ExecutionPlanner executionPlanner(
      ResourceGraphPort resourceGraphPort,
      BackpressureManager backpressureManager,
      MetricsPort metrics,
      PlannerConfig plannerConfig,
      TaskDurationEstimator estimator) {
    return new ExecutionPlanner(
        resourceGraphPort, backpressureManager, metrics, plannerConfig, estimator);
  }

  @Bean
  @ConditionalOnMissingBean
  TaskWorker taskWorker() {
    // v1 default: no-op worker so a fresh install can boot without a plugged-in workload. Real
    // deployments override this bean.
    return (Task task) -> TaskWorker.Result.completed();
  }

  @Bean
  MetricsPort metricsPort(
      MeterRegistry registry,
      BackpressureManager backpressureManager,
      TaskRepositoryPort taskRepo,
      ObservationRegistry observations) {
    return new MicrometerMetricsPort(registry, backpressureManager, taskRepo, observations);
  }

  @Bean
  @ConditionalOnMissingBean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
      name = "resolutor.dispatch.mode",
      havingValue = "in-process",
      matchIfMissing = true)
  DispatcherPort dispatcherPort(InProcessDispatcher dispatcher) {
    return dispatcher;
  }

  @Bean
  InProcessDispatcher inProcessDispatcher(
      TaskRepositoryPort taskRepo,
      TaskWorker worker,
      BackpressureManager backpressure,
      MetricsPort metrics,
      DispatchConfig dispatchConfig,
      TaskDurationEstimator estimator) {
    return new InProcessDispatcher(
        taskRepo, worker, backpressure, metrics, dispatchConfig, estimator);
  }

  @Bean
  TaskTimeoutReaper taskTimeoutReaper(
      TaskRepositoryPort taskRepo, BackpressureManager backpressure) {
    return new TaskTimeoutReaper(taskRepo, backpressure);
  }

  @Bean
  LeadershipManager leadershipManager(
      LeadershipPort leadershipPort,
      BackpressureManager backpressure,
      ProgressRepositoryPort progressRepo,
      TaskRepositoryPort taskRepo,
      ExecutionPlanner planner,
      PlanPublisherPort planPublisher,
      PlannerConfig plannerConfig,
      MetricsPort metrics,
      DispatcherPort dispatcher,
      TaskTimeoutReaper timeoutReaper,
      Clock clock) {
    return new LeadershipManager(
        leadershipPort,
        backpressure,
        progressRepo,
        taskRepo,
        planner,
        planPublisher,
        plannerConfig,
        metrics,
        dispatcher,
        timeoutReaper,
        clock);
  }

  @Bean
  TaskIngestionPort taskIngestionPort(
      TaskRepositoryPort taskRepo, MetricsPort metrics, Clock clock) {
    return new TaskIngestionService(taskRepo, metrics, clock);
  }

  @Bean
  ProgressPort progressPort(ProgressRepositoryPort progressRepo, TaskRepositoryPort taskRepo) {
    return new ProgressTracker(progressRepo, taskRepo);
  }

  @Bean
  PlanQueryPort planQueryPort(
      PlanPublisherPort planPublisher, ExecutionPlanner planner, Clock clock) {
    return new PlanQueryService(planPublisher, planner, clock);
  }

  private static OrderingPolicy resolveOrderingPolicy(String name) {
    return switch (name.toUpperCase(java.util.Locale.ROOT)) {
      case "PRIORITY" -> PriorityPolicy.INSTANCE;
      case "DEADLINE" -> DeadlinePolicy.INSTANCE;
      default -> FifoPolicy.INSTANCE;
    };
  }
}
