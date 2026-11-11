package com.projectmodule.work.infrastructure;

import com.projectmodule.work.domain.TaskFollower;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskFollowerRepository extends JpaRepository<TaskFollower, TaskFollower.Key> {

    List<TaskFollower> findByTaskId(UUID taskId);
}
