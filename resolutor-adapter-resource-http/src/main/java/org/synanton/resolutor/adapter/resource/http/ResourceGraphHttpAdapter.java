package org.synanton.resolutor.adapter.resource.http;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.web.client.RestClient;
import org.synanton.resolutor.application.port.out.ResourceGraphPort;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.Task;

/**
 * HTTP implementation of {@link ResourceGraphPort}.
 *
 * <p>Wraps a remote resource-graph service behind a Resilience4j circuit breaker named {@code
 * resource-graph}. The upstream contract accepts a JSON request describing the top resource plus
 * search DSL and returns a list of {@code {resourceClass, resourceId}} pairs.
 *
 * <p>When the circuit is open or the call fails, {@link #fallback(Task, Throwable)} throws so the
 * planner skips the task instead of compiling a truncated footprint.
 */
public final class ResourceGraphHttpAdapter implements ResourceGraphPort {

  private static final System.Logger LOG =
      System.getLogger(ResourceGraphHttpAdapter.class.getName());

  private final RestClient restClient;
  private final String endpoint;

  public ResourceGraphHttpAdapter(RestClient restClient, String endpoint) {
    this.restClient = Objects.requireNonNull(restClient, "restClient");
    this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
  }

  /**
   * POST the task's top resource and search DSL to the configured endpoint and map the response
   * into a resource set. An empty or null body is treated as "top resource only".
   */
  @Override
  @CircuitBreaker(name = "resource-graph", fallbackMethod = "fallback")
  public Set<Resource> resolve(Task task) {
    Objects.requireNonNull(task, "task");
    ResolveRequest body =
        new ResolveRequest(
            task.id().value().toString(),
            new ResolveRequest.ResourceRef(
                task.topResource().resourceClass(), task.topResource().resourceId()),
            task.searchDsl());
    @Nullable ResolveResponse response =
        restClient
            .post()
            .uri(endpoint)
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(ResolveResponse.class);
    if (response == null) {
      return Set.of(task.topResource());
    }
    List<ResolveResponse.ResourceRef> refs = response.resources();
    if (refs == null || refs.isEmpty()) {
      return Set.of(task.topResource());
    }
    return refs.stream()
        .map(r -> Resource.of(r.resourceClass(), r.resourceId()))
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * Circuit-breaker fallback used when the upstream is unhealthy or too slow. Rethrows so {@code
   * ExecutionPlanner} excludes the task from this tick.
   */
  Set<Resource> fallback(Task task, Throwable ex) {
    LOG.log(
        System.Logger.Level.WARNING,
        "Resource-graph fallback for task {0}: {1}",
        task.id(),
        ex.getMessage());
    throw new ResourceGraphResolutionException(task.id(), ex);
  }

  /** Wire request body. Package-private for testing. */
  record ResolveRequest(String taskId, ResourceRef topResource, String searchDsl) {
    record ResourceRef(String resourceClass, String resourceId) {}
  }

  /** Wire response body. Package-private for testing. */
  record ResolveResponse(@Nullable List<ResourceRef> resources) {
    record ResourceRef(String resourceClass, String resourceId) {}
  }
}
