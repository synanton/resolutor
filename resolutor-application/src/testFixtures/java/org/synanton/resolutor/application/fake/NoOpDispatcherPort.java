package org.synanton.resolutor.application.fake;

import java.util.List;
import org.synanton.resolutor.application.port.out.DispatcherPort;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.plan.SequentialGroup;

/** No-op {@link DispatcherPort} that marks every task in every group as successful. */
public final class NoOpDispatcherPort implements DispatcherPort {

  @Override
  public List<GroupResult> runPlan(ExecutionPlan plan) {
    return plan.groups().stream().map(this::dispatch).toList();
  }

  @Override
  public GroupResult dispatch(SequentialGroup group) {
    List<TaskResult> results = group.orderedTasks().stream().map(TaskResult::ok).toList();
    return new GroupResult(group.componentId(), results);
  }
}
