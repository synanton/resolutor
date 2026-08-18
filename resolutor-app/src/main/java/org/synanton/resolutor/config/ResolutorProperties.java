package org.synanton.resolutor.config;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Root Resolutor configuration surface. See {@code docs/implementation-plan.md} §10.
 *
 * <p>All fields have safe defaults so the application boots without an {@code application.yml}
 * override in tests.
 */
@ConfigurationProperties(prefix = "resolutor")
public record ResolutorProperties(
    @Nullable Planner planner, @Nullable Dispatch dispatch, @Nullable Backpressure backpressure) {

  public Planner plannerOrDefault() {
    return planner == null ? Planner.defaults() : planner;
  }

  public Dispatch dispatchOrDefault() {
    return dispatch == null ? Dispatch.defaults() : dispatch;
  }

  public Backpressure backpressureOrDefault() {
    return backpressure == null ? Backpressure.defaults() : backpressure;
  }

  public record Planner(
      @Nullable Duration tickInterval,
      @Nullable Integer batchSize,
      @Nullable String orderPolicy,
      @Nullable Integer maxBucketSize,
      @Nullable String plannerVersion,
      @Nullable Boolean colouring,
      @Nullable Boolean locality,
      @Nullable Boolean cost,
      @Nullable Boolean backpressureReorder,
      @Nullable Duration defaultTaskDuration,
      @Nullable Map<String, Duration> taskDurations) {

    public Duration tickIntervalOrDefault() {
      return tickInterval == null ? Duration.ofSeconds(1) : tickInterval;
    }

    public int batchSizeOrDefault() {
      return batchSize == null ? 100 : batchSize;
    }

    public String orderPolicyOrDefault() {
      return orderPolicy == null ? "FIFO" : orderPolicy;
    }

    public int maxBucketSizeOrDefault() {
      return maxBucketSize == null ? 10_000 : maxBucketSize;
    }

    public String plannerVersionOrDefault() {
      return plannerVersion == null ? "v1" : plannerVersion;
    }

    public boolean colouringOrDefault() {
      return colouring == null || colouring;
    }

    public boolean localityOrDefault() {
      return locality == null || locality;
    }

    public boolean costOrDefault() {
      return cost == null || cost;
    }

    public boolean backpressureReorderOrDefault() {
      return backpressureReorder == null || backpressureReorder;
    }

    public Duration defaultTaskDurationOrDefault() {
      return defaultTaskDuration == null ? Duration.ofMillis(100) : defaultTaskDuration;
    }

    public Map<String, Duration> taskDurationsOrEmpty() {
      return taskDurations == null ? Map.of() : taskDurations;
    }

    public static Planner defaults() {
      return new Planner(null, null, null, null, null, null, null, null, null, null, null);
    }
  }

  public record Dispatch(
      @Nullable String mode,
      @Nullable Integer maxConcurrentGroups,
      @Nullable Duration taskTimeout,
      @Nullable Duration lockAtMost) {

    public int maxConcurrentGroupsOrDefault() {
      return maxConcurrentGroups == null ? 0 : maxConcurrentGroups;
    }

    public Duration taskTimeoutOrDefault() {
      return taskTimeout == null ? Duration.ofMinutes(5) : taskTimeout;
    }

    public Duration lockAtMostOrDefault() {
      return lockAtMost == null ? Duration.ofMinutes(10) : lockAtMost;
    }

    public static Dispatch defaults() {
      return new Dispatch(null, null, null, null);
    }
  }

  public record Backpressure(
      @Nullable Boolean enabled,
      @Nullable Limits defaultLimits,
      @Nullable Map<String, Limits> classes) {

    public boolean isEnabled() {
      return enabled == null || enabled;
    }

    public Limits defaultLimitsOrDefault() {
      return defaultLimits == null ? Limits.defaults() : defaultLimits;
    }

    public Map<String, Limits> classesOrEmpty() {
      return classes == null ? Map.of() : classes;
    }

    public static Backpressure defaults() {
      return new Backpressure(true, Limits.defaults(), Map.of());
    }
  }

  public record Limits(@Nullable Long maxInflightMessages, @Nullable Long maxEmissionRatePerHour) {

    public long maxInflight() {
      return maxInflightMessages == null ? 1_000_000L : maxInflightMessages;
    }

    public long maxRatePerHour() {
      return maxEmissionRatePerHour == null ? 500_000L : maxEmissionRatePerHour;
    }

    public static Limits defaults() {
      return new Limits(null, null);
    }
  }

  /** Compile-time null-check convenience used from consumers. */
  public ResolutorProperties nonNull() {
    return new ResolutorProperties(
        Objects.requireNonNullElseGet(planner, Planner::defaults),
        Objects.requireNonNullElseGet(dispatch, Dispatch::defaults),
        Objects.requireNonNullElseGet(backpressure, Backpressure::defaults));
  }
}
