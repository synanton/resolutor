package org.synanton.resolutor.adapter.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import org.springframework.kafka.annotation.KafkaListener;
import org.synanton.resolutor.adapter.kafka.KafkaPlanDispatcher.GroupMessage;
import org.synanton.resolutor.application.dispatch.InProcessDispatcher;

/** Consumes published groups and executes them in-process. */
public final class KafkaGroupConsumer {

  private static final System.Logger LOG = System.getLogger(KafkaGroupConsumer.class.getName());

  private final InProcessDispatcher dispatcher;
  private final ObjectMapper json;

  public KafkaGroupConsumer(InProcessDispatcher dispatcher, ObjectMapper json) {
    this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    this.json = Objects.requireNonNull(json, "json");
  }

  @KafkaListener(topics = "${resolutor.kafka.groups-topic:resolutor.plan.groups}")
  public void onGroup(String payload) {
    try {
      GroupMessage message = json.readValue(payload, GroupMessage.class);
      dispatcher.dispatch(message.toGroup());
    } catch (Exception ex) {
      LOG.log(System.Logger.Level.ERROR, "Failed to dispatch Kafka plan group", ex);
      throw new IllegalStateException("Kafka group dispatch failed", ex);
    }
  }
}
