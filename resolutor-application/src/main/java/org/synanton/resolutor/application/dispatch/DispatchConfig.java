package org.synanton.resolutor.application.dispatch;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable configuration snapshot for {@link InProcessDispatcher}.
 *
 * <p>{@code maxConcurrentGroups} of {@code 0} means unbounded - the virtual-thread executor grants
 * a carrier per group. See {@code docs/implementation-plan.md} §7.
 */
public record DispatchConfig(int maxConcurrentGroups, Duration taskTimeout) {

  public DispatchConfig {
    if (maxConcurrentGroups < 0) {
      throw new IllegalArgumentException("maxConcurrentGroups must be >= 0");
    }
    Objects.requireNonNull(taskTimeout, "taskTimeout");
    if (taskTimeout.isNegative() || taskTimeout.isZero()) {
      throw new IllegalArgumentException("taskTimeout must be positive");
    }
  }

  /** Unbounded concurrency and a 5-minute per-task deadline. */
  public static DispatchConfig defaults() {
    return new DispatchConfig(0, Duration.ofMinutes(5));
  }
}
