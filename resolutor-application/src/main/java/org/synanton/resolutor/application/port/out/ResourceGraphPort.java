package org.synanton.resolutor.application.port.out;

import java.util.Set;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.Task;

/** Outbound port: resolves the deterministic resource footprint touched by a task. */
public interface ResourceGraphPort {

  /** Return every resource whose state could be read or written by {@code task}. */
  Set<Resource> resolve(Task task);
}
