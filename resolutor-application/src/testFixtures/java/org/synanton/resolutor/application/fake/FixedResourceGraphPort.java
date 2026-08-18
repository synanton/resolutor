package org.synanton.resolutor.application.fake;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.synanton.resolutor.application.port.out.ResourceGraphPort;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.Task;

/**
 * {@link ResourceGraphPort} that returns a pre-configured fixed resource set per task, falling back
 * to the task's own {@code topResource} when no override is registered.
 */
public final class FixedResourceGraphPort implements ResourceGraphPort {

  private final Map<Task, Set<Resource>> overrides = new ConcurrentHashMap<>();

  public void register(Task task, Set<Resource> resources) {
    overrides.put(task, resources);
  }

  @Override
  public Set<Resource> resolve(Task task) {
    return overrides.getOrDefault(task, Set.of(task.topResource()));
  }
}
