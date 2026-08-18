package org.synanton.resolutor.adapter.resource.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

/**
 * Exercises the JSON shape and fallback path directly; the Resilience4j circuit-breaker annotation
 * is validated via Spring integration tests in the app module.
 */
class ResourceGraphHttpAdapterTest {

  private WireMockServer wireMock;
  private ResourceGraphHttpAdapter adapter;

  @BeforeEach
  void setUp() {
    wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMock.start();
    HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    RestClient client =
        RestClient.builder().requestFactory(new JdkClientHttpRequestFactory(http)).build();
    adapter = new ResourceGraphHttpAdapter(client, wireMock.baseUrl() + "/resources");
  }

  @AfterEach
  void tearDown() {
    wireMock.stop();
  }

  @Test
  void resolvesResponseIntoResources() {
    wireMock.stubFor(
        post(urlPathEqualTo("/resources"))
            .withRequestBody(
                equalToJson(
                    "{\"taskId\":\"11111111-1111-1111-1111-111111111111\","
                        + "\"topResource\":{\"resourceClass\":\"project\",\"resourceId\":\"7\"},"
                        + "\"searchDsl\":\"{}\"}"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"resources\":["
                            + "{\"resourceClass\":\"project\",\"resourceId\":\"7\"},"
                            + "{\"resourceClass\":\"talk\",\"resourceId\":\"9\"}]}")));

    Task task = task("11111111-1111-1111-1111-111111111111", "project", "7");
    Set<Resource> result = adapter.resolve(task);

    assertThat(result)
        .containsExactlyInAnyOrder(Resource.of("project", "7"), Resource.of("talk", "9"));
  }

  @Test
  void whenUpstreamFailsInvokingDirectlyPropagatesException() {
    // The fallback fires via Spring AOP; called directly the method should surface the exception.
    wireMock.stubFor(post(urlPathEqualTo("/resources")).willReturn(serverError()));

    Task task = task("22222222-2222-2222-2222-222222222222", "project", "1");

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.resolve(task))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void fallbackThrowsSoPlannerSkipsTheTask() {
    Task task = task("33333333-3333-3333-3333-333333333333", "project", "1");

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> adapter.fallback(task, new RuntimeException("upstream timeout")))
        .isInstanceOf(ResourceGraphResolutionException.class)
        .hasMessageContaining("33333333-3333-3333-3333-333333333333");
  }

  private static Task task(String id, String klass, String resId) {
    return new Task(
        TaskId.parse(id),
        Resource.of(klass, resId),
        Set.of(),
        "{}",
        "{}",
        null,
        TaskState.PENDING,
        Instant.now(),
        null,
        0L);
  }
}
