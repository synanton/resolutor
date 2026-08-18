package org.synanton.resolutor.application.backpressure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RingBufferTest {

  private static final long MIN_MS = 60_000L;

  @Test
  void freshBufferSumsToZero() {
    RingBuffer buf = new RingBuffer(0L);
    assertThat(buf.sum()).isZero();
  }

  @Test
  void incrementIncreasesSum() {
    long now = 0L;
    RingBuffer buf = new RingBuffer(now);
    buf.increment(now);
    buf.increment(now);

    assertThat(buf.sum()).isEqualTo(2);
  }

  @Test
  void eventsInSameMinuteCumulate() {
    long base = MIN_MS * 10;
    RingBuffer buf = new RingBuffer(base);
    buf.increment(base);
    buf.increment(base + 30_000L);

    assertThat(buf.sum()).isEqualTo(2);
  }

  @Test
  void advanceByOneClearsOneBucket() {
    long base = 0L;
    RingBuffer buf = new RingBuffer(base);
    buf.increment(base);
    buf.increment(base + MIN_MS);

    assertThat(buf.sum()).isEqualTo(2);
  }

  @Test
  void eventOutsideWindowIsEvicted() {
    long base = 0L;
    RingBuffer buf = new RingBuffer(base);
    buf.increment(base);

    // Advance past the full 60-minute window.
    long future = base + (RingBuffer.BUCKETS + 1) * MIN_MS;
    buf.increment(future);

    // Only the new event survives.
    assertThat(buf.sum()).isEqualTo(1);
  }

  @Test
  void advanceByExactlyWindowClearsAll() {
    long base = 0L;
    RingBuffer buf = new RingBuffer(base);
    // Fill a single bucket at minute 0.
    buf.increment(base);
    assertThat(buf.sum()).isEqualTo(1);

    // Jump exactly one full window forward from minute 0 (to minute 60).
    // advance() clears all 60 buckets, then the new increment lands in bucket[0].
    buf.increment(base + (long) RingBuffer.BUCKETS * MIN_MS);

    assertThat(buf.sum()).isEqualTo(1);
  }

  @Test
  void resetClearsAllBuckets() {
    long base = 0L;
    RingBuffer buf = new RingBuffer(base);
    buf.increment(base);
    buf.increment(base);
    buf.reset(base + MIN_MS * 5);

    assertThat(buf.sum()).isZero();
  }

  @Test
  void pastTimestampDoesNotAdvance() {
    long now = MIN_MS * 100;
    RingBuffer buf = new RingBuffer(now);
    buf.increment(now);
    buf.increment(now - MIN_MS);

    // The past event should still record in the current bucket (advance() is a no-op).
    assertThat(buf.sum()).isEqualTo(2);
  }
}
