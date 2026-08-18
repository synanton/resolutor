package org.synanton.resolutor.application.backpressure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CountMinSketchTest {

  @Test
  void freshSketchEstimatesZero() {
    CountMinSketch sketch = new CountMinSketch();
    assertThat(sketch.estimate("anything")).isZero();
  }

  @Test
  void estimateReflectsIncrements() {
    CountMinSketch sketch = new CountMinSketch();
    sketch.increment("key1");
    sketch.increment("key1");
    sketch.increment("key1");

    assertThat(sketch.estimate("key1")).isGreaterThanOrEqualTo(3);
  }

  @Test
  void differentKeysAreIndependent() {
    CountMinSketch sketch = new CountMinSketch();
    sketch.increment("aaa");
    sketch.increment("aaa");
    sketch.increment("bbb");

    assertThat(sketch.estimate("aaa")).isGreaterThanOrEqualTo(2);
    assertThat(sketch.estimate("bbb")).isGreaterThanOrEqualTo(1);
  }

  @Test
  void seedSetsMinimumValue() {
    CountMinSketch sketch = new CountMinSketch();
    sketch.seed("seeded", 100);

    assertThat(sketch.estimate("seeded")).isGreaterThanOrEqualTo(100);
  }

  @Test
  void seedDoesNotDecrease() {
    CountMinSketch sketch = new CountMinSketch();
    sketch.seed("key", 50);
    sketch.seed("key", 10);

    assertThat(sketch.estimate("key")).isGreaterThanOrEqualTo(50);
  }

  @Test
  void resetClearsAllCounts() {
    CountMinSketch sketch = new CountMinSketch();
    sketch.increment("x");
    sketch.increment("x");
    sketch.reset();

    assertThat(sketch.estimate("x")).isZero();
  }

  @Test
  void estimateNeverUnderCounts() {
    CountMinSketch sketch = new CountMinSketch();
    int actual = 10;
    for (int i = 0; i < actual; i++) {
      sketch.increment("exactKey");
    }
    assertThat(sketch.estimate("exactKey")).isGreaterThanOrEqualTo(actual);
  }
}
