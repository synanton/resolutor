package org.synanton.resolutor.adapter.persistence.jpa;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.Cursor;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

/** Bidirectional mapping between {@link Task} records and {@link TaskEntity} rows. */
public final class TaskEntityMapper {

  private static final TypeReference<List<ResourceJson>> RESOURCE_LIST = new TypeReference<>() {};

  private final ObjectMapper json;

  public TaskEntityMapper(ObjectMapper json) {
    this.json = Objects.requireNonNull(json, "json");
  }

  /** Merge {@code task} into {@code entity} (upsert semantics). */
  public void merge(TaskEntity entity, Task task) {
    entity.setId(task.id().value());
    entity.setCreatedAt(task.createdAt());
    entity.setPayload(task.payload());
    entity.setSearchDsl(task.searchDsl());
    entity.setResolvedResourcesJson(serialise(task.resolvedResources()));
    entity.setCursor(task.cursor() == null ? null : task.cursor().value());
    entity.setState(task.state().name());
    entity.setTimeoutAt(task.timeoutAt());
    entity.setTopResourceClass(task.topResource().resourceClass());
    entity.setTopResourceId(task.topResource().resourceId());
    if (entity.getVersion() == 0L && task.version() > 0L) {
      entity.setVersion(task.version());
    }
  }

  public Task toDomain(TaskEntity entity) {
    UUID id = Objects.requireNonNull(entity.getId(), "entity.id");
    String state = Objects.requireNonNull(entity.getState(), "entity.state");
    Instant createdAt = Objects.requireNonNull(entity.getCreatedAt(), "entity.createdAt");
    String payload = Objects.requireNonNull(entity.getPayload(), "entity.payload");
    String searchDsl = Objects.requireNonNull(entity.getSearchDsl(), "entity.searchDsl");
    String topClass =
        Objects.requireNonNull(entity.getTopResourceClass(), "entity.topResourceClass");
    String topId = Objects.requireNonNull(entity.getTopResourceId(), "entity.topResourceId");

    Cursor cursor = entity.getCursor() == null ? null : new Cursor(entity.getCursor());

    return new Task(
        TaskId.of(id),
        Resource.of(topClass, topId),
        deserialise(entity.getResolvedResourcesJson()),
        searchDsl,
        payload,
        cursor,
        TaskState.valueOf(state),
        createdAt,
        entity.getTimeoutAt(),
        entity.getVersion());
  }

  // ── JSON helpers ──────────────────────────────────────────────────────────

  private @Nullable String serialise(Set<Resource> resources) {
    if (resources.isEmpty()) {
      return null;
    }
    try {
      List<ResourceJson> list =
          resources.stream().map(r -> new ResourceJson(r.resourceClass(), r.resourceId())).toList();
      return json.writeValueAsString(list);
    } catch (JsonMappingException e) {
      throw new IllegalStateException("Failed to serialise resolvedResources", e);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialise resolvedResources", e);
    }
  }

  private Set<Resource> deserialise(@Nullable String raw) {
    if (raw == null || raw.isBlank()) {
      return Set.of();
    }
    try {
      List<ResourceJson> list = json.readValue(raw, RESOURCE_LIST);
      return list.stream()
          .map(r -> Resource.of(r.resourceClass(), r.resourceId()))
          .collect(Collectors.toUnmodifiableSet());
    } catch (Exception e) {
      throw new IllegalStateException("Failed to deserialise resolvedResources: " + raw, e);
    }
  }

  /** Wire format for {@link Resource} inside the JSON payload. Package-private for testing. */
  record ResourceJson(String resourceClass, String resourceId) {}
}
