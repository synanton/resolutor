package org.synanton.resolutor.domain.plan;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier for a persisted {@link ExecutionPlan}. */
public record PlanId(UUID value) {

  public PlanId {
    Objects.requireNonNull(value, "value");
  }

  public static PlanId generate() {
    return new PlanId(UUID.randomUUID());
  }

  public static PlanId of(UUID value) {
    return new PlanId(value);
  }

  public static PlanId parse(String raw) {
    return new PlanId(UUID.fromString(raw));
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
