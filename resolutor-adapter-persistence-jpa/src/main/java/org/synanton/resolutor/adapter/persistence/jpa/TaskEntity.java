package org.synanton.resolutor.adapter.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/** Row-shaped image of a {@link org.synanton.resolutor.domain.task.Task}. */
@Entity
@Table(name = "tasks")
public class TaskEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private @Nullable UUID id;

  @Column(name = "created_at", nullable = false, updatable = false)
  private @Nullable Instant createdAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
  private @Nullable String payload;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "search_dsl", nullable = false, columnDefinition = "jsonb")
  private @Nullable String searchDsl;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "resources", columnDefinition = "jsonb")
  private @Nullable String resolvedResourcesJson;

  @Column(name = "cursor")
  private @Nullable String cursor;

  @Column(name = "state", nullable = false)
  private @Nullable String state;

  @Column(name = "timeout_at")
  private @Nullable Instant timeoutAt;

  @Column(name = "top_resource_class", nullable = false)
  private @Nullable String topResourceClass;

  @Column(name = "top_resource_id", nullable = false)
  private @Nullable String topResourceId;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  public TaskEntity() {}

  public @Nullable UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public @Nullable Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public @Nullable String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  public @Nullable String getSearchDsl() {
    return searchDsl;
  }

  public void setSearchDsl(String searchDsl) {
    this.searchDsl = searchDsl;
  }

  public @Nullable String getResolvedResourcesJson() {
    return resolvedResourcesJson;
  }

  public void setResolvedResourcesJson(@Nullable String resolvedResourcesJson) {
    this.resolvedResourcesJson = resolvedResourcesJson;
  }

  public @Nullable String getCursor() {
    return cursor;
  }

  public void setCursor(@Nullable String cursor) {
    this.cursor = cursor;
  }

  public @Nullable String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public @Nullable Instant getTimeoutAt() {
    return timeoutAt;
  }

  public void setTimeoutAt(@Nullable Instant timeoutAt) {
    this.timeoutAt = timeoutAt;
  }

  public @Nullable String getTopResourceClass() {
    return topResourceClass;
  }

  public void setTopResourceClass(String topResourceClass) {
    this.topResourceClass = topResourceClass;
  }

  public @Nullable String getTopResourceId() {
    return topResourceId;
  }

  public void setTopResourceId(String topResourceId) {
    this.topResourceId = topResourceId;
  }

  public long getVersion() {
    return version;
  }

  public void setVersion(long version) {
    this.version = version;
  }
}
