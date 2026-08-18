package org.synanton.resolutor.adapter.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.synanton.resolutor.adapter.web.dto.IngestTaskRequest;
import org.synanton.resolutor.adapter.web.dto.IngestTaskResponse;
import org.synanton.resolutor.adapter.web.dto.ProgressUpdateRequest;
import org.synanton.resolutor.adapter.web.dto.TaskStatusResponse;
import org.synanton.resolutor.application.port.in.NewTaskCommand;
import org.synanton.resolutor.application.port.in.ProgressDelta;
import org.synanton.resolutor.application.port.in.ProgressPort;
import org.synanton.resolutor.application.port.in.TaskIngestionPort;
import org.synanton.resolutor.application.port.in.TaskStatusView;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.TaskId;

/**
 * REST facade for task lifecycle. See {@code docs/design.md} §25 and {@code
 * docs/implementation-plan.md} §15 phase 4.
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

  private final TaskIngestionPort ingestion;
  private final ProgressPort progress;
  private final ObjectMapper json;

  public TaskController(TaskIngestionPort ingestion, ProgressPort progress, ObjectMapper json) {
    this.ingestion = Objects.requireNonNull(ingestion, "ingestion");
    this.progress = Objects.requireNonNull(progress, "progress");
    this.json = Objects.requireNonNull(json, "json");
  }

  /** Create a task in {@code PENDING} and return 201 with its id. */
  @PostMapping
  public ResponseEntity<IngestTaskResponse> ingest(@Valid @RequestBody IngestTaskRequest req) {
    NewTaskCommand cmd =
        new NewTaskCommand(
            Resource.of(req.topResourceClass(), req.topResourceId()),
            writeJson(req.searchDsl()),
            writeJson(req.payload()),
            req.timeoutAt());
    TaskId id = ingestion.ingest(cmd);
    return ResponseEntity.status(HttpStatus.CREATED).body(new IngestTaskResponse(id.value()));
  }

  /** Return current state and optional progress counters for {@code id}. */
  @GetMapping("/{id}")
  public TaskStatusResponse status(@PathVariable UUID id) {
    TaskStatusView view = progress.status(TaskId.of(id));
    TaskStatusResponse.ProgressBody progressBody =
        view.progress() == null
            ? null
            : new TaskStatusResponse.ProgressBody(
                view.progress().totalCount(),
                view.progress().successCount(),
                view.progress().failedCount(),
                view.progress().pendingCount());
    return new TaskStatusResponse(id, view.state().name(), progressBody);
  }

  /** Apply a progress delta; 204 on success. */
  @PostMapping("/{id}/progress")
  @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateProgress(@PathVariable UUID id, @Valid @RequestBody ProgressUpdateRequest req) {
    progress.updateProgress(
        TaskId.of(id), new ProgressDelta(req.successDelta(), req.failedDelta(), req.totalDelta()));
  }

  /** Force the task into COMPLETED; 204 on success. */
  @PostMapping("/{id}/complete")
  @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
  public void forceComplete(@PathVariable UUID id) {
    progress.forceComplete(TaskId.of(id));
  }

  private String writeJson(@Nullable JsonNode node) {
    if (node == null || node instanceof MissingNode) {
      return "{}";
    }
    try {
      return json.writeValueAsString(node);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid JSON in request", e);
    }
  }
}
