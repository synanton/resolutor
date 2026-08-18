package org.synanton.resolutor.application.backpressure;

import java.util.Map;
import java.util.Objects;

/**
 * Per-class backpressure limits. Falls back to {@code defaultMaxInflight} / {@code
 * defaultMaxRatePerHour} when a class has no override.
 */
public record BackpressureConfig(
    boolean enabled,
    long defaultMaxInflight,
    long defaultMaxRatePerHour,
    Map<String, ClassConfig> classOverrides) {

  public BackpressureConfig {
    Objects.requireNonNull(classOverrides, "classOverrides");
    classOverrides = Map.copyOf(classOverrides);
  }

  public static BackpressureConfig disabled() {
    return new BackpressureConfig(false, Long.MAX_VALUE, Long.MAX_VALUE, Map.of());
  }

  public static BackpressureConfig defaults() {
    return new BackpressureConfig(true, 1_000_000L, 500_000L, Map.of());
  }

  public long maxInflight(String resourceClass) {
    ClassConfig override = classOverrides.get(resourceClass);
    return override != null ? override.maxInflight() : defaultMaxInflight;
  }

  public long maxRatePerHour(String resourceClass) {
    ClassConfig override = classOverrides.get(resourceClass);
    return override != null ? override.maxRatePerHour() : defaultMaxRatePerHour;
  }

  /** Per-class limit overrides. */
  public record ClassConfig(long maxInflight, long maxRatePerHour) {}
}
