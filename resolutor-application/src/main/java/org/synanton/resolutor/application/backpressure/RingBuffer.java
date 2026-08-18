package org.synanton.resolutor.application.backpressure;

import java.util.Arrays;

/**
 * Sliding-window event counter with 60 one-minute buckets (1-hour window).
 *
 * <p>On each call to {@link #increment(long)}, the buffer advances to the current minute and clears
 * any stale buckets that have fallen out of the window. {@link #sum()} returns the total events in
 * the last hour. Both operations are O(60) in the worst case but O(1) when the minute does not
 * advance.
 *
 * <p>All public methods are {@code synchronized}.
 */
final class RingBuffer {

  static final int BUCKETS = 60;

  private final long[] buckets = new long[BUCKETS];
  private long currentMinute;

  RingBuffer(long nowMs) {
    this.currentMinute = toMinute(nowMs);
  }

  synchronized void increment(long nowMs) {
    advance(toMinute(nowMs));
    buckets[(int) (currentMinute % BUCKETS)]++;
  }

  synchronized long sum() {
    return Arrays.stream(buckets).sum();
  }

  synchronized void reset(long nowMs) {
    Arrays.fill(buckets, 0L);
    currentMinute = toMinute(nowMs);
  }

  private void advance(long minute) {
    if (minute <= currentMinute) {
      return;
    }
    long diff = minute - currentMinute;
    int toClear = (int) Math.min(diff, BUCKETS);
    for (int i = 1; i <= toClear; i++) {
      buckets[(int) ((currentMinute + i) % BUCKETS)] = 0;
    }
    currentMinute = minute;
  }

  private static long toMinute(long nowMs) {
    return nowMs / 60_000L;
  }
}
