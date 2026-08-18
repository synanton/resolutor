package org.synanton.resolutor.application.port.in;

/** Incremental progress reported by a task worker after processing a page. */
public record ProgressDelta(long successDelta, long failedDelta, long totalDelta) {

  public static ProgressDelta success(long processed) {
    return new ProgressDelta(processed, 0, processed);
  }

  public static ProgressDelta failed(long failed, long total) {
    return new ProgressDelta(0, failed, total);
  }
}
