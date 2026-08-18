package org.synanton.resolutor.application.fake;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import org.synanton.resolutor.application.port.out.LeadershipPort;

/** {@link LeadershipPort} that always grants leadership - for single-node / unit-test use. */
public final class AlwaysLeaderPort implements LeadershipPort {

  @Override
  public <T> Optional<T> runIfLeader(Duration lockAtMost, Supplier<T> work) {
    return Optional.of(work.get());
  }
}
