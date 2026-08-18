package org.synanton.resolutor.adapter.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/** Append-only row in {@code execution_plans}. */
@Entity
@Table(name = "execution_plans")
public class ExecutionPlanEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private @Nullable UUID id;

  @Column(name = "generated_at", nullable = false, updatable = false)
  private @Nullable Instant generatedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "plan", nullable = false, columnDefinition = "jsonb")
  private @Nullable String plan;

  public ExecutionPlanEntity() {}

  public @Nullable UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public @Nullable Instant getGeneratedAt() {
    return generatedAt;
  }

  public void setGeneratedAt(Instant generatedAt) {
    this.generatedAt = generatedAt;
  }

  public @Nullable String getPlan() {
    return plan;
  }

  public void setPlan(String plan) {
    this.plan = plan;
  }
}
