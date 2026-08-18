package org.synanton.resolutor.domain.task;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.synanton.resolutor.domain.resource.Resource;

/**
 * Unit of work. Carries a top-level resource, an opaque search DSL, an opaque payload, a cursor for
 * resumable processing, and lifecycle state.
 *
 * <p>{@code searchDsl} and {@code payload} are stored as raw JSON strings; adapters own
 * serialisation. {@code resolvedResources} is populated by the planner via {@code
 * ResourceGraphPort} and is empty until resolution.
 */
public record Task(
    TaskId id,
    Resource topResource,
    Set<Resource> resolvedResources,
    String searchDsl,
    String payload,
    @Nullable Cursor cursor,
    TaskState state,
    Instant createdAt,
    @Nullable Instant timeoutAt,
    long version) {

  public Task {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(topResource, "topResource");
    resolvedResources = Set.copyOf(resolvedResources);
    Objects.requireNonNull(searchDsl, "searchDsl");
    Objects.requireNonNull(payload, "payload");
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(createdAt, "createdAt");
  }

  public Task withResolvedResources(Set<Resource> resources) {
    return new Task(
        id,
        topResource,
        resources,
        searchDsl,
        payload,
        cursor,
        state,
        createdAt,
        timeoutAt,
        version);
  }

  public Task withState(TaskState newState) {
    return new Task(
        id,
        topResource,
        resolvedResources,
        searchDsl,
        payload,
        cursor,
        newState,
        createdAt,
        timeoutAt,
        version);
  }

  public Task withCursor(Cursor newCursor) {
    return new Task(
        id,
        topResource,
        resolvedResources,
        searchDsl,
        payload,
        newCursor,
        state,
        createdAt,
        timeoutAt,
        version);
  }
}
