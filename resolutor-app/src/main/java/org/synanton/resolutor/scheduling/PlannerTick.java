package org.synanton.resolutor.scheduling;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.synanton.resolutor.application.lifecycle.LeadershipManager;
import org.synanton.resolutor.config.ResolutorProperties;

/**
 * Periodic planner + dispatcher tick. Only one node executes the body at a time - leadership is
 * enforced by ShedLock via {@link LeadershipManager#runPlanningCycle(Duration)}, which also
 * dispatches before releasing the lock.
 *
 * <p>See {@code docs/implementation-plan.md} §15 phase 5.
 */
@Component
public class PlannerTick {

  private final LeadershipManager leadershipManager;
  private final ResolutorProperties properties;
  private final AtomicBoolean acceptingTicks = new AtomicBoolean(true);

  public PlannerTick(LeadershipManager leadershipManager, ResolutorProperties properties) {
    this.leadershipManager = Objects.requireNonNull(leadershipManager, "leadershipManager");
    this.properties = Objects.requireNonNull(properties, "properties");
  }

  /**
   * Runs on the interval configured by {@code resolutor.planner.tick-interval}. Skips silently when
   * this node is not the leader or the process is shutting down.
   */
  @Scheduled(fixedDelayString = "${resolutor.planner.tick-interval}")
  public void tick() {
    if (!acceptingTicks.get()) {
      return;
    }
    Duration lockAtMost = properties.dispatchOrDefault().lockAtMostOrDefault();
    leadershipManager.runPlanningCycle(lockAtMost);
  }

  /** Refuse new ticks so in-flight dispatch can drain during graceful shutdown. */
  @PreDestroy
  public void stopAcceptingTicks() {
    acceptingTicks.set(false);
  }
}
