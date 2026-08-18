package org.synanton.resolutor.application.backpressure;

import java.util.Arrays;

/**
 * Space-efficient approximate frequency counter.
 *
 * <p>Width = 65,536 (2¹⁶), depth = 5 ≈ 2.6 MB per sketch (longs). Guaranteed no false negatives:
 * {@code estimate(key) >= actualCount(key)}. False-positive error probability ≈ 2/width ≈ 0.003%.
 *
 * <p>All public methods are {@code synchronized}; callers share a single sketch instance per
 * resource class via {@link BackpressureManager}.
 */
final class CountMinSketch {

  static final int WIDTH = 65_536;
  static final int DEPTH = 5;

  private static final long[] SEEDS = {
    0x9747b28cL, 0x8e8e5f26L, 0xb5b0ee71L, 0xd30a8b29L, 0x6c62272dL
  };

  private final long[][] table = new long[DEPTH][WIDTH];

  synchronized void increment(String key) {
    int raw = key.hashCode();
    for (int d = 0; d < DEPTH; d++) {
      table[d][column(raw, d)]++;
    }
  }

  synchronized long estimate(String key) {
    int raw = key.hashCode();
    long min = Long.MAX_VALUE;
    for (int d = 0; d < DEPTH; d++) {
      min = Math.min(min, table[d][column(raw, d)]);
    }
    return min;
  }

  /** Seed a key to at least {@code value} in every row (conservative update for reconstruction). */
  synchronized void seed(String key, long value) {
    int raw = key.hashCode();
    for (int d = 0; d < DEPTH; d++) {
      int col = column(raw, d);
      if (table[d][col] < value) {
        table[d][col] = value;
      }
    }
  }

  synchronized void reset() {
    for (long[] row : table) {
      Arrays.fill(row, 0L);
    }
  }

  private static int column(int rawHash, int depth) {
    // Mix raw hash with a depth-specific seed using a finalisation step from MurmurHash3.
    long h = ((long) rawHash) ^ SEEDS[depth];
    h = (h ^ (h >>> 30)) * 0xbf58476d1ce4e5b9L;
    h = (h ^ (h >>> 27)) * 0x94d049bb133111ebL;
    h ^= h >>> 31;
    return (int) (h & (WIDTH - 1));
  }
}
