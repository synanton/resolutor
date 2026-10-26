package org.synanton.resolutor.application.port.out;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/** Outbound port: distributes leadership so only one node runs a planning cycle at a time. */
public interface LeadershipPort {

  /**
   * Run {@code work} while holding the leadership lock for at most {@code lockAtMost}.
   *
   * @return the work result, or empty when another node already holds leadership
   */
  <T> Optional<T> runIfLeader(Duration lockAtMost, Supplier<T> work);
}
