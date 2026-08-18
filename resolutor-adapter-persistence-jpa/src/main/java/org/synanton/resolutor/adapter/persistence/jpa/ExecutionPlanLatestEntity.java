package org.synanton.resolutor.adapter.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/** Single-row entity mapping the {@code execution_plan_latest} table. */
@Entity
@Table(name = "execution_plan_latest")
public class ExecutionPlanLatestEntity {

  /** Constant primary-key value enforced by the database check constraint. */
  public static final short SINGLETON_ID = 1;

  @Id
  @Column(name = "id", nullable = false)
  private short id;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "plan", nullable = false, columnDefinition = "jsonb")
  private @Nullable String plan;

  @Column(name = "generated_at", nullable = false)
  private @Nullable Instant generatedAt;

  public ExecutionPlanLatestEntity() {
    this.id = SINGLETON_ID;
  }

  public short getId() {
    return id;
  }

  public void setId(short id) {
    this.id = id;
  }

  public @Nullable String getPlan() {
    return plan;
  }

  public void setPlan(String plan) {
    this.plan = plan;
  }

  public @Nullable Instant getGeneratedAt() {
    return generatedAt;
  }

  public void setGeneratedAt(Instant generatedAt) {
    this.generatedAt = generatedAt;
  }
}
