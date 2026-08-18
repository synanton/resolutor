package org.synanton.resolutor.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.synanton.resolutor.application.port.in.NewTaskCommand;
import org.synanton.resolutor.application.port.in.PlanQueryPort;
import org.synanton.resolutor.application.port.in.ProgressDelta;
import org.synanton.resolutor.application.port.in.ProgressPort;
import org.synanton.resolutor.application.port.in.TaskIngestionPort;
import org.synanton.resolutor.application.port.in.TaskStatusView;
import org.synanton.resolutor.application.port.in.TaskStatusView.ProgressView;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

@WebMvcTest(controllers = TaskController.class)
@Import(GlobalExceptionHandler.class)
class TaskControllerTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @MockBean TaskIngestionPort ingestion;
  @MockBean ProgressPort progress;
  @MockBean PlanQueryPort planQuery;

  @Test
  void ingestReturns201WithGeneratedId() throws Exception {
    TaskId id = TaskId.generate();
    when(ingestion.ingest(any(NewTaskCommand.class))).thenReturn(id);

    String body =
        """
        {"topResourceClass":"project","topResourceId":"7","searchDsl":{"q":"x"},"payload":{"a":1}}
        """;

    mvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(id.value().toString()));
  }

  @Test
  void ingestForwardsResourceAndPayload() throws Exception {
    when(ingestion.ingest(any(NewTaskCommand.class))).thenReturn(TaskId.generate());

    String body =
        """
        {"topResourceClass":"talk","topResourceId":"9","searchDsl":{},"payload":{"foo":"bar"}}
        """;

    mvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());

    ArgumentCaptor<NewTaskCommand> captor = ArgumentCaptor.forClass(NewTaskCommand.class);
    verify(ingestion).ingest(captor.capture());
    NewTaskCommand cmd = captor.getValue();
    assertThat(cmd.topResource().resourceClass()).isEqualTo("talk");
    assertThat(cmd.topResource().resourceId()).isEqualTo("9");
    assertThat(cmd.payload()).contains("\"foo\"").contains("\"bar\"");
  }

  @Test
  void ingestRejectsBlankTopResourceClass() throws Exception {
    String body =
        """
        {"topResourceClass":"","topResourceId":"7","searchDsl":{},"payload":{}}
        """;

    mvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Validation failed"));
  }

  @Test
  void ingestReturnsProblemJsonForMalformedBody() throws Exception {
    mvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content("{not json"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void statusReturns200WithStateAndProgress() throws Exception {
    TaskId id = TaskId.generate();
    when(progress.status(id))
        .thenReturn(new TaskStatusView(id, TaskState.PROCESSING, new ProgressView(10, 7, 2)));

    mvc.perform(get("/api/v1/tasks/" + id.value()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("PROCESSING"))
        .andExpect(jsonPath("$.progress.totalCount").value(10))
        .andExpect(jsonPath("$.progress.successCount").value(7))
        .andExpect(jsonPath("$.progress.pendingCount").value(1));
  }

  @Test
  void statusReturnsNullProgressWhenSnapshotAbsent() throws Exception {
    TaskId id = TaskId.generate();
    when(progress.status(id)).thenReturn(new TaskStatusView(id, TaskState.PENDING, null));

    mvc.perform(get("/api/v1/tasks/" + id.value()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("PENDING"))
        .andExpect(jsonPath("$.progress").doesNotExist());
  }

  @Test
  void statusReturns404WhenTaskNotFound() throws Exception {
    TaskId id = TaskId.generate();
    doThrow(new IllegalArgumentException("Task not found: " + id)).when(progress).status(id);

    mvc.perform(get("/api/v1/tasks/" + id.value()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Resource not found"));
  }

  @Test
  void updateProgressReturns204() throws Exception {
    UUID id = UUID.randomUUID();
    String body = "{\"successDelta\":5,\"failedDelta\":0,\"totalDelta\":5}";

    mvc.perform(
            post("/api/v1/tasks/" + id + "/progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNoContent());

    ArgumentCaptor<ProgressDelta> captor = ArgumentCaptor.forClass(ProgressDelta.class);
    verify(progress).updateProgress(any(), captor.capture());
    ProgressDelta d = captor.getValue();
    assertThat(d.successDelta()).isEqualTo(5);
    assertThat(d.totalDelta()).isEqualTo(5);
  }

  @Test
  void updateProgressRejectsNegativeDelta() throws Exception {
    UUID id = UUID.randomUUID();
    String body = "{\"successDelta\":-1,\"failedDelta\":0,\"totalDelta\":5}";

    mvc.perform(
            post("/api/v1/tasks/" + id + "/progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void forceCompleteReturns204() throws Exception {
    UUID id = UUID.randomUUID();
    doAnswer(inv -> null).when(progress).forceComplete(any());

    mvc.perform(post("/api/v1/tasks/" + id + "/complete")).andExpect(status().isNoContent());

    verify(progress).forceComplete(TaskId.of(id));
  }

  @Test
  void malformedUuidPathReturns400ProblemJson() throws Exception {
    mvc.perform(get("/api/v1/tasks/not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }
}
