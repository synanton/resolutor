package org.synanton.resolutor.adapter.persistence.jpa;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.synanton.resolutor.application.port.out.LeadershipPort;

/**
 * ShedLock-backed {@link LeadershipPort}. Uses a single named lock ({@code resolutor-planner}) so
 * only one node executes {@link #runIfLeader(Duration, Supplier)} at a time.
 *
 * <p>{@code lockAtLeastFor} is kept small so a graceful shutdown does not starve failover; the
 * planner tick interval provides the natural back-off.
 */
public final class ShedLockLeadershipAdapter implements LeadershipPort {

  static final String LOCK_NAME = "resolutor-planner";
  static final Duration LOCK_AT_LEAST = Duration.ofMillis(100);

  private final LockProvider lockProvider;

  public ShedLockLeadershipAdapter(LockProvider lockProvider) {
    this.lockProvider = Objects.requireNonNull(lockProvider, "lockProvider");
  }

  /**
   * Execute {@code work} while holding the {@code resolutor-planner} ShedLock, for at most {@code
   * lockAtMost}. Returns empty when another node already holds the lock.
   */
  @Override
  public <T> Optional<T> runIfLeader(Duration lockAtMost, Supplier<T> work) {
    Objects.requireNonNull(lockAtMost, "lockAtMost");
    Objects.requireNonNull(work, "work");

    LockConfiguration cfg =
        new LockConfiguration(Instant.now(), LOCK_NAME, lockAtMost, LOCK_AT_LEAST);
    Optional<SimpleLock> maybeLock = lockProvider.lock(cfg);
    if (maybeLock.isEmpty()) {
      return Optional.empty();
    }
    SimpleLock lock = maybeLock.get();
    try {
      return Optional.ofNullable(work.get());
    } finally {
      lock.unlock();
    }
  }
}
